// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.microsoft.copilot.eclipse.core.lsp.protocol.ConfirmationMessages;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult.ToolInvocationStatus;
import com.microsoft.copilot.eclipse.ui.chat.ChatView;

/**
 * Writes a DataWeave 2.0 script into a Transform Message (ee:transform) component in a Mule XML file.
 * Targets the ee:set-payload, ee:set-attributes, or a named ee:set-variable element.
 * Requires user confirmation before modifying the XML file.
 */
public class MuleTransformWriteTool extends BaseTool {
  static final String TOOL_NAME = "mule_write_transform";

  /**
   * Creates a Mule transform write tool.
   */
  public MuleTransformWriteTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public boolean needConfirmation() {
    return true;
  }

  @Override
  public ConfirmationMessages getConfirmationMessages() {
    ConfirmationMessages messages = new ConfirmationMessages();
    messages.setTitle("Update Transform Message DataWeave Script");
    messages.setMessage(
        "This will replace the DataWeave script inside the Transform Message component in the Mule XML file. "
            + "Continue?");
    return messages;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Write a DataWeave script into a Transform Message component");
    toolInfo.setDescription("""
        Replace the DataWeave 2.0 script inside a Transform Message (ee:transform) component in a Mule XML file.
        Use target 'payload', 'attributes', 'variable:name', or a variable name.
        Identify the transform by transformName (doc:name) or transformId (doc:id).
        If the target uses resource=\"...\", the external DWL file under src/main/resources is updated.
        The dwlScript must be a complete DataWeave 2.0 script starting with %dw 2.0 and an output directive.
        Always run mule_read_transform first to confirm the current state before writing.
        Requires user confirmation before modifying the file.
        """);
    toolInfo.setInputSchema(MuleToolInputs.transformWriteSchema());
    return toolInfo;
  }

