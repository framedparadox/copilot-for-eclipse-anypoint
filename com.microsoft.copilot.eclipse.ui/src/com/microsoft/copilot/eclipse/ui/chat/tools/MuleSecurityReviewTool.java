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
        and API specs. Detects: hardcoded credentials (password, secret, token, apikey, clientsecret patterns in XML
        or property files), plain ${property} references for sensitive values that should use ${secure::property},
        missing Secure Configuration Properties module dependency, insecure HTTP Listener endpoints (HTTP not HTTPS),
        outbound HTTP Request configs with insecure="true" or missing TLS context, Database connector queries
        with string-concatenated SQL (SQL injection risk), unsafe payload logging in Logger components,
        flows with no authentication mechanism on HTTP-facing endpoints, and missing API policy coverage.
        Common high-severity findings: base64-encoded credentials in XML attributes, passwords in config-default.yaml,
        HTTP Listener on port 8081 without TLS in a production-bound project.
        This tool is read-only and classifies findings as critical, high, medium, or low.
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
