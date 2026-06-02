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
 * Suggests improvements for MUnit coverage cadence and maintainability.
 */
public class MunitImprovementSuggestionsTool extends BaseTool {
  static final String TOOL_NAME = "munit_improvement_suggestions";

  /**
   * Creates an MUnit improvement suggestion tool.
   */
  public MunitImprovementSuggestionsTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Suggest MUnit cadence and coverage improvements");
    toolInfo.setDescription("""
        Review MUnit coverage cadence for a Mule project or flow. Suggests focused improvements for scenario mix,
        assertion depth, external connector mocking, branch and error-path tests, and maintainable test naming. This
        tool is read-only and returns structured recommendations.
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
        result.addContent(MuleProjectAnalyzer.munitImprovementSuggestionsResponse(projectPath,
            MuleToolInputs.optionalString(input.get(MuleToolInputs.FLOW_NAME)),
            MuleToolInputs.optionalPath(input.get(MuleToolInputs.MUNIT_PATH))).toJson());
      }
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to suggest MUnit improvements: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }
}