  @Override
  public CompletableFuture<LanguageModelToolResult[]> invoke(Map<String, Object> input, ChatView chatView) {
    LanguageModelToolResult result = new LanguageModelToolResult();
    try {
      Path xmlPath = MuleToolInputs.existingFile(input.get(MuleToolInputs.XML_FILE_PATH));
      if (xmlPath == null) {
        result.setStatus(ToolInvocationStatus.error);
        result.addContent("xmlFilePath must be an absolute path to an existing Mule XML file.");
        return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
      }

      String dwlScript = MuleToolInputs.optionalString(input.get(MuleToolInputs.DWL_SCRIPT));
      if (dwlScript.isBlank()) {
        result.setStatus(ToolInvocationStatus.error);
        result.addContent("dwlScript is required and must not be blank.");
        return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
      }

      String target = MuleToolInputs.optionalString(input.get(MuleToolInputs.TARGET));
      String transformName = MuleToolInputs.optionalString(input.get(MuleToolInputs.TRANSFORM_NAME));
      String transformId = MuleToolInputs.optionalString(input.get(MuleToolInputs.TRANSFORM_ID));

      WriteOutcome outcome = writeTransform(xmlPath, transformName, transformId, target, dwlScript);
      if (outcome.refreshPath() != null) {
        refreshWorkspaceFile(outcome.refreshPath());
      }

      result.setStatus(outcome.success() ? ToolInvocationStatus.success : ToolInvocationStatus.error);
      result.addContent(outcome.message());
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to write Transform Message: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }

  private static WriteOutcome writeTransform(Path xmlPath, String transformName, String transformId,
      String target, String dwlScript) throws Exception {
    Document document = MuleTransformSupport.parseXml(xmlPath);
    SingleTransformMatch match = findSingleTransform(document, transformName, transformId);
    if (!match.success()) {
      return new WriteOutcome(false, match.message(), null);
    }

    TargetElement targetElement = findTargetElement(match.transform(), target);
    if (!targetElement.success()) {
      return new WriteOutcome(false, targetElement.message(), null);
    }

    MuleTransformSupport.WriteContentResult writeResult =
        MuleTransformSupport.writeScriptContent(document, targetElement.element(), xmlPath, dwlScript);
    if (!writeResult.success()) {
      return new WriteOutcome(false, writeResult.message(), null);
    }
    if (writeResult.xmlModified()) {
      MuleTransformSupport.serializeDocument(document, xmlPath);
    }

    String message = writeResult.message() + " for Transform Message '"
        + MuleTransformSupport.transformLabel(match.transform()) + "' target='" + targetElement.label()
        + "' in " + (writeResult.modifiedPath() == null ? xmlPath.getFileName() : writeResult.modifiedPath());
    return new WriteOutcome(true, message, writeResult.modifiedPath());
  }

  private static SingleTransformMatch findSingleTransform(Document document, String transformName, String transformId) {
    List<Element> transforms = MuleTransformSupport.findTransforms(document, transformName, transformId);
    if (transforms.isEmpty()) {
      return new SingleTransformMatch(false,
          "No matching ee:transform element found. Provide transformName or transformId, or verify the file path.",
          null);
    }
    if (transforms.size() > 1) {
      return new SingleTransformMatch(false,
          "Multiple ee:transform elements matched. Provide a unique transformName or transformId.", null);
    }
    return new SingleTransformMatch(true, "", transforms.get(0));
  }

  private static TargetElement findTargetElement(Element transform, String target) {
    String normalizedTarget = normalizeTarget(target);
    if (MuleTransformSupport.TARGET_PAYLOAD.equals(normalizedTarget)) {
      Element payload = firstMessageChild(transform, "set-payload");
      return payload == null ? missingTarget(normalizedTarget) : new TargetElement(true, "", payload, normalizedTarget);
    }
    if (MuleTransformSupport.TARGET_ATTRIBUTES.equals(normalizedTarget)) {
      Element attributes = firstMessageChild(transform, "set-attributes");
      return attributes == null ? missingTarget(normalizedTarget)
          : new TargetElement(true, "", attributes, normalizedTarget);
    }

    String variableName = normalizedTarget.startsWith(MuleTransformSupport.TARGET_VARIABLE_PREFIX)
        ? normalizedTarget.substring(MuleTransformSupport.TARGET_VARIABLE_PREFIX.length()) : normalizedTarget;
    for (Element variablesEl : MuleTransformSupport.directChildren(transform, "variables")) {
      for (Element setVarEl : MuleTransformSupport.directChildren(variablesEl, "set-variable")) {
        if (variableName.equals(setVarEl.getAttribute("variableName"))) {
          return new TargetElement(true, "", setVarEl, MuleTransformSupport.TARGET_VARIABLE_PREFIX + variableName);
        }
      }
    }
    return missingTarget(MuleTransformSupport.TARGET_VARIABLE_PREFIX + variableName);
  }

  private static Element firstMessageChild(Element transform, String localName) {
    for (Element messageEl : MuleTransformSupport.directChildren(transform, "message")) {
      Element child = MuleTransformSupport.firstDirectChild(messageEl, localName);
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  private static String normalizeTarget(String target) {
    String normalized = target == null ? "" : target.trim();
    return normalized.isBlank() ? MuleTransformSupport.TARGET_PAYLOAD : normalized;
  }

  private static TargetElement missingTarget(String target) {
    return new TargetElement(false,
        "Target element not found in transform. Verify that target '" + target
            + "' exists in the Transform Message component.",
        null, target);
  }

  private static void refreshWorkspaceFile(Path xmlPath) {
    try {
      IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
      IFile file = root.getFileForLocation(
          new org.eclipse.core.runtime.Path(xmlPath.toAbsolutePath().toString()));
      if (file != null && file.exists()) {
        file.refreshLocal(IFile.DEPTH_ZERO, null);
      }
    } catch (Exception e) {
      // Non-fatal: the file was written successfully; workspace refresh will happen on next build
    }
  }

  private record SingleTransformMatch(boolean success, String message, Element transform) {
  }

  private record TargetElement(boolean success, String message, Element element, String label) {
  }

  private record WriteOutcome(boolean success, String message, Path refreshPath) {
  }
}
