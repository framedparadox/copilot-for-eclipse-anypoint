// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

import com.microsoft.copilot.eclipse.core.lsp.protocol.InputSchema;
import com.microsoft.copilot.eclipse.core.lsp.protocol.InputSchemaPropertyValue;

/**
 * Shared input helpers for MuleSoft tools.
 */
final class MuleToolInputs {
  static final String PROJECT_PATH = "projectPath";
  static final String SCHEMA_PATH = "schemaPath";
  static final String SCHEMA_TYPE = "schemaType";
  static final String RULESET_PATH = "rulesetPath";
  static final String FILES = "files";
  static final String FLOW_NAME = "flowName";
  static final String MUNIT_PATH = "munitPath";
  static final String REVIEW_TYPE = "reviewType";
  static final String SCOPE = "scope";
  static final String API_EXPOSURE = "apiExposure";
  static final String XML_FILE_PATH = "xmlFilePath";
  static final String DWL_FILE_PATH = "dwlFilePath";
  static final String TRANSFORM_NAME = "transformName";
  static final String TRANSFORM_ID = "transformId";
  static final String DWL_SCRIPT = "dwlScript";
  static final String VARIABLE_NAME = "variableName";
  static final String TARGET = "target";
  static final String LAYER = "layer";
  static final String TARGET_ENVIRONMENT = "targetEnvironment";
  static final String MAVEN_PROFILE = "mavenProfile";
  static final String INCLUDE_COMMENTS = "includeComments";
  static final String APPLY_FIXES = "applyFixes";

  private MuleToolInputs() {
  }

