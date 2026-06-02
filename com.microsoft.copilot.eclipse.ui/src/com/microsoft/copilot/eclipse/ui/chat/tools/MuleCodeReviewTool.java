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
 * Senior-level MuleSoft code review tool.
 */
public class MuleCodeReviewTool extends BaseTool {
  static final String TOOL_NAME = "mule_code_review";

  /**
   * Creates a Mule code review tool.
   */
  public MuleCodeReviewTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Review Mule XML, DataWeave, specs, and tests");
    toolInfo.setDescription("""
        Perform a MuleSoft code review across Mule XML, DataWeave, properties, API specs, MUnit, and POM metadata.
        Checks: flow naming conventions (camelCase verb-noun), duplicate or unused global configs, missing
        On Error Propagate on HTTP-facing flows, correlation ID propagation in error handlers, property placeholder
        externalization (secure:: for secrets, plain placeholder for env values), APIkit route coverage vs API spec,
        DataWeave output type declarations and null-safety, and MUnit test coverage gaps.
        Common findings: flows with no error handler, hardcoded URLs in global configs, on-error-continue misused
        as a catch-all, flows with zero MUnit coverage, DataWeave scripts missing output directive.
        This tool is read-only and returns findings by severity (critical, high, medium, low) with remediation guidance.
        """);
    toolInfo.setInputSchema(MuleToolInputs.codeReviewSchema());
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
        result.addContent(MuleProjectAnalyzer.codeReviewResponse(projectPath,
            MuleToolInputs.optionalStringList(input.get(MuleToolInputs.FILES)),
            MuleToolInputs.optionalString(input.get(MuleToolInputs.REVIEW_TYPE))).toJson());
      }
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to review Mule project: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }
}
