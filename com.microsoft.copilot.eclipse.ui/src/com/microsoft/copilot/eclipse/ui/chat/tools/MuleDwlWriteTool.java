// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import com.microsoft.copilot.eclipse.core.lsp.protocol.ConfirmationMessages;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult.ToolInvocationStatus;
import com.microsoft.copilot.eclipse.ui.chat.ChatView;

/**
 * Writes a complete DataWeave 2.0 script to a standalone .dwl module file. Replaces the entire
 * file content. Requires user confirmation before writing.
 */
public class MuleDwlWriteTool extends BaseTool {
  static final String TOOL_NAME = "mule_write_dwl_file";

  /**
   * Creates a DataWeave file write tool.
   */
  public MuleDwlWriteTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public boolean needConfirmation() {
    return true;
  }

  @Override
  public ConfirmationMessages getConfirmationMessages() {
    ConfirmationMessages messages = new ConfirmationMessages();
    messages.setTitle("Update DataWeave Module File");
    messages.setMessage(
        "This will replace the entire content of the DataWeave .dwl file. Continue?");
    return messages;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Write a DataWeave script to a standalone .dwl module file");
    toolInfo.setDescription("""
        Replace the content of a standalone DataWeave 2.0 module (.dwl) file.
        The dwlScript must be a complete script (not a fragment) and should start with '%dw 2.0'
        followed by an output directive.
        Always run mule_read_dwl_file first to confirm the current state before writing.
        Always run mulesoft/dataweave_run_script_tool after writing to validate the updated script.
        Requires user confirmation before modifying the file.
        """);
    toolInfo.setInputSchema(MuleToolInputs.dwlWriteSchema());
    return toolInfo;
  }

  @Override
  public CompletableFuture<LanguageModelToolResult[]> invoke(Map<String, Object> input, ChatView chatView) {
    LanguageModelToolResult result = new LanguageModelToolResult();
    try {
      Path dwlPath = MuleToolInputs.existingFile(input.get(MuleToolInputs.DWL_FILE_PATH));
      if (dwlPath == null) {
        result.setStatus(ToolInvocationStatus.error);
        result.addContent("dwlFilePath must be an absolute path to an existing .dwl file.");
        return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
      }
      if (!dwlPath.getFileName().toString().endsWith(".dwl")) {
        result.setStatus(ToolInvocationStatus.error);
        result.addContent("The file must be a DataWeave module (.dwl). Got: " + dwlPath.getFileName());
        return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
      }

      String dwlScript = MuleToolInputs.optionalString(input.get(MuleToolInputs.DWL_SCRIPT));
      if (dwlScript.isBlank()) {
        result.setStatus(ToolInvocationStatus.error);
        result.addContent("dwlScript is required and must not be blank.");
        return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
      }

      Files.writeString(dwlPath, dwlScript, StandardCharsets.UTF_8);
      refreshWorkspaceFile(dwlPath);

      result.setStatus(ToolInvocationStatus.success);
      result.addContent("Updated DataWeave module: " + dwlPath.toAbsolutePath());
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to write DataWeave file: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }

  private static void refreshWorkspaceFile(Path dwlPath) {
    try {
      IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
      IFile file = root.getFileForLocation(
          new org.eclipse.core.runtime.Path(dwlPath.toAbsolutePath().toString()));
      if (file != null && file.exists()) {
        file.refreshLocal(IFile.DEPTH_ZERO, null);
      }
    } catch (Exception e) {
      // Non-fatal: the file was written successfully; workspace refresh will happen on next build
    }
  }
}