  static InputSchema projectPathSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(PROJECT_PATH,
        new InputSchemaPropertyValue("string", "Absolute path to the Mule project folder")));
    inputSchema.setRequired(List.of(PROJECT_PATH));
    return inputSchema;
  }

  static InputSchema schemaAnalyzeSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(
        SCHEMA_PATH, new InputSchemaPropertyValue("string",
            "Absolute path to RAML/OpenAPI/OData/AsyncAPI/WSDL/XSD/JSON schema file"),
        SCHEMA_TYPE, new InputSchemaPropertyValue("string",
            "Optional schema type such as raml, openapi, odata, asyncapi, graphql, wsdl, xsd, jsonschema, or avro"),
        RULESET_PATH, new InputSchemaPropertyValue("string", "Optional absolute path to governance ruleset")));
    inputSchema.setRequired(List.of(SCHEMA_PATH));
    return inputSchema;
  }

  static InputSchema codeReviewSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    InputSchemaPropertyValue files = new InputSchemaPropertyValue("array",
        "Optional project-relative files to review; omit for full project");
    files.setItems(new InputSchemaPropertyValue("string", "Project-relative file path"));
    inputSchema.setProperties(Map.of(
        PROJECT_PATH, new InputSchemaPropertyValue("string", "Absolute path to the Mule project folder"),
        FILES, files,
        REVIEW_TYPE, new InputSchemaPropertyValue("string", "architecture, code, pr, or full"),
        LAYER, new InputSchemaPropertyValue("string", "API-led layer: experience, process, system, or unknown")));
    inputSchema.setRequired(List.of(PROJECT_PATH));
    return inputSchema;
  }

  static InputSchema securityReviewSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(
        PROJECT_PATH, new InputSchemaPropertyValue("string", "Absolute path to the Mule project folder"),
        SCOPE, new InputSchemaPropertyValue("string", "full, changed-files, or active-file"),
        API_EXPOSURE, new InputSchemaPropertyValue("string", "public, partner, or internal"),
        TARGET_ENVIRONMENT,
        new InputSchemaPropertyValue("string", "cloudhub, cloudhub2, rtf, standalone, or unknown")));
    inputSchema.setRequired(List.of(PROJECT_PATH));
    return inputSchema;
  }

  static InputSchema munitValidationSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(
        PROJECT_PATH, new InputSchemaPropertyValue("string", "Absolute path to the Mule project folder"),
        FLOW_NAME, new InputSchemaPropertyValue("string", "Optional Mule flow name to validate test coverage for"),
        MUNIT_PATH, new InputSchemaPropertyValue("string", "Optional absolute path to one MUnit suite file"),
        LAYER, new InputSchemaPropertyValue("string", "API-led layer: experience, process, system, or unknown")));
    inputSchema.setRequired(List.of(PROJECT_PATH));
    return inputSchema;
  }

  @Nullable
  static Path existingDirectory(Object value) {
    if (!(value instanceof String pathString) || pathString.isBlank()) {
      return null;
    }
    Path path = Path.of(pathString).toAbsolutePath().normalize();
    return Files.isDirectory(path) ? path : null;
  }

  @Nullable
  static Path existingFile(Object value) {
    if (!(value instanceof String pathString) || pathString.isBlank()) {
      return null;
    }
    Path path = Path.of(pathString).toAbsolutePath().normalize();
    return Files.isRegularFile(path) ? path : null;
  }

  @Nullable
  static Path optionalPath(Object value) {
    if (!(value instanceof String pathString) || pathString.isBlank()) {
      return null;
    }
    return Path.of(pathString).toAbsolutePath().normalize();
  }

  static String optionalString(Object value) {
    return value instanceof String string ? string : "";
  }

  static List<String> optionalStringList(Object value) {
    if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
      return list.stream().map(String.class::cast).filter(item -> !item.isBlank()).toList();
    }
    return List.of();
  }

  static InputSchema transformReadSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(
        XML_FILE_PATH, new InputSchemaPropertyValue("string",
            "Absolute path to a Mule XML file containing ee:transform elements"),
        TRANSFORM_NAME, new InputSchemaPropertyValue("string",
            "Optional doc:name of the Transform Message component to read"),
        TRANSFORM_ID, new InputSchemaPropertyValue("string",
            "Optional doc:id of the Transform Message component to read")));
    inputSchema.setRequired(List.of(XML_FILE_PATH));
    return inputSchema;
  }

  static InputSchema transformWriteSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(
        XML_FILE_PATH, new InputSchemaPropertyValue("string",
            "Absolute path to the Mule XML file containing the target ee:transform element"),
        TRANSFORM_NAME, new InputSchemaPropertyValue("string",
            "doc:name of the Transform Message component to update"),
        TRANSFORM_ID, new InputSchemaPropertyValue("string",
            "doc:id of the Transform Message component to update"),
        TARGET, new InputSchemaPropertyValue("string",
            "What to update: 'payload', 'attributes', 'variable:name', or a variable name"),
        DWL_SCRIPT, new InputSchemaPropertyValue("string",
            "Complete DataWeave 2.0 script starting with %dw 2.0 and output directive")));
    inputSchema.setRequired(List.of(XML_FILE_PATH, DWL_SCRIPT));
    return inputSchema;
  }

  static InputSchema dwlReadSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(
        DWL_FILE_PATH, new InputSchemaPropertyValue("string",
            "Absolute path to a standalone DataWeave module (.dwl) file")));
    inputSchema.setRequired(List.of(DWL_FILE_PATH));
    return inputSchema;
  }

  static InputSchema dwlWriteSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(
        DWL_FILE_PATH, new InputSchemaPropertyValue("string",
            "Absolute path to the .dwl file to write"),
        DWL_SCRIPT, new InputSchemaPropertyValue("string",
            "Complete replacement DataWeave 2.0 script (should start with %dw 2.0 and output directive)")));
    inputSchema.setRequired(List.of(DWL_FILE_PATH, DWL_SCRIPT));
    return inputSchema;
  }

  static InputSchema dwlOptimizeSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(
        DWL_FILE_PATH, new InputSchemaPropertyValue("string",
            "Absolute path to the .dwl file to analyze and optimize"),
        INCLUDE_COMMENTS, new InputSchemaPropertyValue("boolean",
            "Whether to add inline comments to undocumented functions (default: true)"),
        APPLY_FIXES, new InputSchemaPropertyValue("boolean",
            "Whether to write the optimized script back to the file (default: false — preview only)")));
    inputSchema.setRequired(List.of(DWL_FILE_PATH));
    return inputSchema;
  }
}
