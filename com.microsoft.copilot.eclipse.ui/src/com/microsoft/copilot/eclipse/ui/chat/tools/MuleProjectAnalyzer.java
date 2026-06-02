// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parser-backed Mule project analyzer used by MuleSoft Copilot chat tools.
 */
final class MuleProjectAnalyzer {
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
  private static final Pattern SECRET_LINE_PATTERN =
      Pattern.compile("(?i)^\\s*[^#\\s][^=]*(password|secret|token|apikey|api-key|client_secret|clientSecret)"
          + "[^=]*=(.*)$");
  private static final Pattern RAML_RESPONSE_PATTERN = Pattern.compile("^\\s{2,}[1-5][0-9][0-9]:\\s*$");
  private static final int MAX_FILES = 200;
  private static final Set<String> API_SPEC_EXTENSIONS = Set.of(".raml", ".yaml", ".yml", ".json", ".wsdl", ".xsd",
      ".graphql", ".avsc", ".csv");

  private MuleProjectAnalyzer() {
  }

  static MuleProjectAnalysis scan(Path projectPath) throws Exception {
    MuleProjectAnalysis analysis = new MuleProjectAnalysis(projectPath);
    analysis.hasPom = Files.isRegularFile(projectPath.resolve("pom.xml"));
    analysis.hasMuleArtifact = Files.isRegularFile(projectPath.resolve("mule-artifact.json"));

    if (analysis.hasPom) {
      parsePom(projectPath.resolve("pom.xml"), analysis);
    } else {
      analysis.diagnostics.add(MuleDiagnostic.medium("pom.xml", 0, "Mule project is missing pom.xml.",
          "Add the Mule Maven project descriptor or select the Mule project root."));
    }

    if (analysis.hasMuleArtifact) {
      parseMuleArtifact(projectPath.resolve("mule-artifact.json"), analysis);
    } else {
      analysis.diagnostics.add(MuleDiagnostic.medium("mule-artifact.json", 0,
          "Mule project is missing mule-artifact.json.", "Add deployment metadata with the target Mule runtime."));
    }

    listFiles(projectPath.resolve("src/main/mule"), ".xml")
        .forEach(file -> parseMuleXml(projectPath, file, analysis));
    Path log4j2Path = projectPath.resolve("src/main/resources/log4j2.xml");
    if (Files.isRegularFile(log4j2Path)) {
      parseLog4j2(log4j2Path, analysis);
    }
    listFiles(projectPath.resolve("src/main/resources"), null).forEach(file -> {
      String relative = relativize(projectPath, file);
      analysis.resourceFiles.add(relative);
      if (isApiSpec(file)) {
        analysis.apiSpecFiles.add(relative);
      }
    });
    listFiles(projectPath.resolve("src/test/munit"), ".xml")
        .forEach(file -> analysis.munitFiles.add(relativize(projectPath, file)));

    addProjectLevelDiagnostics(analysis);
    return analysis;
  }

