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
        Reports syntax errors and governance diagnostics. Governance issues include: missing required metadata
        (title, version, baseUri/servers), missing examples on request or response bodies, missing error response
        definitions (400, 401, 404, 500), no security scheme defined, inline anonymous schemas that should be
        named reusable types, inconsistent naming conventions across endpoints, and APIkit compatibility issues
        (RAML baseUri and version must match the APIkit router config, all resources must have at least one method).
        Common findings: RAML with no securitySchemes, OpenAPI with 200-only responses on POST endpoints,
        RAML types section missing (all schemas inline), examples that do not validate against their schema.
        This tool is read-only.
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
