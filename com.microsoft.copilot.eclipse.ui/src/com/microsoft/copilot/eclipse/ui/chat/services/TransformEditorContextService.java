// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.services;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.microsoft.copilot.eclipse.core.CopilotCore;
import com.microsoft.copilot.eclipse.ui.chat.tools.MuleTransformSupport;
import com.microsoft.copilot.eclipse.ui.chat.tools.MuleTransformSupport.ScriptContent;
import com.microsoft.copilot.eclipse.ui.utils.UiUtils;

/**
 * Captures the active Mule XML editor's Transform Message elements for chat context.
 */
public class TransformEditorContextService {
  public static final int DEFAULT_MAX_CHARS_PER_SCRIPT = 8_000;

  /** View ID for the Anypoint Studio Transform Message properties panel. */
  public static final String MULE_TRANSFORM_VIEW_ID = "org.mule.tooling.ui.views.transformView";

  /** Source tag emitted when context comes from the properties-view hint. */
  public static final String SOURCE_PROPERTIES_VIEW = "properties-view";

  private volatile String activeTransformName = null;
  private volatile String activeTransformId = null;

  /**
   * Sets a hint identifying the specific Transform Message component currently open in the
   * Anypoint Studio properties panel. Called from the workbench part listener when the
   * Transform properties view is activated.
   *
   * @param transformName the {@code doc:name} of the focused transform (may be blank)
   * @param transformId the {@code doc:id} of the focused transform (may be blank)
   */
  public void setActiveTransformHint(String transformName, String transformId) {
    this.activeTransformName = transformName;
    this.activeTransformId = transformId;
  }

  /**
   * Clears the properties-view hint so context falls back to the full file's transforms.
   * Called when the Transform properties view is deactivated or closed.
   */
  public void clearActiveTransformHint() {
    this.activeTransformName = null;
    this.activeTransformId = null;
  }

  /**
   * Captures all Transform Message elements from the currently active Mule XML editor.
   *
   * <p>When the Anypoint Studio Transform Message properties view is active, narrows the result
   * to the specific transform identified by the properties-view hint. Falls back to all transforms
   * in the file when no hint is set, and to searching open editors when no main editor is active.
   * Must be called on the SWT UI thread.
   *
   * @return a transform editor snapshot, or an unavailable snapshot when no Mule XML editor is active
   */
  public TransformEditorSnapshot captureActiveTransformContext() {
    IFile xmlFile = UiUtils.getCurrentFile();
    if (xmlFile == null) {
      xmlFile = findFirstMuleXmlFile(UiUtils.getOpenedFiles());
    }
    String nameHint = this.activeTransformName;
    String idHint = this.activeTransformId;
    return captureTransformContext(xmlFile, nameHint, idHint, DEFAULT_MAX_CHARS_PER_SCRIPT);
  }

  /**
   * Captures all Transform Message elements by scanning all currently open editors for a Mule XML
   * file. Used for silent auto-inject when the user has not typed an explicit {@code @transform}
   * command.
   *
   * <p>Must be called on the SWT UI thread.
   *
   * @return a transform editor snapshot, or an unavailable snapshot when no open Mule XML file is found
   */
  public TransformEditorSnapshot captureAutoTransformContext() {
    IFile xmlFile = findFirstMuleXmlFile(UiUtils.getOpenedFiles());
    return captureTransformContext(xmlFile, null, null, DEFAULT_MAX_CHARS_PER_SCRIPT);
  }

  /**
   * Captures Transform Message elements from the given Mule XML file, returning all transforms.
   *
   * @param file the IFile to inspect (may be null)
   * @param maxCharsPerScript maximum characters per individual DataWeave script
   * @return a transform editor snapshot
   */
  public TransformEditorSnapshot captureTransformContext(IFile file, int maxCharsPerScript) {
    return captureTransformContext(file, null, null, maxCharsPerScript);
  }

