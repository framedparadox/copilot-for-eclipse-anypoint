// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * Analyzes a standalone DataWeave (.dwl) file for performance anti-patterns, null-safety gaps,
 * and documentation issues. By default operates as read-only (preview). When applyFixes=true,
 * writes the optimized script back to the file (requires user confirmation).
 */
public class MuleDwlOptimizeTool extends BaseTool {
  static final String TOOL_NAME = "mule_optimize_dwl";

  /**
   * Creates a DataWeave optimize tool.
   */
  public MuleDwlOptimizeTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public boolean needConfirmation() {
    return true;
  }

  @Override
  public ConfirmationMessages getConfirmationMessages() {
    ConfirmationMessages messages = new ConfirmationMessages();
    messages.setTitle("Apply DataWeave Optimizations");
    messages.setMessage(
        "This will analyze and optionally rewrite the DataWeave .dwl file with performance "
            + "improvements and documentation comments. Continue?");
    return messages;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Analyze and optimize a DataWeave module file");
    toolInfo.setDescription("""
        Analyze a standalone DataWeave 2.0 module (.dwl) file for common issues:
        - Missing %dw 2.0 header or output directive
        - Nested map+filter patterns (O(n×m)) — suggest groupBy pre-indexing
        - Inline regex literals inside map/filter — suggest extracting to var
        - Round-trip write()/read() serialization — flag as no-op
        - Field accesses without null guards (missing 'default' operator)
        - Undocumented function declarations — suggest comment stubs
        Returns a structured findings report and the suggested optimized script.
        When applyFixes=true, writes the improved script back to the file (requires confirmation).
        When includeComments=true (default), adds documentation comments to undocumented functions.
        Always run mulesoft/dataweave_run_script_tool after applying to validate the result.
        """);
    toolInfo.setInputSchema(MuleToolInputs.dwlOptimizeSchema());
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

      boolean includeComments = !Boolean.FALSE.equals(input.get(MuleToolInputs.INCLUDE_COMMENTS));
      boolean applyFixes = Boolean.TRUE.equals(input.get(MuleToolInputs.APPLY_FIXES));

      String script = Files.readString(dwlPath, StandardCharsets.UTF_8);
      List<DwlAnalyzer.Issue> issues = DwlAnalyzer.analyze(script);
      String optimizedScript = includeComments ? DwlAnalyzer.addComments(script) : script;

      if (applyFixes) {
        Files.writeString(dwlPath, optimizedScript, StandardCharsets.UTF_8);
        refreshWorkspaceFile(dwlPath);
      }

      result.setStatus(ToolInvocationStatus.success);
      result.addContent(formatReport(dwlPath, issues, optimizedScript, applyFixes));
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to optimize DataWeave file: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }

  private static String formatReport(Path dwlPath, List<DwlAnalyzer.Issue> issues,
      String optimizedScript, boolean applied) {
    StringBuilder sb = new StringBuilder();
    sb.append("file=").append(dwlPath.toAbsolutePath()).append(System.lineSeparator());
    sb.append("issues=").append(issues.size()).append(System.lineSeparator());
    sb.append("optimized=").append(applied ? "yes (written to file)" : "no (preview only)")
        .append(System.lineSeparator());

    if (issues.isEmpty()) {
      sb.append(System.lineSeparator()).append("No issues found. Script looks good.");
    } else {
      int num = 1;
      for (DwlAnalyzer.Issue issue : issues) {
        sb.append(System.lineSeparator());
        sb.append("[Issue ").append(num++).append("]").append(System.lineSeparator());
        sb.append("type: ").append(issue.type()).append(System.lineSeparator());
        sb.append("line: ").append(issue.line()).append(System.lineSeparator());
        sb.append("description: ").append(issue.description()).append(System.lineSeparator());
        if (!issue.suggestion().isBlank()) {
          sb.append("suggestion:").append(System.lineSeparator());
          for (String line : issue.suggestion().split("\\r?\\n", -1)) {
            sb.append("  ").append(line).append(System.lineSeparator());
          }
        }
      }
    }

    sb.append(System.lineSeparator());
    sb.append("[Suggested script").append(applied ? " (applied)" : " (preview)").append(":]")
        .append(System.lineSeparator());
    sb.append(optimizedScript);
    return sb.toString();
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
