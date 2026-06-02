// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult.ToolInvocationStatus;
import com.microsoft.copilot.eclipse.ui.chat.ChatView;

/**
 * Reads the content of a standalone DataWeave module (.dwl) file. Use this before editing or
 * reviewing a DataWeave module to understand the current script and its output type declaration.
 * This tool is read-only.
 */
public class MuleDwlReadTool extends BaseTool {
  static final String TOOL_NAME = "mule_read_dwl_file";

  /**
   * Creates a DataWeave file read tool.
   */
  public MuleDwlReadTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Read a standalone DataWeave module file");
    toolInfo.setDescription("""
        Read the content of a standalone DataWeave 2.0 module (.dwl) file.
        Returns the file path, line count, and full script content.
        Use this before editing, reviewing, or optimizing a DataWeave module to understand
        the current script, its output type declaration, function definitions, and imports.
        This tool is read-only.
        """);
    toolInfo.setInputSchema(MuleToolInputs.dwlReadSchema());
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

      String content = Files.readString(dwlPath, StandardCharsets.UTF_8);
      long lineCount = content.lines().count();

      StringBuilder sb = new StringBuilder();
      sb.append("file=").append(dwlPath.toAbsolutePath()).append(System.lineSeparator());
      sb.append("lines=").append(lineCount).append(System.lineSeparator());
      sb.append("script:").append(System.lineSeparator());
      sb.append(content);

      result.setStatus(ToolInvocationStatus.success);
      result.addContent(sb.toString());
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to read DataWeave file: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }
}