  /**
   * Captures Transform Message elements from the given Mule XML file, optionally filtered to a
   * specific transform by name or ID.
   *
   * @param file the IFile to inspect (may be null)
   * @param transformName optional {@code doc:name} filter (null or blank = match all)
   * @param transformId optional {@code doc:id} filter (null or blank = match all)
   * @param maxCharsPerScript maximum characters per individual DataWeave script
   * @return a transform editor snapshot
   */
  public TransformEditorSnapshot captureTransformContext(IFile file, String transformName, String transformId,
      int maxCharsPerScript) {
    if (file == null) {
      return TransformEditorSnapshot.unavailable("No active editor is open.");
    }
    if (!isMuleXmlFile(file)) {
      return TransformEditorSnapshot.unavailable("The active file is not a Mule XML flow file.");
    }

    String name = transformName != null ? transformName : "";
    String id = transformId != null ? transformId : "";
    boolean hasHint = !name.isBlank() || !id.isBlank();

    Path xmlPath = file.getLocation().toFile().toPath();
    try {
      Document document = MuleTransformSupport.parseXml(xmlPath);
      List<Element> transforms = MuleTransformSupport.findTransforms(document, name, id);

      String source = hasHint ? SOURCE_PROPERTIES_VIEW : null;

      if (transforms.isEmpty()) {
        return TransformEditorSnapshot.available(xmlPath.toString(), 0, List.of(), false, source);
      }

      List<TransformEditorSnapshot.TransformEntry> entries = new ArrayList<>();
      boolean anyTruncated = false;

      for (Element transform : transforms) {
        String label = MuleTransformSupport.transformLabel(transform);
        String docName = getDocAttribute(transform, "name");
        String docId = getDocAttribute(transform, "id");

        List<TransformEditorSnapshot.ScriptEntry> scripts = new ArrayList<>();

        // payload and attributes (inside ee:message)
        for (Element messageEl : MuleTransformSupport.directChildren(transform, "message")) {
          for (Element setPayload : MuleTransformSupport.directChildren(messageEl, "set-payload")) {
            ReadResult entry = readScript(setPayload, xmlPath, MuleTransformSupport.TARGET_PAYLOAD, maxCharsPerScript);
            scripts.add(entry.scriptEntry());
            if (entry.truncated()) {
              anyTruncated = true;
            }
          }
          for (Element setAttribs : MuleTransformSupport.directChildren(messageEl, "set-attributes")) {
            ReadResult entry = readScript(setAttribs, xmlPath, MuleTransformSupport.TARGET_ATTRIBUTES,
                maxCharsPerScript);
            scripts.add(entry.scriptEntry());
            if (entry.truncated()) {
              anyTruncated = true;
            }
          }
        }

        // variables (inside ee:variables)
        for (Element setVar : MuleTransformSupport.directChildren(transform, "variables")) {
          for (Element variable : MuleTransformSupport.directChildren(setVar, "set-variable")) {
            String varName = variable.getAttribute("variableName");
            String target = varName.isBlank() ? MuleTransformSupport.TARGET_VARIABLE_PREFIX + "unknown"
                : MuleTransformSupport.TARGET_VARIABLE_PREFIX + varName;
            ReadResult entry = readScript(variable, xmlPath, target, maxCharsPerScript);
            scripts.add(entry.scriptEntry());
            if (entry.truncated()) {
              anyTruncated = true;
            }
          }
        }

        entries.add(new TransformEditorSnapshot.TransformEntry(label, docName, docId, scripts));
      }

      return TransformEditorSnapshot.available(xmlPath.toString(), transforms.size(), entries, anyTruncated, source);
    } catch (Exception e) {
      CopilotCore.LOGGER.error("Failed to capture transform editor context", e);
      return TransformEditorSnapshot.unavailable("Failed to read Mule XML: " + e.getMessage());
    }
  }

  private IFile findFirstMuleXmlFile(java.util.List<IFile> files) {
    for (IFile file : files) {
      if (isMuleXmlFile(file)) {
        return file;
      }
    }
    return null;
  }

  private boolean isMuleXmlFile(IFile file) {
    if (!"xml".equalsIgnoreCase(file.getFileExtension())) {
      return false;
    }
    // Accept files under src/main/mule or any XML in a project that has pom.xml
    String fullPath = file.getFullPath().toString();
    return fullPath.contains("/src/main/mule/") || fullPath.contains("\\src\\main\\mule\\");
  }

  private String getDocAttribute(Element element, String attrLocalName) {
    String value = element.getAttributeNS(MuleTransformSupport.DOC_NS, attrLocalName);
    if (value.isBlank()) {
      value = element.getAttribute("doc:" + attrLocalName);
    }
    return value;
  }

  private ReadResult readScript(Element element, Path xmlPath, String target, int maxCharsPerScript) {
    ScriptContent content = MuleTransformSupport.readScriptContent(element, xmlPath);
    String script = content.script();
    boolean truncated = maxCharsPerScript > 0 && script.length() > maxCharsPerScript;
    if (truncated) {
      script = script.substring(0, maxCharsPerScript);
    }
    String outputType = extractOutputType(content.script());
    return new ReadResult(
        new TransformEditorSnapshot.ScriptEntry(target, outputType, script),
        truncated);
  }

  private String extractOutputType(String script) {
    if (script == null || script.isBlank()) {
      return "";
    }
    for (String line : script.split("\n", -1)) {
      String trimmed = line.trim();
      if (trimmed.startsWith("output ")) {
        return trimmed.substring("output ".length()).trim();
      }
    }
    return "";
  }

  private record ReadResult(TransformEditorSnapshot.ScriptEntry scriptEntry, boolean truncated) {
  }

  /**
   * Snapshot of the active Mule XML editor's Transform Message elements for a chat turn.
   */
  public record TransformEditorSnapshot(
      String xmlFilePath,
      int transformCount,
      List<TransformEntry> transforms,
      boolean truncated,
      String unavailableReason,
      String source) {

    public record TransformEntry(String label, String docName, String docId, List<ScriptEntry> scripts) {
    }

    public record ScriptEntry(String target, String outputType, String script) {
    }

    public static TransformEditorSnapshot available(String xmlFilePath, int transformCount,
        List<TransformEntry> transforms, boolean truncated, String source) {
      return new TransformEditorSnapshot(xmlFilePath, transformCount, transforms, truncated, null, source);
    }

    public static TransformEditorSnapshot available(String xmlFilePath, int transformCount,
        List<TransformEntry> transforms, boolean truncated) {
      return available(xmlFilePath, transformCount, transforms, truncated, null);
    }

    public static TransformEditorSnapshot unavailable(String reason) {
      return new TransformEditorSnapshot(null, 0, List.of(), false, reason, null);
    }

    public boolean isAvailable() {
      return unavailableReason == null;
    }

    public boolean isEmpty() {
      return transforms.isEmpty();
    }

    public boolean isFromPropertiesView() {
      return SOURCE_PROPERTIES_VIEW.equals(source);
    }
  }
}
