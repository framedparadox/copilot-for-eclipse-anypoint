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
 * Deterministic Mule project scanner for Agent Mode.
 */
public class MuleProjectScanTool extends BaseTool {
  static final String TOOL_NAME = "mule_project_scan";

  /**
   * Creates a Mule project scanner tool.
   */
  public MuleProjectScanTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Scan Mule project structure and metadata");
    toolInfo.setDescription("""
        Detect Mule project structure and metadata. Run this first on any Mule task before code review, security review,
        or XML edits. Returns: Mule runtime version, all Mule XML file paths, flow and sub-flow names, API spec paths
        (RAML, OpenAPI, WSDL), MUnit suite paths and test counts, connector dependencies with versions, APIkit usage,
        deployment plugins (CloudHub, Runtime Fabric), property placeholder patterns, and immediate diagnostics
        (missing mule-artifact.json, missing POM, no MUnit coverage, no API spec).
        Use the runtime version and connector list to check version compatibility before suggesting upgrades.
        Use the MUnit coverage data to identify flows with no test coverage.
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
        result.addContent(MuleProjectAnalyzer.projectScanResponse(projectPath).toJson());
      }
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to scan Mule project: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }
}