  static MuleToolResponse projectScanResponse(Path projectPath) throws Exception {
    MuleProjectAnalysis analysis = scan(projectPath);
    MuleToolResponse response = new MuleToolResponse("success", "Scanned Mule project " + projectPath);
    response.addArtifact("runtimeVersion=" + blankToUnknown(analysis.muleRuntimeVersion));
    response.addArtifact("muleXmlFiles=" + analysis.muleXmlFiles.size());
    response.addArtifact("apiSpecFiles=" + analysis.apiSpecFiles.size());
    response.addArtifact("munitFiles=" + analysis.munitFiles.size());
    response.addArtifact("connectors=" + String.join(", ", analysis.connectorDependencies));
    response.addArtifact("deploymentPlugins=" + String.join(", ", analysis.deploymentPlugins));
    response.addArtifact("flows=" + String.join(", ", analysis.flows));
    response.addArtifact("subFlows=" + String.join(", ", analysis.subFlows));
    response.addArtifact("globalConfigs=" + String.join(", ", analysis.globalConfigs));
    response.addArtifact("propertyPlaceholders=" + String.join(", ", analysis.placeholders));
    response.addArtifact("hasApikit=" + analysis.hasApikit);
    response.addArtifact("hasSecureProperties=" + analysis.hasSecureProperties);
    response.addArtifact("hasBatchJob=" + analysis.hasBatchJob);
    response.addArtifact("schedulerFlows=" + String.join(", ", analysis.schedulerFlows));
    response.addArtifact("hasReconnectForever=" + analysis.hasReconnectForever);
    response.addArtifact("log4j2RootLevel=" + blankToUnknown(analysis.log4j2RootLevel));
    response.addArtifact("hasDbPoolConfig=" + analysis.hasDbPoolConfig);
    response.addArtifact("hasHttpRequestTimeout=" + analysis.hasHttpRequestTimeout);
    response.addArtifact("flowsWithCorrelationId=" + String.join(", ", analysis.flowsWithCorrelationId));
    String errorHandlerSummary = analysis.flowErrorHandlerTypes.entrySet().stream()
        .map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(", "));
    response.addArtifact("flowErrorHandlerTypes=" + (errorHandlerSummary.isBlank() ? "none" : errorHandlerSummary));
    if (!analysis.untilSuccessfulWithoutMaxRetries.isEmpty()) {
      response.addArtifact("untilSuccessfulWithoutMaxRetries=" + String.join(", ",
          analysis.untilSuccessfulWithoutMaxRetries));
    }
    response.addDiagnostics(analysis.diagnostics);
    response.addNextAction("Run mule_code_review for maintainability findings.");
    response.addNextAction("Run mule_security_review before committing Mule configuration or property changes.");
    return response;
  }

  static MuleToolResponse codeReviewResponse(Path projectPath, List<String> files, String reviewType) throws Exception {
    MuleProjectAnalysis analysis = scan(projectPath);
    List<MuleDiagnostic> diagnostics = new ArrayList<>(analysis.diagnostics);
    if (analysis.munitFiles.isEmpty()) {
      diagnostics.add(MuleDiagnostic.medium("src/test/munit", 0, "No MUnit suites were found.",
          "Add positive, negative, and edge-case MUnit coverage for changed flows."));
    }
    if (analysis.apiSpecFiles.isEmpty() && analysis.hasApikit) {
      diagnostics.add(MuleDiagnostic.high("src/main/resources", 0,
          "APIkit usage was detected but no API specification file was found in resources.",
          "Add or reference the RAML/OpenAPI contract used by APIkit routing."));
    }
    addFlowStructureFindings(projectPath, analysis, diagnostics);
    addDataWeaveFindings(projectPath, files, diagnostics);

    MuleToolResponse response = new MuleToolResponse(diagnostics.isEmpty() ? "success" : "partial",
        "Completed Mule " + emptyToDefault(reviewType, "code") + " review for " + projectPath);
    response.addArtifact("reviewedFiles=" + (files == null || files.isEmpty() ? "project" : String.join(", ", files)));
    response.addArtifact("flows=" + analysis.flows.size());
    response.addArtifact("munitSuites=" + analysis.munitFiles.size());
    response.addDiagnostics(diagnostics);
    response.addNextAction("Apply minimal fixes for high and critical findings first.");
    response.addNextAction("Run run_mule_maven_tests with goals [\"test\"] after changes.");
    return response;
  }

  static MuleToolResponse securityReviewResponse(Path projectPath, String scope, String apiExposure) throws Exception {
    MuleProjectAnalysis analysis = scan(projectPath);
    List<MuleDiagnostic> diagnostics = new ArrayList<>(analysis.diagnostics);
    addSecretFindings(projectPath, diagnostics);
    addXmlSecurityFindings(projectPath, analysis, diagnostics);
    if ("public".equalsIgnoreCase(apiExposure) && analysis.apiSpecFiles.isEmpty()) {
      diagnostics.add(MuleDiagnostic.high("src/main/resources", 0,
          "Public API exposure was selected but no API contract was found.",
          "Require a RAML/OpenAPI contract with auth, examples, validation, and error responses."));
    }
    if (!analysis.hasSecureProperties) {
      diagnostics.add(MuleDiagnostic.medium("src/main/mule", 0,
          "No secure-properties configuration was detected.",
          "Use Mule secure properties or external secret references for sensitive configuration."));
    }

    MuleToolResponse response = new MuleToolResponse(diagnostics.isEmpty() ? "success" : "partial",
        "Completed Mule security review for " + projectPath);
    response.addArtifact("scope=" + emptyToDefault(scope, "full"));
    response.addArtifact("apiExposure=" + emptyToDefault(apiExposure, "internal"));
    response.addDiagnostics(diagnostics);
    response.addNextAction("Move hardcoded secrets into secure properties before merge.");
    response.addNextAction("Confirm API Manager or implementation-level authentication and authorization policies.");
    return response;
  }

  static MuleToolResponse schemaAnalyzeResponse(Path schemaPath, String schemaType, Path rulesetPath) throws Exception {
    String content = Files.readString(schemaPath, StandardCharsets.UTF_8);
    String inferredType = inferSchemaType(schemaPath, schemaType, content);
    List<MuleDiagnostic> diagnostics = new ArrayList<>();
    List<String> artifacts = new ArrayList<>();
    artifacts.add("schemaType=" + inferredType);
    artifacts.add("schemaPath=" + schemaPath);

    switch (inferredType) {
      case "raml" -> analyzeRaml(schemaPath, content, diagnostics, artifacts);
      case "openapi" -> analyzeOpenApi(schemaPath, content, diagnostics, artifacts);
      case "wsdl", "xsd" -> analyzeXmlContract(schemaPath, content, diagnostics, artifacts);
      case "jsonschema", "avro" -> analyzeJsonContract(schemaPath, content, diagnostics, artifacts);
      default -> diagnostics.add(MuleDiagnostic.info(schemaPath.toString(),
          "Schema type was inferred as " + inferredType + " with lightweight validation only.",
          "Provide a schemaType value or ruleset for deeper validation."));
    }
    if (rulesetPath != null && !Files.isRegularFile(rulesetPath)) {
      diagnostics.add(MuleDiagnostic.medium(rulesetPath.toString(), 0, "Ruleset path does not exist.",
          "Provide an existing governance ruleset file or omit rulesetPath."));
    }

    MuleToolResponse response = new MuleToolResponse(diagnostics.isEmpty() ? "success" : "partial",
        "Analyzed API schema " + schemaPath.getFileName());
    artifacts.forEach(response::addArtifact);
    response.addDiagnostics(diagnostics);
    response.addNextAction("Address missing examples, error responses, and security definitions before "
        + "implementation.");
    return response;
  }

  static MuleToolResponse munitValidationResponse(Path projectPath, String flowName, Path munitPath) throws Exception {
    MuleProjectAnalysis analysis = scan(projectPath);
    List<MuleDiagnostic> diagnostics = new ArrayList<>(analysis.diagnostics);
    List<Path> munitFiles = resolveMunitFiles(projectPath, analysis, munitPath, diagnostics);
    Map<String, List<MuleFlowComponent>> flowComponents = readFlowComponents(projectPath, diagnostics);
    List<MunitSuiteSummary> suites = new ArrayList<>();

    for (Path file : munitFiles) {
      suites.add(analyzeMunitSuite(projectPath, file, diagnostics));
    }

    validateMunitPurposeAndCoverage(projectPath, emptyToDefault(flowName, ""), flowComponents, suites, diagnostics);

    MuleToolResponse response = new MuleToolResponse(diagnostics.isEmpty() ? "success" : "partial",
        "Validated MUnit structure and flow coverage for " + projectPath);
    response.addArtifact("requiredNamespaces=munit, munit-tools");
    response.addArtifact("requiredSchemaLocations=mule-munit.xsd, mule-munit-tools.xsd");
    response.addArtifact("requiredSuiteElement=munit:config");
    response.addArtifact("requiredTestStructure=munit:test with execution and validation");
    response.addArtifact("recommendedValidation=munit-tools:assert-that, verify-call, spy, and mock-when");
    response.addArtifact("targetFlow=" + emptyToDefault(flowName, "all flows"));
    response.addArtifact("munitSuites=" + suites.stream().map(suite -> suite.relativePath).toList());
    response.addArtifact("flows=" + flowComponents.keySet());
    response.addDiagnostics(diagnostics);
    response.addNextAction("Add MUnit assertions for payload, variables, attributes, status, and error outcomes.");
    response.addNextAction("Mock external connectors and verify critical connector or flow-ref calls.");
    response.addNextAction("Run run_mule_maven_tests with goals [\"test\"] after updating MUnit suites.");
    return response;
  }

  static MuleToolResponse munitFullReviewResponse(Path projectPath, String flowName, Path munitPath) throws Exception {
    MuleProjectAnalysis analysis = scan(projectPath);
    List<MuleDiagnostic> diagnostics = new ArrayList<>(analysis.diagnostics);
    List<Path> munitFiles = resolveMunitFiles(projectPath, analysis, munitPath, diagnostics);
    Map<String, List<MuleFlowComponent>> flowComponents = readFlowComponents(projectPath, diagnostics);
    List<MunitSuiteSummary> suites = new ArrayList<>();

    for (Path file : munitFiles) {
      suites.add(analyzeMunitSuite(projectPath, file, diagnostics));
    }

    String targetFlow = emptyToDefault(flowName, "");
    validateMunitPurposeAndCoverage(projectPath, targetFlow, flowComponents, suites, diagnostics);
    addMunitReviewDiagnostics(projectPath, targetFlow, flowComponents, suites, diagnostics);

    MuleToolResponse response = new MuleToolResponse(diagnostics.isEmpty() ? "success" : "partial",
        "Completed full MUnit review for " + projectPath);
    addMunitReviewArtifacts(response, flowComponents, suites, targetFlow);
    response.addDiagnostics(diagnostics);
    response.addNextAction("Prioritize high findings that make tests non-executable or logically meaningless.");
    response.addNextAction("Add scenario-level assertions before expanding low-value processor-only checks.");
    response.addNextAction("Run run_mule_maven_tests with goals [\"test\"] and review failing test intent.");
    return response;
  }

  static MuleToolResponse munitImprovementSuggestionsResponse(Path projectPath, String flowName, Path munitPath)
      throws Exception {
    MuleProjectAnalysis analysis = scan(projectPath);
    List<MuleDiagnostic> diagnostics = new ArrayList<>(analysis.diagnostics);
    List<Path> munitFiles = resolveMunitFiles(projectPath, analysis, munitPath, diagnostics);
    Map<String, List<MuleFlowComponent>> flowComponents = readFlowComponents(projectPath, diagnostics);
    List<MunitSuiteSummary> suites = new ArrayList<>();

    for (Path file : munitFiles) {
      suites.add(analyzeMunitSuite(projectPath, file, diagnostics));
    }

    String targetFlow = emptyToDefault(flowName, "");
    addMunitReviewDiagnostics(projectPath, targetFlow, flowComponents, suites, diagnostics);
    addMunitCadenceDiagnostics(targetFlow, flowComponents, suites, diagnostics);

    MuleToolResponse response = new MuleToolResponse(diagnostics.isEmpty() ? "success" : "partial",
        "Suggested MUnit improvements for " + projectPath);
    addMunitReviewArtifacts(response, flowComponents, suites, targetFlow);
    response.addArtifact("recommendedCadence=positive, negative, edge, connector-failure, and error-contract tests");
    response.addArtifact("recommendedAssertions=payload, attributes, variables, status, outbound calls, and errors");
    response.addDiagnostics(diagnostics);
    response.addNextAction("Create one happy-path test for every public flow before adding edge cases.");
    response.addNextAction("For each branch, add one test per route and assert the business outcome.");
    response.addNextAction("For each external connector, mock success and failure and verify the call shape.");
    response.addNextAction("Keep generated tests small: arrange mocks, execute one flow, then assert outcomes.");
    return response;
  }

  static String renderSummary(MuleProjectAnalysis analysis) {
    StringBuilder builder = new StringBuilder();
    builder.append("Mule project: ").append(analysis.projectPath).append(System.lineSeparator());
    builder.append("Runtime version: ").append(blankToUnknown(analysis.muleRuntimeVersion))
        .append(System.lineSeparator());
    appendList(builder, "Mule XML files", analysis.muleXmlFiles);
    appendList(builder, "API specs", analysis.apiSpecFiles);
    appendList(builder, "MUnit suites", analysis.munitFiles);
    appendList(builder, "Flows", analysis.flows);
    appendList(builder, "Sub-flows", analysis.subFlows);
    appendList(builder, "Global configs", analysis.globalConfigs);
    appendList(builder, "Connector dependencies", analysis.connectorDependencies);
    appendList(builder, "Deployment plugins", analysis.deploymentPlugins);
    appendList(builder, "Property placeholders", analysis.placeholders);
    builder.append("Top processors/connectors:").append(System.lineSeparator());
    analysis.processorCounts.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(25)
        .forEach(entry -> builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
            .append(System.lineSeparator()));
    builder.append("hasApikit: ").append(analysis.hasApikit).append(System.lineSeparator());
    builder.append("hasSecureProperties: ").append(analysis.hasSecureProperties).append(System.lineSeparator());
    builder.append("hasBatchJob: ").append(analysis.hasBatchJob).append(System.lineSeparator());
    builder.append("hasReconnectForever: ").append(analysis.hasReconnectForever).append(System.lineSeparator());
    builder.append("log4j2RootLevel: ").append(blankToUnknown(analysis.log4j2RootLevel)).append(System.lineSeparator());
    builder.append("hasDbPoolConfig: ").append(analysis.hasDbPoolConfig).append(System.lineSeparator());
    builder.append("hasHttpRequestTimeout: ").append(analysis.hasHttpRequestTimeout).append(System.lineSeparator());
    if (!analysis.schedulerFlows.isEmpty()) {
      appendList(builder, "Scheduler-triggered flows", analysis.schedulerFlows);
    }
    if (!analysis.flowsWithCorrelationId.isEmpty()) {
      appendList(builder, "Flows with correlationId set", analysis.flowsWithCorrelationId);
    }
    builder.append("Diagnostics: ").append(analysis.diagnostics.size())
        .append(" finding(s) — run mule_project_scan for full details.").append(System.lineSeparator());
    return builder.toString();
  }

  private static void parsePom(Path pomPath, MuleProjectAnalysis analysis) throws Exception {
    Document document = parseXml(pomPath);
    NodeList artifactIds = document.getElementsByTagNameNS("*", "artifactId");
    for (int i = 0; i < artifactIds.getLength(); i++) {
      String artifactId = artifactIds.item(i).getTextContent().trim();
      if (artifactId.contains("mule") || artifactId.contains("connector")) {
        analysis.connectorDependencies.add(artifactId);
      }
    }
    NodeList plugins = document.getElementsByTagNameNS("*", "plugin");
    for (int i = 0; i < plugins.getLength(); i++) {
      Element plugin = (Element) plugins.item(i);
      String artifactId = firstChildText(plugin, "artifactId");
      if (artifactId.contains("mule") || artifactId.contains("cloudhub") || artifactId.contains("rtf")) {
        analysis.deploymentPlugins.add(artifactId);
      }
    }
    String text = Files.readString(pomPath, StandardCharsets.UTF_8);
    Matcher runtimeMatcher =
        Pattern.compile("<mule\\.runtime\\.version>([^<]+)</mule\\.runtime\\.version>").matcher(text);
    if (runtimeMatcher.find()) {
      analysis.muleRuntimeVersion = runtimeMatcher.group(1).trim();
    }
  }

  private static void parseMuleArtifact(Path artifactPath, MuleProjectAnalysis analysis) throws Exception {
    String text = Files.readString(artifactPath, StandardCharsets.UTF_8);
    Matcher matcher = Pattern.compile("\"minMuleVersion\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
    if (matcher.find()) {
      analysis.muleRuntimeVersion = matcher.group(1).trim();
    }
  }

  private static void parseMuleXml(Path projectPath, Path xmlFile, MuleProjectAnalysis analysis) {
    String relative = relativize(projectPath, xmlFile);
    analysis.muleXmlFiles.add(relative);
    try {
      Document document = parseXml(xmlFile);
      Element root = document.getDocumentElement();
      collectNamespaces(root, analysis);
      collectPlaceholders(root.getTextContent(), analysis.placeholders);
      collectElements(root, analysis);
      analyzeFlowDetails(document, analysis);
    } catch (Exception e) {
      analysis.diagnostics.add(MuleDiagnostic.high(relative, 0, "Failed to parse Mule XML: " + e.getMessage(),
          "Fix XML syntax before asking Copilot to edit or review this file."));
    }
  }

  private static void collectNamespaces(Element root, MuleProjectAnalysis analysis) {
    for (int i = 0; i < root.getAttributes().getLength(); i++) {
      Node attribute = root.getAttributes().item(i);
      String name = attribute.getNodeName();
      String value = attribute.getNodeValue();
      if ("xmlns".equals(name) || name.startsWith("xmlns:")) {
        analysis.namespaces.add(name + "=" + value);
        if (value.contains("/mule/apikit")) {
          analysis.hasApikit = true;
        }
        if (value.contains("/mule/secure-properties")) {
          analysis.hasSecureProperties = true;
        }
      } else {
        collectPlaceholders(value, analysis.placeholders);
      }
    }
  }

  private static void collectElements(Element root, MuleProjectAnalysis analysis) {
    NodeList nodes = root.getElementsByTagName("*");
    for (int i = 0; i < nodes.getLength(); i++) {
      if (!(nodes.item(i) instanceof Element element)) {
        continue;
      }
      String localName = localName(element);
      String qualifiedName = qualifiedName(element);
      analysis.processorCounts.merge(qualifiedName, 1, Integer::sum);
      if ("flow".equals(localName)) {
        addNamedElement(element, analysis.flows);
      } else if ("sub-flow".equals(localName)) {
        addNamedElement(element, analysis.subFlows);
      } else if (element.getParentNode() == root) {
        analysis.globalConfigs.add(qualifiedName + optionalName(element));
      }
      if ("reconnect-forever".equals(localName)) {
        analysis.hasReconnectForever = true;
      }
      if ("job".equals(localName) && qualifiedName.startsWith("batch:")) {
        analysis.hasBatchJob = true;
      }
      if ("until-successful".equals(localName) && !element.hasAttribute("maxRetries")) {
        String docName = element.getAttribute("doc:name");
        analysis.untilSuccessfulWithoutMaxRetries.add(docName.isBlank() ? "unnamed" : docName);
      }
      if (element.hasAttribute("minPoolSize")) {
        analysis.hasDbPoolConfig = true;
      }
      if (element.hasAttribute("responseTimeout")) {
        analysis.hasHttpRequestTimeout = true;
      }
      collectPlaceholders(element.getTextContent(), analysis.placeholders);
      collectAttributePlaceholders(element, analysis.placeholders);
    }
  }

  private static void collectAttributePlaceholders(Element element, Set<String> placeholders) {
    for (int i = 0; i < element.getAttributes().getLength(); i++) {
      collectPlaceholders(element.getAttributes().item(i).getNodeValue(), placeholders);
    }
  }

  private static void addProjectLevelDiagnostics(MuleProjectAnalysis analysis) {
    if (analysis.muleXmlFiles.isEmpty()) {
      analysis.diagnostics.add(MuleDiagnostic.high("src/main/mule", 0, "No Mule XML files were found.",
          "Select a Mule application root with src/main/mule/*.xml files."));
    }
    if (analysis.apiSpecFiles.isEmpty()) {
      analysis.diagnostics.add(MuleDiagnostic.low("src/main/resources", 0, "No API specification files were found.",
          "Add RAML/OpenAPI/AsyncAPI/WSDL/XSD contracts when this Mule app exposes or consumes APIs."));
    }
    if (analysis.hasReconnectForever) {
      analysis.diagnostics.add(MuleDiagnostic.medium("src/main/mule", 0,
          "reconnect-forever detected in connector configuration.",
          "Replace reconnect-forever with reconnect (finite retries and frequency) to prevent indefinite thread blocking in production."));
    }
    if (!analysis.untilSuccessfulWithoutMaxRetries.isEmpty()) {
      analysis.diagnostics.add(MuleDiagnostic.medium("src/main/mule", 0,
          "until-successful usage without maxRetries: " + String.join(", ", analysis.untilSuccessfulWithoutMaxRetries),
          "Set maxRetries and millisBetweenRetries on all until-successful scopes to prevent runaway retry loops."));
    }
    if (!analysis.log4j2RootLevel.isBlank()
        && (analysis.log4j2RootLevel.equalsIgnoreCase("debug")
            || analysis.log4j2RootLevel.equalsIgnoreCase("trace"))) {
      analysis.diagnostics.add(MuleDiagnostic.medium("src/main/resources/log4j2.xml", 0,
          "Root log level is " + analysis.log4j2RootLevel.toUpperCase() + " which is not suitable for production.",
          "Set the root logger level to INFO or WARN before deploying to production environments."));
    }
    boolean hasDbConnector = analysis.connectorDependencies.stream()
        .anyMatch(d -> d.toLowerCase(Locale.ROOT).contains("db") || d.toLowerCase(Locale.ROOT).contains("database"));
    if (hasDbConnector && !analysis.hasDbPoolConfig) {
      analysis.diagnostics.add(MuleDiagnostic.medium("src/main/mule", 0,
          "Database connector dependency found but no connection pool configuration (minPoolSize/maxPoolSize) was detected.",
          "Add connection pool settings to db:config to prevent connection exhaustion under load."));
    }
    boolean hasHttpConnector = analysis.connectorDependencies.stream()
        .anyMatch(d -> d.toLowerCase(Locale.ROOT).contains("http"));
    if (hasHttpConnector && !analysis.hasHttpRequestTimeout) {
      analysis.diagnostics.add(MuleDiagnostic.medium("src/main/mule", 0,
          "HTTP connector found but no responseTimeout was detected in HTTP Request configurations.",
          "Set responseTimeout on http:request-config to prevent threads blocking indefinitely on slow upstreams."));
    }
    detectDuplicateNames("flow", analysis.flows, analysis.diagnostics);
    detectDuplicateNames("sub-flow", analysis.subFlows, analysis.diagnostics);
  }

  private static void addFlowStructureFindings(Path projectPath, MuleProjectAnalysis analysis,
      List<MuleDiagnostic> diagnostics) throws Exception {
    for (String relative : analysis.muleXmlFiles) {
      Path file = projectPath.resolve(relative);
      String text = Files.readString(file, StandardCharsets.UTF_8);
      if (!text.contains("<error-handler") && !text.contains(":error-handler")) {
        diagnostics.add(MuleDiagnostic.medium(relative, 0, "No error-handler was detected in this Mule XML file.",
            "Add flow-level or global error handling for connector failures, validation errors, and downstream "
                + "4xx/5xx responses."));
      }
      if (text.contains("<logger") && text.toLowerCase(Locale.ROOT).contains("payload")) {
        diagnostics.add(MuleDiagnostic.medium(relative, 0, "Logger usage appears to include payload data.",
            "Avoid logging full payloads in production; log correlation IDs and safe business identifiers instead."));
      }
    }
  }

  private static void addDataWeaveFindings(Path projectPath, List<String> files, List<MuleDiagnostic> diagnostics)
      throws Exception {
    List<Path> dwFiles = listFiles(projectPath.resolve("src/main/resources"), ".dwl");
    if (files != null && !files.isEmpty()) {
      Set<Path> selected = files.stream().map(projectPath::resolve).map(Path::normalize).collect(Collectors.toSet());
      dwFiles = dwFiles.stream().filter(file -> selected.contains(file.normalize())).toList();
    }
    for (Path file : dwFiles) {
      String text = Files.readString(file, StandardCharsets.UTF_8);
      String relative = relativize(projectPath, file);
      if (!text.contains("%dw 2.0")) {
        diagnostics.add(MuleDiagnostic.high(relative, 1, "DataWeave script does not declare %dw 2.0.",
            "Add the DataWeave version header expected by Mule 4."));
      }
      if (!text.contains("output ")) {
        diagnostics.add(MuleDiagnostic.medium(relative, 0, "DataWeave script does not declare an output MIME type.",
            "Declare the output format explicitly, for example output application/json."));
      }
    }
  }

  private static void addSecretFindings(Path projectPath, List<MuleDiagnostic> diagnostics) throws Exception {
    List<Path> files = new ArrayList<>();
    files.addAll(listFiles(projectPath.resolve("src/main/resources"), null));
    files.addAll(listFiles(projectPath.resolve("src/main/mule"), ".xml"));
    for (Path file : files) {
      String relative = relativize(projectPath, file);
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        Matcher matcher = SECRET_LINE_PATTERN.matcher(line);
        if (matcher.find() && isLiteralSecret(matcher.group(2))) {
          diagnostics.add(MuleDiagnostic.critical(relative, i + 1, "Possible hardcoded secret in configuration.",
              "Move this value to secure properties or an external secret manager."));
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("password=\"") || lower.contains("clientsecret=\"") || lower.contains("client-secret=\"")
            || lower.contains("secret=\"") || lower.contains("token=\"")) {
          if (!line.contains("${") && !line.contains("$[")) {
            diagnostics.add(MuleDiagnostic.critical(relative, i + 1, "Possible hardcoded secret in Mule XML attribute.",
                "Use property placeholders backed by secure properties."));
          }
        }
      }
    }
  }

  private static void addXmlSecurityFindings(Path projectPath, MuleProjectAnalysis analysis,
      List<MuleDiagnostic> diagnostics) throws Exception {
    for (String relative : analysis.muleXmlFiles) {
      Path file = projectPath.resolve(relative);
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i).toLowerCase(Locale.ROOT);
        if ((line.contains("<http:listener-config") || line.contains("<http:request-config"))
            && line.contains("protocol=\"http\"")) {
          diagnostics.add(MuleDiagnostic.high(relative, i + 1, "HTTP protocol is configured without TLS.",
              "Use HTTPS/TLS or document why this endpoint is local-only."));
        }
      }
    }
  }

  private static void analyzeRaml(Path schemaPath, String content, List<MuleDiagnostic> diagnostics,
      List<String> artifacts) {
    if (!content.startsWith("#%RAML 1.0")) {
      diagnostics.add(MuleDiagnostic.high(schemaPath.toString(), 1, "RAML file does not start with #%RAML 1.0.",
          "Use RAML 1.0 for APIkit-compatible Mule contracts."));
    }
    addPresenceDiagnostic(schemaPath, content, "title:", "RAML contract is missing title.", diagnostics,
        "Add a human-readable API title.");
    addPresenceDiagnostic(schemaPath, content, "version:", "RAML contract is missing version.", diagnostics,
        "Add a version so generated Mule artifacts and Exchange assets are traceable.");
    if (!content.contains("securitySchemes:") && !content.contains("securedBy:")) {
      diagnostics.add(MuleDiagnostic.medium(schemaPath.toString(), 0, "RAML contract does not declare security.",
          "Add securitySchemes and securedBy for the expected auth profile."));
    }
    if (!RAML_RESPONSE_PATTERN.matcher(content).find()) {
      diagnostics.add(MuleDiagnostic.high(schemaPath.toString(), 0, "RAML contract does not define HTTP responses.",
          "Define success and error responses for every method."));
    }
    if (!content.contains("example:") && !content.contains("examples:")) {
      diagnostics.add(MuleDiagnostic.medium(schemaPath.toString(), 0, "RAML contract has no examples.",
          "Add request and response examples for Copilot, APIkit, and review workflows."));
    }
    artifacts.add("resources=" + countMatches(content, Pattern.compile("^\\s*/[^:]+:", Pattern.MULTILINE)));
  }

  private static void analyzeOpenApi(Path schemaPath, String content, List<MuleDiagnostic> diagnostics,
      List<String> artifacts) {
    if (!content.contains("openapi: 3.") && !content.contains("\"openapi\"")) {
      diagnostics.add(MuleDiagnostic.high(schemaPath.toString(), 0, "OpenAPI 3.x marker was not found.",
          "Use OpenAPI 3.0.x or 3.1.x for Mule API contracts."));
    }
    if (!content.contains("operationId")) {
      diagnostics.add(MuleDiagnostic.medium(schemaPath.toString(), 0, "OpenAPI operations are missing operationId.",
          "Add stable operationId values for routing, generated code, and review traceability."));
    }
    if (!content.contains("components:") && !content.contains("\"components\"")) {
      diagnostics.add(MuleDiagnostic.medium(schemaPath.toString(), 0, "OpenAPI contract does not use components.",
          "Move schemas, responses, parameters, and security schemes into reusable components."));
    }
    if (!content.contains("security") && !content.contains("securitySchemes")) {
      diagnostics.add(MuleDiagnostic.medium(schemaPath.toString(), 0, "OpenAPI contract does not declare security.",
          "Add OAuth2, JWT, API key, mTLS, or client credential definitions as appropriate."));
    }
    artifacts.add("paths=" + countMatches(content, Pattern.compile("^\\s*/[^:]+:", Pattern.MULTILINE)));
  }

  private static void analyzeXmlContract(Path schemaPath, String content, List<MuleDiagnostic> diagnostics,
      List<String> artifacts) {
    if (!content.trim().startsWith("<")) {
      diagnostics.add(MuleDiagnostic.high(schemaPath.toString(), 1, "XML contract does not start with an XML element.",
          "Fix WSDL/XSD syntax before Mule import or validation."));
    }
    artifacts.add("xmlElements=" + countMatches(content, Pattern.compile("<[A-Za-z_:][A-Za-z0-9_.:-]*")));
  }

  private static void analyzeJsonContract(Path schemaPath, String content, List<MuleDiagnostic> diagnostics,
      List<String> artifacts) {
    String trimmed = content.trim();
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
      diagnostics.add(MuleDiagnostic.high(schemaPath.toString(), 1, "JSON-like contract is not valid JSON-shaped text.",
          "Fix syntax or choose the correct schemaType."));
    }
    artifacts.add("bytes=" + content.getBytes(StandardCharsets.UTF_8).length);
  }

  private static List<Path> resolveMunitFiles(Path projectPath, MuleProjectAnalysis analysis, Path munitPath,
      List<MuleDiagnostic> diagnostics) {
    if (munitPath != null) {
      if (Files.isRegularFile(munitPath)) {
        return List.of(munitPath);
      }
      diagnostics.add(MuleDiagnostic.high(munitPath.toString(), 0, "MUnit suite path does not exist.",
          "Provide an existing MUnit XML suite file or omit munitPath to validate all suites."));
      return List.of();
    }
    if (analysis.munitFiles.isEmpty()) {
      diagnostics.add(MuleDiagnostic.high("src/test/munit", 0, "No MUnit suites were found.",
          "Create MUnit suites under src/test/munit for the Mule flows."));
      return List.of();
    }
    return analysis.munitFiles.stream().map(projectPath::resolve).toList();
  }

  private static Map<String, List<MuleFlowComponent>> readFlowComponents(Path projectPath,
      List<MuleDiagnostic> diagnostics) {
    Map<String, List<MuleFlowComponent>> componentsByFlow = new LinkedHashMap<>();
    for (Path file : listFiles(projectPath.resolve("src/main/mule"), ".xml")) {
      String relative = relativize(projectPath, file);
      try {
        Document document = parseXml(file);
        NodeList flows = document.getElementsByTagNameNS("*", "flow");
        for (int i = 0; i < flows.getLength(); i++) {
          Element flow = (Element) flows.item(i);
          String flowName = flow.getAttribute("name");
          if (!flowName.isBlank()) {
            componentsByFlow.put(flowName, collectFlowComponents(relative, flow));
          }
        }
      } catch (Exception e) {
        diagnostics.add(MuleDiagnostic.high(relative, 0, "Failed to parse Mule XML for MUnit validation.",
            "Fix Mule XML syntax before validating MUnit coverage."));
      }
    }
    return componentsByFlow;
  }

  private static List<MuleFlowComponent> collectFlowComponents(String relativeFile, Element flow) {
    List<MuleFlowComponent> components = new ArrayList<>();
    NodeList nodes = flow.getElementsByTagName("*");
    for (int i = 0; i < nodes.getLength(); i++) {
      if (!(nodes.item(i) instanceof Element element) || isStructuralFlowElement(element)) {
        continue;
      }
      String qualifiedName = qualifiedName(element);
      components.add(new MuleFlowComponent(relativeFile, flow.getAttribute("name"), qualifiedName,
          componentDisplayName(element), isExternalProcessor(qualifiedName), isBranchProcessor(element)));
    }
    return components;
  }

  private static MunitSuiteSummary analyzeMunitSuite(Path projectPath, Path suitePath,
      List<MuleDiagnostic> diagnostics) {
    String relative = relativize(projectPath, suitePath);
    MunitSuiteSummary suite = new MunitSuiteSummary(relative);
    try {
      Document document = parseXml(suitePath);
      Element root = document.getDocumentElement();
      suite.hasMunitNamespace = hasNamespace(root, "munit", "http://www.mulesoft.org/schema/mule/munit");
      suite.hasMunitToolsNamespace =
          hasNamespace(root, "munit-tools", "http://www.mulesoft.org/schema/mule/munit-tools");
      suite.hasMunitSchema = root.getAttribute("xsi:schemaLocation").contains("mule-munit.xsd");
      suite.hasMunitToolsSchema = root.getAttribute("xsi:schemaLocation").contains("mule-munit-tools.xsd");
      suite.hasConfig = document.getElementsByTagNameNS("*", "config").getLength() > 0;
      suite.tests.addAll(collectMunitTests(document));
      addMunitStructureDiagnostics(suite, diagnostics);
    } catch (Exception e) {
      diagnostics.add(MuleDiagnostic.high(relative, 0, "Failed to parse MUnit XML: " + e.getMessage(),
          "Fix MUnit XML syntax before validating test purpose or coverage."));
    }
    return suite;
  }

  private static List<MunitTestSummary> collectMunitTests(Document document) {
    List<MunitTestSummary> tests = new ArrayList<>();
    NodeList testNodes = document.getElementsByTagNameNS("*", "test");
    for (int i = 0; i < testNodes.getLength(); i++) {
      Element testElement = (Element) testNodes.item(i);
      MunitTestSummary test = new MunitTestSummary(testElement.getAttribute("name"),
          testElement.getAttribute("description"));
      NodeList descendants = testElement.getElementsByTagName("*");
      for (int j = 0; j < descendants.getLength(); j++) {
        if (!(descendants.item(j) instanceof Element element)) {
          continue;
        }
        collectMunitTestElement(test, element);
      }
      tests.add(test);
    }
    return tests;
  }

  private static void collectMunitTestElement(MunitTestSummary test, Element element) {
    String qualifiedName = qualifiedName(element);
    test.processors.add(qualifiedName);
    String localName = localName(element);
    if ("execution".equals(localName)) {
      test.hasExecution = true;
    } else if ("validation".equals(localName)) {
      test.hasValidation = true;
    } else if ("assert-that".equals(localName) || "assert-equals".equals(localName)
        || "assert-true".equals(localName) || "assert-false".equals(localName)) {
      test.assertions++;
    } else if ("mock-when".equals(localName)) {
      addProcessorAttribute(test.mockedProcessors, element);
    } else if ("spy".equals(localName)) {
      addProcessorAttribute(test.spiedProcessors, element);
    } else if ("verify-call".equals(localName)) {
      addProcessorAttribute(test.verifiedProcessors, element);
    } else if ("flow-ref".equals(localName)) {
      String flowName = element.getAttribute("name");
      if (!flowName.isBlank()) {
        test.flowRefs.add(flowName);
      }
    }
    if (element.hasAttribute("expression")) {
      test.assertedExpressions.add(element.getAttribute("expression"));
    }
  }

  private static void addMunitStructureDiagnostics(MunitSuiteSummary suite, List<MuleDiagnostic> diagnostics) {
    if (!suite.hasMunitNamespace) {
      diagnostics.add(MuleDiagnostic.high(suite.relativePath, 0, "MUnit suite is missing the munit namespace.",
          "Declare xmlns:munit=\"http://www.mulesoft.org/schema/mule/munit\"."));
    }
    if (!suite.hasMunitToolsNamespace) {
      diagnostics.add(MuleDiagnostic.high(suite.relativePath, 0, "MUnit suite is missing the munit-tools namespace.",
          "Declare xmlns:munit-tools=\"http://www.mulesoft.org/schema/mule/munit-tools\"."));
    }
    if (!suite.hasMunitSchema || !suite.hasMunitToolsSchema) {
      diagnostics.add(MuleDiagnostic.medium(suite.relativePath, 0,
          "MUnit suite schemaLocation is missing MUnit schema entries.",
          "Include mule-munit.xsd and mule-munit-tools.xsd schema locations."));
    }
    if (!suite.hasConfig) {
      diagnostics.add(MuleDiagnostic.high(suite.relativePath, 0, "MUnit suite is missing munit:config.",
          "Add munit:config to identify the file as an MUnit suite."));
    }
    if (suite.tests.isEmpty()) {
      diagnostics.add(MuleDiagnostic.high(suite.relativePath, 0, "MUnit suite contains no munit:test elements.",
          "Add MUnit tests with execution and validation scopes."));
    }
  }

  private static void validateMunitPurposeAndCoverage(Path projectPath, String targetFlow,
      Map<String, List<MuleFlowComponent>> flowComponents, List<MunitSuiteSummary> suites,
      List<MuleDiagnostic> diagnostics) {
    Map<String, List<MunitTestSummary>> testsByFlow = mapTestsToFlows(targetFlow, flowComponents.keySet(), suites);
    for (MunitSuiteSummary suite : suites) {
      validateIndividualMunitTests(suite, diagnostics);
    }
    List<String> flowNames = targetFlow.isBlank() ? new ArrayList<>(flowComponents.keySet()) : List.of(targetFlow);
    for (String flowName : flowNames) {
      List<MunitTestSummary> tests = testsByFlow.getOrDefault(flowName, List.of());
      List<MuleFlowComponent> components = flowComponents.getOrDefault(flowName, List.of());
      if (components.isEmpty()) {
        diagnostics.add(MuleDiagnostic.high("src/main/mule", 0, "Target flow was not found: " + flowName,
            "Pass an existing Mule flow name or validate all flows."));
        continue;
      }
      validateFlowTestCoverage(projectPath, flowName, components, tests, diagnostics);
    }
  }

  private static void validateIndividualMunitTests(MunitSuiteSummary suite, List<MuleDiagnostic> diagnostics) {
    for (MunitTestSummary test : suite.tests) {
      String testLabel = suite.relativePath + ":" + emptyToDefault(test.name, "unnamed-test");
      if (!test.hasExecution) {
        diagnostics.add(MuleDiagnostic.high(suite.relativePath, 0, "MUnit test has no execution scope: " + test.name,
            "Add munit:execution and call the target flow, usually with flow-ref."));
      }
      if (!test.hasValidation) {
        diagnostics.add(MuleDiagnostic.high(suite.relativePath, 0, "MUnit test has no validation scope: " + test.name,
            "Add munit:validation with assertions or verify-call processors."));
      }
      if (!test.hasMeaningfulValidation()) {
        diagnostics.add(MuleDiagnostic.high(suite.relativePath, 0,
            "MUnit test appears to have no logical validation purpose: " + testLabel,
            "Assert payload, variables, attributes, errors, or verify expected processor calls."));
      }
      if (test.description.isBlank()) {
        diagnostics.add(MuleDiagnostic.low(suite.relativePath, 0, "MUnit test has no description: " + test.name,
            "Describe the business scenario or edge case covered by the test."));
      }
    }
  }

  private static void validateFlowTestCoverage(Path projectPath, String flowName, List<MuleFlowComponent> components,
      List<MunitTestSummary> tests, List<MuleDiagnostic> diagnostics) {
    if (tests.isEmpty()) {
      diagnostics.add(MuleDiagnostic.high("src/test/munit", 0, "No MUnit tests target flow: " + flowName,
          "Add tests that execute the flow and validate its outputs and side effects."));
      return;
    }
    Set<String> coveredProcessors = tests.stream().flatMap(test -> test.coveredProcessors().stream())
        .collect(Collectors.toCollection(LinkedHashSet::new));
    List<String> uncovered = components.stream()
        .filter(component -> !isComponentCovered(component.qualifiedName, coveredProcessors))
        .map(MuleFlowComponent::displayLabel).distinct().toList();
    if (!uncovered.isEmpty()) {
      diagnostics.add(MuleDiagnostic.medium(components.get(0).relativeFile, 0,
          "Not all flow components are explicitly mocked, spied, verified, or asserted for flow: " + flowName,
          "Add coverage for: " + String.join(", ", uncovered)));
    }
    validateExternalConnectorCoverage(flowName, components, tests, diagnostics);
    validateBranchAndErrorCoverage(projectPath, flowName, components, tests, diagnostics);
  }

  private static void validateExternalConnectorCoverage(String flowName, List<MuleFlowComponent> components,
      List<MunitTestSummary> tests, List<MuleDiagnostic> diagnostics) {
    Set<String> mockedProcessors = tests.stream().flatMap(test -> test.mockedProcessors.stream())
        .collect(Collectors.toCollection(LinkedHashSet::new));
    List<String> unmocked = components.stream()
        .filter(component -> component.external && !isComponentCovered(component.qualifiedName, mockedProcessors))
        .map(MuleFlowComponent::displayLabel).distinct().toList();
    if (!unmocked.isEmpty()) {
      diagnostics.add(MuleDiagnostic.high(components.get(0).relativeFile, 0,
          "External connector calls are not mocked for flow: " + flowName,
          "Use munit-tools:mock-when for: " + String.join(", ", unmocked)));
    }
  }

  private static void validateBranchAndErrorCoverage(Path projectPath, String flowName,
      List<MuleFlowComponent> components, List<MunitTestSummary> tests, List<MuleDiagnostic> diagnostics) {
    boolean hasBranch = components.stream().anyMatch(component -> component.branch);
    if (hasBranch && tests.size() < 2) {
      diagnostics.add(MuleDiagnostic.medium(components.get(0).relativeFile, 0,
          "Flow contains branching but has fewer than two MUnit tests: " + flowName,
          "Add separate tests for true/false, route, or choice outcomes."));
    }
    if (flowHasErrorHandler(projectPath, components.get(0).relativeFile, flowName)
        && tests.stream().noneMatch(MunitTestSummary::looksLikeErrorTest)) {
      diagnostics.add(MuleDiagnostic.medium(components.get(0).relativeFile, 0,
          "Flow has error handling but no obvious error-path MUnit test: " + flowName,
          "Add a negative test that mocks a failure and validates the error contract."));
    }
  }

  private static void addMunitReviewDiagnostics(Path projectPath, String targetFlow,
      Map<String, List<MuleFlowComponent>> flowComponents, List<MunitSuiteSummary> suites,
      List<MuleDiagnostic> diagnostics) {
    Map<String, List<MunitTestSummary>> testsByFlow = mapTestsToFlows(targetFlow, flowComponents.keySet(), suites);
    List<String> flowNames = targetFlow.isBlank() ? new ArrayList<>(flowComponents.keySet()) : List.of(targetFlow);
    for (String flowName : flowNames) {
      List<MunitTestSummary> tests = testsByFlow.getOrDefault(flowName, List.of());
      List<MuleFlowComponent> components = flowComponents.getOrDefault(flowName, List.of());
      if (components.isEmpty()) {
        continue;
      }
      addMunitScenarioDiagnostics(projectPath, flowName, components, tests, diagnostics);
      addMunitAssertionQualityDiagnostics(flowName, components, tests, diagnostics);
    }
    for (MunitSuiteSummary suite : suites) {
      for (MunitTestSummary test : suite.tests) {
        if (isGenericTestName(test.name)) {
          diagnostics.add(MuleDiagnostic.low(suite.relativePath, 0,
              "MUnit test name does not communicate the scenario: " + emptyToDefault(test.name, "unnamed-test"),
              "Name tests after behavior, such as get-account-success or get-account-connector-timeout."));
        }
      }
    }
  }

  private static void addMunitScenarioDiagnostics(Path projectPath, String flowName,
      List<MuleFlowComponent> components, List<MunitTestSummary> tests, List<MuleDiagnostic> diagnostics) {
    if (tests.isEmpty()) {
      return;
    }
    boolean hasBranch = components.stream().anyMatch(component -> component.branch);
    boolean hasExternal = components.stream().anyMatch(component -> component.external);
    boolean hasErrorHandler = flowHasErrorHandler(projectPath, components.get(0).relativeFile, flowName);
    if (tests.size() == 1 && (hasBranch || hasExternal || hasErrorHandler)) {
      diagnostics.add(MuleDiagnostic.medium(components.get(0).relativeFile, 0,
          "MUnit coverage is too shallow for the flow complexity: " + flowName,
          "Add separate tests for happy path, edge data, external failure, and error mapping."));
    }
    if (hasExternal && tests.stream().noneMatch(MunitTestSummary::looksLikeFailureTest)) {
      diagnostics.add(MuleDiagnostic.medium(components.get(0).relativeFile, 0,
          "Flow calls external systems but has no obvious connector-failure MUnit scenario: " + flowName,
          "Mock connector failures and assert the fallback, retry, or error response behavior."));
    }
  }

  private static void addMunitAssertionQualityDiagnostics(String flowName, List<MuleFlowComponent> components,
      List<MunitTestSummary> tests, List<MuleDiagnostic> diagnostics) {
    if (tests.isEmpty()) {
      return;
    }
    boolean assertsPayload = tests.stream().anyMatch(test -> test.assertsExpression("payload"));
    boolean assertsVariables = tests.stream().anyMatch(test -> test.assertsExpression("vars."));
    boolean assertsAttributes = tests.stream().anyMatch(test -> test.assertsExpression("attributes"));
    if (!assertsPayload) {
      diagnostics.add(MuleDiagnostic.medium(components.get(0).relativeFile, 0,
          "MUnit tests do not obviously assert the response payload for flow: " + flowName,
          "Add assertions that prove the transformed business payload is correct."));
    }
    if (!assertsAttributes && tests.stream().anyMatch(MunitTestSummary::hasHttpAssertionOpportunity)) {
      diagnostics.add(MuleDiagnostic.low(components.get(0).relativeFile, 0,
          "MUnit tests do not obviously assert HTTP attributes for flow: " + flowName,
          "Assert status codes, headers, or outbound request attributes where they define the contract."));
    }
    boolean hasSetVariable = components.stream()
        .anyMatch(component -> component.qualifiedName.contains("set-variable"));
    if (!assertsVariables && hasSetVariable) {
      diagnostics.add(MuleDiagnostic.low(components.get(0).relativeFile, 0,
          "Flow sets variables but MUnit tests do not obviously assert them: " + flowName,
          "Assert important vars or verify the downstream call that consumes them."));
    }
  }

  private static void addMunitCadenceDiagnostics(String targetFlow, Map<String, List<MuleFlowComponent>> flowComponents,
      List<MunitSuiteSummary> suites, List<MuleDiagnostic> diagnostics) {
    Map<String, List<MunitTestSummary>> testsByFlow = mapTestsToFlows(targetFlow, flowComponents.keySet(), suites);
    List<String> flowNames = targetFlow.isBlank() ? new ArrayList<>(flowComponents.keySet()) : List.of(targetFlow);
    int testCount = suites.stream().mapToInt(suite -> suite.tests.size()).sum();
    if (testCount == 0) {
      diagnostics.add(MuleDiagnostic.high("src/test/munit", 0, "No MUnit test cadence exists for this project.",
          "Start with one focused happy-path test for each public flow, then add negative and edge scenarios."));
      return;
    }
    List<String> untestedFlows = flowNames.stream()
        .filter(flowName -> testsByFlow.getOrDefault(flowName, List.of()).isEmpty()).toList();
    if (!untestedFlows.isEmpty()) {
      diagnostics.add(MuleDiagnostic.high("src/test/munit", 0,
          "MUnit cadence does not cover every target flow.",
          "Add tests for flows: " + String.join(", ", untestedFlows)));
    }
    long flowsBelowBaseline = flowNames.stream()
        .filter(flowName -> testsByFlow.getOrDefault(flowName, List.of()).size() < 2).count();
    if (flowsBelowBaseline > 0) {
      diagnostics.add(MuleDiagnostic.medium("src/test/munit", 0,
          "Some flows have fewer than two MUnit scenarios.",
          "Use at least happy-path and negative-path tests for changed or externally exposed flows."));
    }
  }

  private static void addMunitReviewArtifacts(MuleToolResponse response,
      Map<String, List<MuleFlowComponent>> flowComponents, List<MunitSuiteSummary> suites, String targetFlow) {
    int testCount = suites.stream().mapToInt(suite -> suite.tests.size()).sum();
    int assertionCount = suites.stream().flatMap(suite -> suite.tests.stream()).mapToInt(test -> test.assertions).sum();
    int mockCount = suites.stream().flatMap(suite -> suite.tests.stream())
        .mapToInt(test -> test.mockedProcessors.size()).sum();
    int verifyCount = suites.stream().flatMap(suite -> suite.tests.stream())
        .mapToInt(test -> test.verifiedProcessors.size()).sum();
    response.addArtifact("targetFlow=" + emptyToDefault(targetFlow, "all flows"));
    response.addArtifact("flowCount=" + flowComponents.size());
    response.addArtifact("munitSuiteCount=" + suites.size());
    response.addArtifact("munitTestCount=" + testCount);
    response.addArtifact("assertionCount=" + assertionCount);
    response.addArtifact("mockWhenCount=" + mockCount);
    response.addArtifact("verifyCallCount=" + verifyCount);
  }

  private static Map<String, List<MunitTestSummary>> mapTestsToFlows(String targetFlow, Set<String> flowNames,
      List<MunitSuiteSummary> suites) {
    Map<String, List<MunitTestSummary>> testsByFlow = new LinkedHashMap<>();
    for (MunitSuiteSummary suite : suites) {
      for (MunitTestSummary test : suite.tests) {
        for (String flowName : flowNames) {
          if (test.targetsFlow(flowName)) {
            testsByFlow.computeIfAbsent(flowName, ignored -> new ArrayList<>()).add(test);
          }
        }
      }
    }
    return testsByFlow;
  }

  private static void addPresenceDiagnostic(Path schemaPath, String content, String marker, String message,
      List<MuleDiagnostic> diagnostics, String recommendation) {
    if (!content.contains(marker)) {
      diagnostics.add(MuleDiagnostic.medium(schemaPath.toString(), 0, message, recommendation));
    }
  }

  private static boolean flowHasErrorHandler(Path projectPath, String relativeFile, String flowName) {
    try {
      Document document = parseXml(projectPath.resolve(relativeFile));
      NodeList flows = document.getElementsByTagNameNS("*", "flow");
      for (int i = 0; i < flows.getLength(); i++) {
        Element flow = (Element) flows.item(i);
        if (flowName.equals(flow.getAttribute("name"))
            && flow.getElementsByTagNameNS("*", "error-handler").getLength() > 0) {
          return true;
        }
      }
    } catch (Exception ignored) {
      return false;
    }
    return false;
  }

  private static boolean hasNamespace(Element root, String prefix, String uri) {
    String value = root.getAttribute("xmlns:" + prefix);
    return uri.equals(value);
  }

  private static void addProcessorAttribute(Set<String> processors, Element element) {
    String processor = element.getAttribute("processor");
    if (!processor.isBlank()) {
      processors.add(processor);
    }
  }

  private static boolean isStructuralFlowElement(Element element) {
    String localName = localName(element);
    String qualifiedName = qualifiedName(element);
    return "flow".equals(localName) || "error-handler".equals(localName) || localName.startsWith("on-error-")
        || "when".equals(localName) || "otherwise".equals(localName) || "ee:message".equals(qualifiedName)
        || "ee:variables".equals(qualifiedName) || "ee:set-payload".equals(qualifiedName);
  }

  private static boolean isExternalProcessor(String qualifiedName) {
    if ("http:listener".equals(qualifiedName)) {
      return false;
    }
    String prefix = qualifiedName.contains(":") ? qualifiedName.substring(0, qualifiedName.indexOf(':')) : "";
    return !prefix.isBlank() && !Set.of("mule", "munit", "munit-tools", "ee", "doc").contains(prefix)
        && !qualifiedName.startsWith("core:");
  }

  private static boolean isBranchProcessor(Element element) {
    String localName = localName(element);
    return "choice".equals(localName) || "foreach".equals(localName) || "until-successful".equals(localName)
        || "scatter-gather".equals(localName) || "parallel-foreach".equals(localName);
  }

  private static boolean isComponentCovered(String qualifiedName, Set<String> processors) {
    if (processors.contains(qualifiedName)) {
      return true;
    }
    return !qualifiedName.contains(":") && processors.contains("mule:" + qualifiedName);
  }

  private static boolean isGenericTestName(String name) {
    String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT).replace("_", "-");
    return normalized.isBlank() || normalized.matches(".*test-[0-9]+.*") || normalized.matches(".*munit-test.*")
        || normalized.equals("test") || normalized.endsWith("-test");
  }

  private static String componentDisplayName(Element element) {
    String docName = element.getAttribute("doc:name");
    if (!docName.isBlank()) {
      return docName;
    }
    String name = element.getAttribute("name");
    return name.isBlank() ? "" : name;
  }

  private static void analyzeFlowDetails(Document document, MuleProjectAnalysis analysis) {
    NodeList flows = document.getElementsByTagNameNS("*", "flow");
    for (int i = 0; i < flows.getLength(); i++) {
      if (!(flows.item(i) instanceof Element flow)) {
        continue;
      }
      String flowName = flow.getAttribute("name");
      if (flowName.isBlank()) {
        continue;
      }
      detectSchedulerSource(flow, flowName, analysis);
      detectCorrelationIdUsage(flow, flowName, analysis);
      detectFlowErrorHandlerType(flow, flowName, analysis);
    }
  }

  private static void detectSchedulerSource(Element flow, String flowName, MuleProjectAnalysis analysis) {
    NodeList children = flow.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (!(children.item(i) instanceof Element firstChild)) {
        continue;
      }
      String lname = localName(firstChild);
      if ("scheduler".equals(lname) || "poll".equals(lname)) {
        analysis.schedulerFlows.add(flowName);
      }
      return; // Only inspect the first element child (the source)
    }
  }

  private static void detectCorrelationIdUsage(Element flow, String flowName, MuleProjectAnalysis analysis) {
    NodeList setVars = flow.getElementsByTagNameNS("*", "set-variable");
    for (int i = 0; i < setVars.getLength(); i++) {
      if (!(setVars.item(i) instanceof Element setVar)) {
        continue;
      }
      String varName = setVar.getAttribute("variableName");
      if ("correlationId".equalsIgnoreCase(varName) || "correlationID".equalsIgnoreCase(varName)) {
        analysis.flowsWithCorrelationId.add(flowName);
        return;
      }
      String value = setVar.getAttribute("value");
      if (value.contains("X-Correlation-ID") || value.contains("correlationId")) {
        analysis.flowsWithCorrelationId.add(flowName);
        return;
      }
    }
  }

  private static void detectFlowErrorHandlerType(Element flow, String flowName, MuleProjectAnalysis analysis) {
    NodeList errorHandlers = flow.getElementsByTagNameNS("*", "error-handler");
    if (errorHandlers.getLength() == 0) {
      analysis.flowErrorHandlerTypes.put(flowName, "none");
      return;
    }
    Element errorHandler = (Element) errorHandlers.item(0);
    boolean allTyped = true;
    int handlerCount = 0;
    NodeList onErrors = errorHandler.getChildNodes();
    for (int i = 0; i < onErrors.getLength(); i++) {
      if (!(onErrors.item(i) instanceof Element onError)) {
        continue;
      }
      String lname = localName(onError);
      if ("on-error-propagate".equals(lname) || "on-error-continue".equals(lname)) {
        handlerCount++;
        if (onError.getAttribute("type").isBlank()) {
          allTyped = false;
        }
      }
    }
    analysis.flowErrorHandlerTypes.put(flowName, handlerCount == 0 ? "none" : (allTyped ? "typed" : "catch-all"));
  }

  private static void parseLog4j2(Path log4j2Path, MuleProjectAnalysis analysis) {
    try {
      Document doc = parseXml(log4j2Path);
      for (String tagName : List.of("Root", "AsyncRoot")) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
          String level = ((Element) nodes.item(0)).getAttribute("level");
          if (!level.isBlank()) {
            analysis.log4j2RootLevel = level;
            return;
          }
        }
      }
    } catch (Exception ignored) {
      // log4j2.xml parse failure is non-critical
    }
  }

  private static Document parseXml(Path file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
    trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
    trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
    trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
    try (InputStream inputStream = Files.newInputStream(file)) {
      return factory.newDocumentBuilder().parse(inputStream);
    }
  }

  private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean enabled) {
    try {
      factory.setFeature(feature, enabled);
    } catch (Exception ignored) {
      // Some XML parsers do not expose every hardening feature.
    }
  }

  private static List<Path> listFiles(Path root, String extension) {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.walk(root)) {
      return stream.filter(Files::isRegularFile)
          .filter(file -> extension == null || file.getFileName().toString().endsWith(extension)).sorted()
          .limit(MAX_FILES).collect(Collectors.toList());
    } catch (Exception e) {
      return List.of();
    }
  }

  private static boolean isApiSpec(Path file) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    return API_SPEC_EXTENSIONS.stream().anyMatch(name::endsWith);
  }

  private static String inferSchemaType(Path schemaPath, String schemaType, String content) {
    if (schemaType != null && !schemaType.isBlank()) {
      return schemaType.toLowerCase(Locale.ROOT).replace("jsonschema", "jsonschema");
    }
    String name = schemaPath.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".raml") || content.startsWith("#%RAML")) {
      return "raml";
    }
    if (name.endsWith(".wsdl")) {
      return "wsdl";
    }
    if (name.endsWith(".xsd")) {
      return "xsd";
    }
    if (name.endsWith(".graphql")) {
      return "graphql";
    }
    if (name.endsWith(".avsc")) {
      return "avro";
    }
    if (content.contains("openapi:") || content.contains("\"openapi\"")) {
      return "openapi";
    }
    if (name.endsWith(".json")) {
      return "jsonschema";
    }
    if (name.endsWith(".yaml") || name.endsWith(".yml")) {
      return "openapi";
    }
    if (name.endsWith(".csv")) {
      return "csv";
    }
    return "unknown";
  }

  private static void collectPlaceholders(String text, Set<String> placeholders) {
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(text == null ? "" : text);
    while (matcher.find()) {
      placeholders.add(matcher.group(1));
    }
  }

  private static void detectDuplicateNames(String label, Set<String> names, List<MuleDiagnostic> diagnostics) {
    Set<String> seen = new LinkedHashSet<>();
    for (String value : names) {
      String name = value.substring(value.indexOf(':') + 1);
      if (!seen.add(name)) {
        diagnostics.add(MuleDiagnostic.high("src/main/mule", 0, "Duplicate " + label + " name detected: " + name,
            "Rename or consolidate duplicate Mule flows to avoid route ambiguity."));
      }
    }
  }

  private static boolean isLiteralSecret(String value) {
    String trimmed = value == null ? "" : value.trim();
    return !trimmed.isBlank() && !trimmed.contains("${") && !trimmed.contains("$[")
        && !trimmed.equalsIgnoreCase("changeme")
        && !trimmed.equalsIgnoreCase("password");
  }

  private static void addNamedElement(Element element, Set<String> target) {
    String name = element.getAttribute("name");
    if (!name.isBlank()) {
      target.add(localName(element) + ":" + name);
    }
  }

  private static String firstChildText(Element element, String localName) {
    NodeList children = element.getElementsByTagNameNS("*", localName);
    if (children.getLength() == 0) {
      return "";
    }
    return children.item(0).getTextContent().trim();
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

  private static String relativize(Path root, Path file) {
    try {
      return root.relativize(file).toString();
    } catch (Exception e) {
      return file.toString();
    }
  }

  private static String blankToUnknown(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }

  private static String emptyToDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private static int countMatches(String content, Pattern pattern) {
    int count = 0;
    Matcher matcher = pattern.matcher(content);
    while (matcher.find()) {
      count++;
    }
    return count;
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

  private record MuleFlowComponent(String relativeFile, String flowName, String qualifiedName, String displayName,
      boolean external, boolean branch) {
    private String displayLabel() {
      return displayName.isBlank() ? qualifiedName : qualifiedName + "(" + displayName + ")";
    }
  }

  private static final class MunitSuiteSummary {
    private final String relativePath;
    private final List<MunitTestSummary> tests = new ArrayList<>();
    private boolean hasMunitNamespace;
    private boolean hasMunitToolsNamespace;
    private boolean hasMunitSchema;
    private boolean hasMunitToolsSchema;
    private boolean hasConfig;

    private MunitSuiteSummary(String relativePath) {
      this.relativePath = relativePath;
    }
  }

  private static final class MunitTestSummary {
    private final String name;
    private final String description;
    private final Set<String> processors = new LinkedHashSet<>();
    private final Set<String> flowRefs = new LinkedHashSet<>();
    private final Set<String> mockedProcessors = new LinkedHashSet<>();
    private final Set<String> spiedProcessors = new LinkedHashSet<>();
    private final Set<String> verifiedProcessors = new LinkedHashSet<>();
    private final Set<String> assertedExpressions = new LinkedHashSet<>();
    private boolean hasExecution;
    private boolean hasValidation;
    private int assertions;

    private MunitTestSummary(String name, String description) {
      this.name = name;
      this.description = description;
    }

    private boolean hasMeaningfulValidation() {
      return assertions > 0 || !verifiedProcessors.isEmpty() || !spiedProcessors.isEmpty()
          || assertedExpressions.stream().anyMatch(expression -> !expression.isBlank());
    }

    private boolean targetsFlow(String flowName) {
      return flowRefs.contains(flowName) || name.toLowerCase(Locale.ROOT).contains(flowName.toLowerCase(Locale.ROOT));
    }

    private Set<String> coveredProcessors() {
      Set<String> covered = new LinkedHashSet<>();
      covered.addAll(mockedProcessors);
      covered.addAll(spiedProcessors);
      covered.addAll(verifiedProcessors);
      covered.addAll(processors);
      return covered;
    }

    private boolean looksLikeErrorTest() {
      String lowerName = name.toLowerCase(Locale.ROOT);
      return lowerName.contains("error") || lowerName.contains("exception") || lowerName.contains("failure")
          || mockedProcessors.stream().anyMatch(processor -> processor.toLowerCase(Locale.ROOT).contains("raise-error"))
          || assertedExpressions.stream().anyMatch(expression -> expression.toLowerCase(Locale.ROOT).contains("error"));
    }

    private boolean looksLikeFailureTest() {
      String lowerName = name.toLowerCase(Locale.ROOT);
      return lowerName.contains("error") || lowerName.contains("exception") || lowerName.contains("failure")
          || lowerName.contains("timeout") || lowerName.contains("unavailable");
    }

    private boolean assertsExpression(String marker) {
      return assertedExpressions.stream().anyMatch(expression -> expression.toLowerCase(Locale.ROOT)
          .contains(marker.toLowerCase(Locale.ROOT)));
    }

    private boolean hasHttpAssertionOpportunity() {
      return processors.stream().anyMatch(processor -> processor.startsWith("http:"))
          || flowRefs.stream().anyMatch(flowRef -> flowRef.toLowerCase(Locale.ROOT).contains("api"));
    }
  }
}
