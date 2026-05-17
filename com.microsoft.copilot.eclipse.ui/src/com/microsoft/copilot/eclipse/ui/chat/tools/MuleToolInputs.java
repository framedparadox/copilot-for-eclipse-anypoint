// Copyright (c) Microsoft Corporation.
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
        REVIEW_TYPE, new InputSchemaPropertyValue("string", "architecture, code, pr, or full")));
    inputSchema.setRequired(List.of(PROJECT_PATH));
    return inputSchema;
  }

  static InputSchema securityReviewSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(
        PROJECT_PATH, new InputSchemaPropertyValue("string", "Absolute path to the Mule project folder"),
        SCOPE, new InputSchemaPropertyValue("string", "full, changed-files, or active-file"),
        API_EXPOSURE, new InputSchemaPropertyValue("string", "public, partner, or internal")));
    inputSchema.setRequired(List.of(PROJECT_PATH));
    return inputSchema;
  }

  static InputSchema munitValidationSchema() {
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    inputSchema.setProperties(Map.of(
        PROJECT_PATH, new InputSchemaPropertyValue("string", "Absolute path to the Mule project folder"),
        FLOW_NAME, new InputSchemaPropertyValue("string", "Optional Mule flow name to validate test coverage for"),
        MUNIT_PATH, new InputSchemaPropertyValue("string", "Optional absolute path to one MUnit suite file")));
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
}
