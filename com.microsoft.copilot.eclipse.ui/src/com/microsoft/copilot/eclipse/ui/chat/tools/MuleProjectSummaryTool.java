// Copyright (c) Microsoft Corporation.
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
        This tool is read-only.
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
