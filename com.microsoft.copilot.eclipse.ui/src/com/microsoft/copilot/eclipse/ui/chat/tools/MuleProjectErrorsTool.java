// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.Nullable;

import com.microsoft.copilot.eclipse.core.lsp.protocol.InputSchema;
import com.microsoft.copilot.eclipse.core.lsp.protocol.InputSchemaPropertyValue;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult.ToolInvocationStatus;
import com.microsoft.copilot.eclipse.ui.chat.ChatView;

/**
 * Reads Eclipse problem markers for a Mule project.
 */
public class MuleProjectErrorsTool extends BaseTool {
  private static final String TOOL_NAME = "get_mule_project_errors";
  private static final String PROJECT_PATH = "projectPath";

  public MuleProjectErrorsTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Read Anypoint Studio Mule project problem markers");
    toolInfo.setDescription("""
        Get Eclipse problem markers for a MuleSoft Anypoint Studio project.
        Use this after inspecting or editing Mule XML, DataWeave, RAML/OpenAPI, or MUnit files to see
        validation errors from the same workspace problem marker system that Anypoint Studio uses.
        This tool is read-only.
        """);
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    Map<String, InputSchemaPropertyValue> properties = new LinkedHashMap<>();
    properties.put(PROJECT_PATH, new InputSchemaPropertyValue("string", "Absolute path to the Mule project folder"));
    inputSchema.setProperties(properties);
    inputSchema.setRequired(List.of(PROJECT_PATH));
    toolInfo.setInputSchema(inputSchema);
    return toolInfo;
  }

  @Override
  public CompletableFuture<LanguageModelToolResult[]> invoke(Map<String, Object> input, ChatView chatView) {
    LanguageModelToolResult result = new LanguageModelToolResult();
    try {
      Path projectPath = getProjectPath(input.get(PROJECT_PATH));
      if (projectPath == null) {
        result.setStatus(ToolInvocationStatus.error);
        result.addContent("projectPath must be an absolute path to an existing Mule project folder.");
      } else {
        String errors = getErrors(projectPath);
        result.setStatus(ToolInvocationStatus.success);
        result.addContent(errors);
      }
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to read Mule project errors: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }

  private String getErrors(Path projectPath) throws CoreException {
    IContainer[] containers = ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(projectPath.toUri());
    if (containers == null || containers.length == 0) {
      return "No Eclipse workspace project or folder is mapped to " + projectPath;
    }

    StringBuilder builder = new StringBuilder();
    for (IContainer container : containers) {
      appendMarkers(container, builder);
    }
    return builder.length() == 0 ? "No error markers found for " + projectPath : builder.toString();
  }

  private void appendMarkers(IContainer container, StringBuilder builder) throws CoreException {
    IMarker[] markers = container.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
    for (IMarker marker : markers) {
      if (marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) != IMarker.SEVERITY_ERROR) {
        continue;
      }
      IResource resource = marker.getResource();
      Object line = marker.getAttribute(IMarker.LINE_NUMBER);
      Object message = marker.getAttribute(IMarker.MESSAGE);
      builder.append(resource == null ? container.getName() : resource.getProjectRelativePath().toString());
      if (line != null) {
        builder.append(":").append(line);
      }
      builder.append(" - ").append(message == null ? "Unknown problem marker" : message).append(System.lineSeparator());
    }
  }

  @Nullable
  private static Path getProjectPath(Object value) {
    if (!(value instanceof String pathString) || pathString.isBlank()) {
      return null;
    }
    Path path = Path.of(pathString).toAbsolutePath().normalize();
    if (!Files.isDirectory(path)) {
      return null;
    }
    URI uri = path.toUri();
    return uri == null ? null : path;
  }
}
