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
 * Lightweight API schema analyzer for MuleSoft projects.
 */
public class ApiSchemaAnalyzeTool extends BaseTool {
  static final String TOOL_NAME = "api_schema_analyze";

  /**
   * Creates an API schema analyzer tool.
   */
  public ApiSchemaAnalyzeTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Analyze Mule API schema files");
    toolInfo.setDescription("""
        Analyze RAML, OpenAPI, OData, AsyncAPI, GraphQL, WSDL, XSD, JSON Schema, Avro, CSV, or flat-file metadata.
        Reports syntax and governance-oriented diagnostics such as missing examples, error responses,
        security definitions, and APIkit compatibility hints. This tool is read-only.
        """);
    toolInfo.setInputSchema(MuleToolInputs.schemaAnalyzeSchema());
    return toolInfo;
  }

  @Override
  public CompletableFuture<LanguageModelToolResult[]> invoke(Map<String, Object> input, ChatView chatView) {
    LanguageModelToolResult result = new LanguageModelToolResult();
    try {
      Path schemaPath = MuleToolInputs.existingFile(input.get(MuleToolInputs.SCHEMA_PATH));
      if (schemaPath == null) {
        result.setStatus(ToolInvocationStatus.error);
        result.addContent("schemaPath must be an absolute path to an existing schema file.");
      } else {
        result.setStatus(ToolInvocationStatus.success);
        result.addContent(MuleProjectAnalyzer.schemaAnalyzeResponse(schemaPath,
            MuleToolInputs.optionalString(input.get(MuleToolInputs.SCHEMA_TYPE)),
            MuleToolInputs.optionalPath(input.get(MuleToolInputs.RULESET_PATH))).toJson());
      }
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to analyze API schema: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }
}
