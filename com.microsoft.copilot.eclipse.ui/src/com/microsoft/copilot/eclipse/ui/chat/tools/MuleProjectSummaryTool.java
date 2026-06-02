// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult.ToolInvocationStatus;
import com.microsoft.copilot.eclipse.ui.chat.ChatView;

/**
 * Read-only Mule project summarizer for Agent Mode.
 */
public class MuleProjectSummaryTool extends BaseTool {
  private static final String TOOL_NAME = "summarize_mule_project";

  /**
   * Creates a Mule project summary tool.
   */
  public MuleProjectSummaryTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Summarize Mule XML flows and project metadata");
    toolInfo.setDescription("""
        Summarize a MuleSoft Anypoint Studio project by reading Mule XML files under src/main/mule,
        project metadata, API specs, MUnit suites, connectors, deployment plugins, namespaces,
        flows, sub-flows, global configs, processors, and property placeholders.
        Also surfaces: hasApikit, hasSecureProperties, hasBatchJob, hasReconnectForever,
        log4j2RootLevel, hasDbPoolConfig, hasHttpRequestTimeout, scheduler-triggered flows,
        flows with correlationId set, and a diagnostic count.
        Use mule_project_scan for a full structured JSON response including all diagnostics.
        This tool is read-only and returns a human-readable text summary.
        """);
    toolInfo.setInputSchema(MuleToolInputs.projectPathSchema());
    return toolInfo;
  }

  @Override
  public CompletableFuture<LanguageModelToolResult[]> invoke(Map<String, Object> input, ChatView chatView) {
    LanguageModelToolResult result = new LanguageModelToolResult();
    try {
      Path projectPath = MuleToolInputs.existingDirectory(input.get(MuleToolInputs.PROJECT_PATH));
      if (projectPath == null) {
        result.setStatus(ToolInvocationStatus.error);
        result.addContent("projectPath must be an absolute path to an existing Mule project folder.");
      } else {
        result.setStatus(ToolInvocationStatus.success);
        result.addContent(MuleProjectAnalyzer.renderSummary(MuleProjectAnalyzer.scan(projectPath)));
      }
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to summarize Mule project: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }
}
