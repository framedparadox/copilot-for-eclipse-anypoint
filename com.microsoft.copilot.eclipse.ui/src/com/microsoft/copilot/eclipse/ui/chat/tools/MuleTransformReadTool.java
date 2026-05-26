// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult.ToolInvocationStatus;
import com.microsoft.copilot.eclipse.ui.chat.ChatView;

/**
 * Reads DataWeave scripts from Transform Message (ee:transform) components in Mule XML files.
 * Returns the payload, attributes, and variable DWL scripts along with their output type declarations,
 * giving Copilot the context needed to generate or improve DataWeave mappings.
 */
public class MuleTransformReadTool extends BaseTool {
  static final String TOOL_NAME = "mule_read_transform";

  /**
   * Creates a Mule transform read tool.
   */
  public MuleTransformReadTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Read DataWeave scripts from Transform Message components");
    toolInfo.setDescription("""
        Read the DataWeave 2.0 scripts inside Transform Message (ee:transform) components in a Mule XML file.
        Returns set-payload, set-attributes, and set-variable scripts with output types.
        External DWL resources are reported and read from src/main/resources when the project root can be inferred.
        Use this before writing or reviewing a DataWeave mapping to understand the current state and type context.
        Optionally filter by doc:name or doc:id to target a specific Transform Message component.
        This tool is read-only.
        """);
    toolInfo.setInputSchema(MuleToolInputs.transformReadSchema());
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

      String transformName = MuleToolInputs.optionalString(input.get(MuleToolInputs.TRANSFORM_NAME));
      String transformId = MuleToolInputs.optionalString(input.get(MuleToolInputs.TRANSFORM_ID));

      ReadOutcome outcome = readTransforms(xmlPath, transformName, transformId);
      result.setStatus(outcome.success() ? ToolInvocationStatus.success : ToolInvocationStatus.error);
      result.addContent(outcome.message());
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to read Transform Message: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }

  private static ReadOutcome readTransforms(Path xmlPath, String transformName, String transformId) throws Exception {
    Document document = MuleTransformSupport.parseXml(xmlPath);
    List<Element> matched = MuleTransformSupport.findTransforms(document, transformName, transformId);
    if (matched.isEmpty()) {
      return new ReadOutcome(false,
          "No ee:transform element matched the given transformName or transformId in " + xmlPath.getFileName());
    }

    StringBuilder sb = new StringBuilder();
    sb.append("file=").append(xmlPath.toAbsolutePath()).append(System.lineSeparator());
    sb.append("transformCount=").append(matched.size()).append(System.lineSeparator());

    for (Element transform : matched) {
      sb.append(System.lineSeparator());
      sb.append("--- Transform: ").append(MuleTransformSupport.transformLabel(transform)).append(" ---")
          .append(System.lineSeparator());

      appendScriptsFromMessage(xmlPath, transform, sb);
      appendScriptsFromVariables(xmlPath, transform, sb);
    }

    return new ReadOutcome(true, sb.toString());
  }

  private static void appendScriptsFromMessage(Path xmlPath, Element transform, StringBuilder sb) {
    for (Element messageEl : MuleTransformSupport.directChildren(transform, "message")) {
      for (Element setPayloadEl : MuleTransformSupport.directChildren(messageEl, "set-payload")) {
        appendScript(xmlPath, sb, MuleTransformSupport.TARGET_PAYLOAD, setPayloadEl);
      }
      for (Element setAttributesEl : MuleTransformSupport.directChildren(messageEl, "set-attributes")) {
        appendScript(xmlPath, sb, MuleTransformSupport.TARGET_ATTRIBUTES, setAttributesEl);
      }
    }
  }

  private static void appendScriptsFromVariables(Path xmlPath, Element transform, StringBuilder sb) {
    for (Element variablesEl : MuleTransformSupport.directChildren(transform, "variables")) {
      for (Element setVarEl : MuleTransformSupport.directChildren(variablesEl, "set-variable")) {
        String varName = setVarEl.getAttribute("variableName");
        appendScript(xmlPath, sb, MuleTransformSupport.TARGET_VARIABLE_PREFIX + varName, setVarEl);
      }
    }
  }

  private static void appendScript(Path xmlPath, StringBuilder sb, String target, Element element) {
    MuleTransformSupport.ScriptContent content = MuleTransformSupport.readScriptContent(element, xmlPath);
    String script = content.script();
    String outputType = extractOutputType(script);
    sb.append("target=").append(target).append(System.lineSeparator());
    if (!content.resource().isBlank()) {
      sb.append("resource=").append(content.resource()).append(System.lineSeparator());
      sb.append("resourceStatus=").append(content.resourceStatus()).append(System.lineSeparator());
      if (content.resourcePath() != null) {
        sb.append("resourcePath=").append(content.resourcePath()).append(System.lineSeparator());
      }
    }
    sb.append("outputType=").append(outputType).append(System.lineSeparator());
    sb.append("script:").append(System.lineSeparator()).append(script).append(System.lineSeparator());
  }

  private static String extractOutputType(String script) {
    for (String line : script.split("\\r?\\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("output ")) {
        return trimmed.substring("output ".length()).trim();
      }
    }
    return "unknown";
  }

  private record ReadOutcome(boolean success, String message) {
  }
}
