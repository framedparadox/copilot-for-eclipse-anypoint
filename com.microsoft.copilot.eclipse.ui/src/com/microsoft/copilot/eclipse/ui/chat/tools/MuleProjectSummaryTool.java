// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.annotation.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.microsoft.copilot.eclipse.core.lsp.protocol.InputSchema;
import com.microsoft.copilot.eclipse.core.lsp.protocol.InputSchemaPropertyValue;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult.ToolInvocationStatus;
import com.microsoft.copilot.eclipse.ui.chat.ChatView;

/**
 * Read-only Mule project summarizer for Agent Mode.
 */
public class MuleProjectSummaryTool extends BaseTool {
  private static final String TOOL_NAME = "summarize_mule_project";
  private static final String PROJECT_PATH = "projectPath";
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
  private static final int MAX_XML_FILES = 50;

  public MuleProjectSummaryTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Summarize Mule XML flows and project metadata");
    toolInfo.setDescription("""
        Summarize a MuleSoft Anypoint Studio project by reading Mule XML files under src/main/mule.
        Use this before editing Mule flows to understand namespaces, flows, sub-flows, global configs,
        connectors, processors, and property placeholders.
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
        result.setStatus(ToolInvocationStatus.success);
        result.addContent(summarize(projectPath));
      }
    } catch (Exception e) {
      result.setStatus(ToolInvocationStatus.error);
      result.addContent("Failed to summarize Mule project: " + e.getMessage());
    }
    return CompletableFuture.completedFuture(new LanguageModelToolResult[] { result });
  }

  private String summarize(Path projectPath) throws Exception {
    List<Path> xmlFiles = findMuleXmlFiles(projectPath);
    if (xmlFiles.isEmpty()) {
      return "No Mule XML files found under " + projectPath.resolve("src/main/mule");
    }

    ProjectSummary summary = new ProjectSummary(projectPath);
    for (Path xmlFile : xmlFiles) {
      summarizeFile(xmlFile, summary);
    }
    return summary.render(xmlFiles.size());
  }

  private List<Path> findMuleXmlFiles(Path projectPath) throws Exception {
    Path muleDir = projectPath.resolve("src/main/mule");
    if (!Files.isDirectory(muleDir)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.walk(muleDir)) {
      return stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".xml"))
          .sorted().limit(MAX_XML_FILES).collect(Collectors.toList());
    }
  }

  private void summarizeFile(Path xmlFile, ProjectSummary summary) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
    trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
    trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
    trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);

    try (InputStream inputStream = Files.newInputStream(xmlFile)) {
      Document document = factory.newDocumentBuilder().parse(inputStream);
      Element root = document.getDocumentElement();
      summary.files.add(summary.projectPath.relativize(xmlFile).toString());
      collectNamespaces(root, summary.namespaces);
      collectPlaceholders(root.getTextContent(), summary.placeholders);
      collectElements(root, summary);
    }
  }

  private void collectNamespaces(Element root, Set<String> namespaces) {
    for (int i = 0; i < root.getAttributes().getLength(); i++) {
      Node attribute = root.getAttributes().item(i);
      String name = attribute.getNodeName();
      if ("xmlns".equals(name) || name.startsWith("xmlns:")) {
        namespaces.add(name + "=" + attribute.getNodeValue());
      }
    }
  }

  private void collectPlaceholders(String text, Set<String> placeholders) {
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
    while (matcher.find()) {
      placeholders.add(matcher.group(1));
    }
  }

  private void collectElements(Element root, ProjectSummary summary) {
    NodeList nodes = root.getElementsByTagName("*");
    for (int i = 0; i < nodes.getLength(); i++) {
      if (!(nodes.item(i) instanceof Element element)) {
        continue;
      }
      String localName = localName(element);
      String qualifiedName = qualifiedName(element);
      summary.processorCounts.merge(qualifiedName, 1, Integer::sum);
      if ("flow".equals(localName) || "sub-flow".equals(localName)) {
        String name = element.getAttribute("name");
        if (!name.isBlank()) {
          summary.flows.add(localName + ":" + name);
        }
      } else if (element.getParentNode() == root) {
        summary.globalConfigs.add(qualifiedName + optionalName(element));
      }
      collectPlaceholders(element.getTextContent(), summary.placeholders);
    }
  }

  private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean enabled) {
    try {
      factory.setFeature(feature, enabled);
    } catch (Exception ignored) {
      // Some XML parsers do not expose every hardening feature.
    }
  }

  private static String localName(Element element) {
    return element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
  }

  private static String qualifiedName(Element element) {
    String prefix = element.getPrefix();
    return prefix == null || prefix.isBlank() ? localName(element) : prefix + ":" + localName(element);
  }

  private static String optionalName(Element element) {
    String name = element.getAttribute("name");
    return name.isBlank() ? "" : "(" + name + ")";
  }

  @Nullable
  private static Path getProjectPath(Object value) {
    if (!(value instanceof String pathString) || pathString.isBlank()) {
      return null;
    }
    Path path = Path.of(pathString).toAbsolutePath().normalize();
    return Files.isDirectory(path) ? path : null;
  }

  private static final class ProjectSummary {
    private final Path projectPath;
    private final List<String> files = new ArrayList<>();
    private final Set<String> namespaces = new LinkedHashSet<>();
    private final Set<String> flows = new LinkedHashSet<>();
    private final Set<String> globalConfigs = new LinkedHashSet<>();
    private final Set<String> placeholders = new LinkedHashSet<>();
    private final Map<String, Integer> processorCounts = new LinkedHashMap<>();

    private ProjectSummary(Path projectPath) {
      this.projectPath = projectPath;
    }

    private String render(int fileCount) {
      StringBuilder builder = new StringBuilder();
      builder.append("Mule project: ").append(projectPath).append(System.lineSeparator());
      builder.append("Mule XML files scanned: ").append(fileCount).append(System.lineSeparator());
      appendList(builder, "Files", files);
      appendList(builder, "Flows and sub-flows", flows);
      appendList(builder, "Global configs", globalConfigs);
      appendList(builder, "Namespaces", namespaces);
      appendList(builder, "Property placeholders", placeholders);
      appendTopProcessors(builder);
      return builder.toString();
    }

    private void appendTopProcessors(StringBuilder builder) {
      builder.append("Top processors/connectors:").append(System.lineSeparator());
      processorCounts.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(25)
          .forEach(entry -> builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
              .append(System.lineSeparator()));
    }

    private static void appendList(StringBuilder builder, String label, Iterable<String> values) {
      builder.append(label).append(":").append(System.lineSeparator());
      boolean hasValue = false;
      for (String value : values) {
        builder.append("- ").append(value).append(System.lineSeparator());
        hasValue = true;
      }
      if (!hasValue) {
        builder.append("- none").append(System.lineSeparator());
      }
    }
  }
}
