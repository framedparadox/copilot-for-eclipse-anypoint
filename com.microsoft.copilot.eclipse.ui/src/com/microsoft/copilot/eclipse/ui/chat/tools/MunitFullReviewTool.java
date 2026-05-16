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
 * Reviews MUnit suites for purpose, coverage, and assertion quality.
 */
public class MunitFullReviewTool extends BaseTool {
  static final String TOOL_NAME = "munit_full_review";

  /**
   * Creates an MUnit full review tool.
   */
  public MunitFullReviewTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Review MUnit purpose, coverage, and quality");
    toolInfo.setDescription("""
        Perform a full read-only MUnit review for Mule flows. Combines structure validation, logical test-purpose
        checks, component coverage, branch and error-path review, assertion quality, external connector mock coverage,
        and scenario completeness. Returns structured findings and next actions.
        """);
    toolInfo.setInputSchema(MuleToolInputs.munitValidationSchema());
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
        result.addContent(MuleProjectAnalyzer.munitFullReviewResponse(projectPath,
            MuleToolInputs.optionalString(input.get(MuleToolInputs.FLOW_NAME)),
            MuleToolInputs.optionalPath(input.get(MuleToolInputs.MUNIT_PATH))).toJson());
      }
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to review MUnit suites: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }
}
