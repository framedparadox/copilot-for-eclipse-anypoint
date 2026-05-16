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
 * MuleSoft security review tool.
 */
public class MuleSecurityReviewTool extends BaseTool {
  static final String TOOL_NAME = "mule_security_review";

  /**
   * Creates a Mule security review tool.
   */
  public MuleSecurityReviewTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Run MuleSoft security review");
    toolInfo.setDescription("""
        Perform a security review for MuleSoft projects by scanning Mule XML, property files, POM metadata,
        and API specs for hardcoded secrets, insecure HTTP, missing secure properties, missing API contracts,
        unsafe logging signals, and policy review prompts. This tool is read-only.
        """);
    toolInfo.setInputSchema(MuleToolInputs.securityReviewSchema());
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
        result.addContent(MuleProjectAnalyzer.securityReviewResponse(projectPath,
            MuleToolInputs.optionalString(input.get(MuleToolInputs.SCOPE)),
            MuleToolInputs.optionalString(input.get(MuleToolInputs.API_EXPOSURE))).toJson());
      }
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to run Mule security review: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }
}
