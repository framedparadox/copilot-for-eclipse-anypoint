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
 * Validates whether MUnit suites meaningfully test Mule flows.
 */
public class MunitValidateFlowTestsTool extends BaseTool {
  static final String TOOL_NAME = "munit_validate_flow_tests";

  /**
   * Creates an MUnit flow test validator tool.
   */
  public MunitValidateFlowTestsTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Validate MUnit purpose, structure, and flow coverage");
    toolInfo.setDescription("""
        Validate MUnit suites for Mule flows. Checks whether tests have a logical purpose, whether MUnit namespaces,
        schema locations, munit:config, execution, validation, assertions, mocks, spies, and verify-call processors are
        present, and whether flow components and behavioral aspects are covered. This tool is read-only.
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
        result.addContent(MuleProjectAnalyzer.munitValidationResponse(projectPath,
            MuleToolInputs.optionalString(input.get(MuleToolInputs.FLOW_NAME)),
            MuleToolInputs.optionalPath(input.get(MuleToolInputs.MUNIT_PATH))).toJson());
      }
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to validate MUnit suites: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }
}
