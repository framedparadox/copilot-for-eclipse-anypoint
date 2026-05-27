// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult.ToolInvocationStatus;

class MuleAgentToolsTest {

  @TempDir
  Path tempDir;

  @Test
  void muleProjectScanReturnsStructuredProjectInventory() throws Exception {
    Path project = createMuleProject();
    LanguageModelToolResult[] results = new MuleProjectScanTool()
        .invoke(Map.of("projectPath", project.toString()), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("\"status\": \"success\""));
    assertTrue(output.contains("muleXmlFiles=1"));
    assertTrue(output.contains("mule-http-connector"));
    assertTrue(output.contains("mule-maven-plugin"));
    assertTrue(output.contains("propertyPlaceholders=http.port"));
  }

  @Test
  void apiSchemaAnalyzeFindsRamlGovernanceGaps() throws Exception {
    Path raml = tempDir.resolve("api.raml");
    Files.writeString(raml, """
        #%RAML 1.0
        /accounts:
          get:
            responses:
              200:
                body:
                  application/json:
        """);

    LanguageModelToolResult[] results = new ApiSchemaAnalyzeTool()
        .invoke(Map.of("schemaPath", raml.toString(), "schemaType", "raml"), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("RAML contract is missing title"));
    assertTrue(output.contains("RAML contract does not declare security"));
  }

  @Test
  void muleSecurityReviewFindsSecretsAndInsecureHttp() throws Exception {
    Path project = createMuleProject();
    LanguageModelToolResult[] results = new MuleSecurityReviewTool()
        .invoke(Map.of("projectPath", project.toString(), "apiExposure", "public"), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("Possible hardcoded secret"));
    assertTrue(output.contains("HTTP protocol is configured without TLS"));
  }

  @Test
  void muleCodeReviewFindsMissingMunitAndErrorHandler() throws Exception {
    Path project = createMuleProject();
    LanguageModelToolResult[] results = new MuleCodeReviewTool()
        .invoke(Map.of("projectPath", project.toString(), "reviewType", "full"), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("No MUnit suites were found"));
    assertTrue(output.contains("No error-handler was detected"));
  }

  @Test
  void munitValidationFindsNoPurposeMissingStructureAndCoverage() throws Exception {
    Path project = createMuleProjectWithWeakMunit();
    LanguageModelToolResult[] results = new MunitValidateFlowTestsTool()
        .invoke(Map.of("projectPath", project.toString(), "flowName", "accounts-get-flow"), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("MUnit suite is missing the munit-tools namespace"));
    assertTrue(output.contains("MUnit test appears to have no logical validation purpose"));
    assertTrue(output.contains("Not all flow components are explicitly mocked, spied, verified, or asserted"));
    assertTrue(output.contains("External connector calls are not mocked"));
  }

  @Test
  void munitFullReviewFindsScenarioAndAssertionQualityGaps() throws Exception {
    Path project = createMuleProjectWithWeakMunit();
    LanguageModelToolResult[] results = new MunitFullReviewTool()
        .invoke(Map.of("projectPath", project.toString(), "flowName", "accounts-get-flow"), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("Completed full MUnit review"));
    assertTrue(output.contains("MUnit coverage is too shallow for the flow complexity"));
    assertTrue(output.contains("MUnit tests do not obviously assert the response payload"));
    assertTrue(output.contains("MUnit test name does not communicate the scenario"));
  }

  @Test
  void munitImprovementSuggestionsReturnsCadenceGuidance() throws Exception {
    Path project = createMuleProjectWithWeakMunit();
    LanguageModelToolResult[] results = new MunitImprovementSuggestionsTool()
        .invoke(Map.of("projectPath", project.toString(), "flowName", "accounts-get-flow"), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("recommendedCadence=positive, negative, edge, connector-failure"));
    assertTrue(output.contains("Some flows have fewer than two MUnit scenarios"));
    assertTrue(output.contains("For each external connector, mock success and failure"));
  }

  @Test
  void transformReadMatchesByNameCaseInsensitiveAndSubstring() throws Exception {
    Path xml = createMuleProjectWithTransform(false);

    // Case-insensitive match
    LanguageModelToolResult[] caseResult = new MuleTransformReadTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "transformName", "map accounts"), null).get();
    assertEquals(ToolInvocationStatus.success, caseResult[0].getStatus());
    assertTrue(caseResult[0].getContent().get(0).getValue().contains("target=payload"));

    // Substring match (partial name)
    LanguageModelToolResult[] subResult = new MuleTransformReadTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "transformName", "Accounts"), null).get();
    assertEquals(ToolInvocationStatus.success, subResult[0].getStatus());
    assertTrue(subResult[0].getContent().get(0).getValue().contains("target=payload"));

    // Non-matching name still fails
    LanguageModelToolResult[] noMatch = new MuleTransformReadTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "transformName", "Non Existent Transform"), null).get();
    assertEquals(ToolInvocationStatus.error, noMatch[0].getStatus());
    assertTrue(noMatch[0].getContent().get(0).getValue().contains("No ee:transform element matched"));
  }

  @Test
  void dwlReadToolReturnsFileContentAndLineCount() throws Exception {
    Path dwl = tempDir.resolve("normalize.dwl");
    Files.writeString(dwl, "%dw 2.0\noutput application/json\n---\npayload map (item -> item)");

    LanguageModelToolResult[] results = new MuleDwlReadTool()
        .invoke(Map.of("dwlFilePath", dwl.toString()), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("lines=4"));
    assertTrue(output.contains("script:"));
    assertTrue(output.contains("%dw 2.0"));
    assertTrue(output.contains("payload map"));
  }

  @Test
  void dwlReadToolRejectsNonDwlFile() throws Exception {
    Path xml = tempDir.resolve("test.xml");
    Files.writeString(xml, "<root/>");

    LanguageModelToolResult[] results = new MuleDwlReadTool()
        .invoke(Map.of("dwlFilePath", xml.toString()), null).get();

    assertEquals(ToolInvocationStatus.error, results[0].getStatus());
    assertTrue(results[0].getContent().get(0).getValue().contains(".dwl"));
  }

  @Test
  void dwlWriteToolWritesScriptToFile() throws Exception {
    Path dwl = tempDir.resolve("transform.dwl");
    Files.writeString(dwl, "%dw 2.0\noutput application/json\n---\npayload");
    String newScript = "%dw 2.0\noutput application/json\n---\npayload map (item -> { id: item.id })";

    LanguageModelToolResult[] results = new MuleDwlWriteTool()
        .invoke(Map.of("dwlFilePath", dwl.toString(), "dwlScript", newScript), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    assertEquals(newScript, Files.readString(dwl));
  }

  @Test
  void dwlOptimizeToolDetectsMissingOutputDirective() throws Exception {
    Path dwl = tempDir.resolve("bad.dwl");
    Files.writeString(dwl, "%dw 2.0\n---\npayload");

    LanguageModelToolResult[] results = new MuleDwlOptimizeTool()
        .invoke(Map.of("dwlFilePath", dwl.toString(), "includeComments", false, "applyFixes", false), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("missing-output-directive"));
    assertTrue(output.contains("output application/json"));
  }

  @Test
  void dwlOptimizeToolDetectsNestedMapFilter() throws Exception {
    Path dwl = tempDir.resolve("nested.dwl");
    Files.writeString(dwl, "%dw 2.0\noutput application/json\n---\npayload.a map (x -> payload.b filter (y -> y.id == x.id))");

    LanguageModelToolResult[] results = new MuleDwlOptimizeTool()
        .invoke(Map.of("dwlFilePath", dwl.toString(), "includeComments", false, "applyFixes", false), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("nested-map-filter"));
    assertTrue(output.contains("groupBy"));
  }

  @Test
  void dwlOptimizeToolAppliesFixesWhenRequested() throws Exception {
    Path dwl = tempDir.resolve("nocomment.dwl");
    String original = "%dw 2.0\noutput application/json\n---\nfun greet(name) = \"Hello \" ++ name\ngreet(payload.name)";
    Files.writeString(dwl, original);

    LanguageModelToolResult[] results = new MuleDwlOptimizeTool()
        .invoke(Map.of("dwlFilePath", dwl.toString(), "includeComments", true, "applyFixes", true), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String written = Files.readString(dwl);
    assertTrue(written.contains("// greet"));
  }

  @Test
  void dwlToolsExposeExpectedToolMetadata() {
    assertEquals("mule_read_dwl_file", new MuleDwlReadTool().getToolInformation().getName());
    assertEquals("mule_write_dwl_file", new MuleDwlWriteTool().getToolInformation().getName());
    assertEquals("mule_optimize_dwl", new MuleDwlOptimizeTool().getToolInformation().getName());
    assertTrue(new MuleDwlReadTool().getToolInformation().getDescription().contains("read-only"));
    assertTrue(new MuleDwlWriteTool().getToolInformation().getDescription().contains("%dw 2.0"));
    assertTrue(new MuleDwlOptimizeTool().getToolInformation().getDescription().contains("groupBy"));
  }

  @Test
  void transformToolsExposeExpectedToolMetadata() {
    assertEquals("mule_read_transform", new MuleTransformReadTool().getToolInformation().getName());
    assertEquals("mule_write_transform", new MuleTransformWriteTool().getToolInformation().getName());
    assertTrue(new MuleTransformReadTool().getToolInformation().getDescription().contains("set-attributes"));
    assertTrue(new MuleTransformWriteTool().getToolInformation().getDescription().contains("variable:name"));
  }

  @Test
  void transformReadReturnsPayloadAttributesVariablesAndExternalDwl() throws Exception {
    Path xml = createMuleProjectWithTransform(false);
    LanguageModelToolResult[] results = new MuleTransformReadTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "transformName", "Map Accounts"), null).get();

    assertEquals(ToolInvocationStatus.success, results[0].getStatus());
    String output = results[0].getContent().get(0).getValue();
    assertTrue(output.contains("target=payload"));
    assertTrue(output.contains("target=attributes"));
    assertTrue(output.contains("target=variable:customerId"));
    assertTrue(output.contains("target=variable:externalVar"));
    assertTrue(output.contains("resource=dw/external.dwl"));
    assertTrue(output.contains("resourceStatus=resolved"));
    assertTrue(output.contains("externalValue"));
  }

  @Test
  void transformWriteUpdatesPayloadAttributesAndVariables() throws Exception {
    Path xml = createMuleProjectWithTransform(false);
    String payloadScript = """
        %dw 2.0
        output application/json
        ---
        { updatedPayload: true }
        """;
    String attributesScript = """
        %dw 2.0
        output application/java
        ---
        attributes ++ { source: "test" }
        """;
    String variableScript = """
        %dw 2.0
        output application/java
        ---
        "updated-variable"
        """;

    assertEquals(ToolInvocationStatus.success, new MuleTransformWriteTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "transformName", "Map Accounts",
            "target", "payload", "dwlScript", payloadScript), null).get()[0].getStatus());
    assertEquals(ToolInvocationStatus.success, new MuleTransformWriteTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "transformName", "Map Accounts",
            "target", "attributes", "dwlScript", attributesScript), null).get()[0].getStatus());
    assertEquals(ToolInvocationStatus.success, new MuleTransformWriteTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "transformName", "Map Accounts",
            "target", "variable:customerId", "dwlScript", variableScript), null).get()[0].getStatus());

    String updated = Files.readString(xml);
    assertTrue(updated.contains("updatedPayload"));
    assertTrue(updated.contains("attributes ++"));
    assertTrue(updated.contains("updated-variable"));
  }

  @Test
  void transformWriteErrorsDoNotModifyXml() throws Exception {
    Path xml = createMuleProjectWithTransform(true);
    String original = Files.readString(xml);
    String script = """
        %dw 2.0
        output application/json
        ---
        payload
        """;

    LanguageModelToolResult[] ambiguous = new MuleTransformWriteTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "dwlScript", script), null).get();
    assertEquals(ToolInvocationStatus.error, ambiguous[0].getStatus());
    assertTrue(ambiguous[0].getContent().get(0).getValue().contains("Multiple ee:transform"));
    assertEquals(original, Files.readString(xml));

    LanguageModelToolResult[] missingTransform = new MuleTransformWriteTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "transformName", "Missing",
            "target", "payload", "dwlScript", script), null).get();
    assertEquals(ToolInvocationStatus.error, missingTransform[0].getStatus());
    assertEquals(original, Files.readString(xml));

    LanguageModelToolResult[] missingTarget = new MuleTransformWriteTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "transformName", "Map Accounts",
            "target", "missingVar", "dwlScript", script), null).get();
    assertEquals(ToolInvocationStatus.error, missingTarget[0].getStatus());
    assertEquals(original, Files.readString(xml));

    LanguageModelToolResult[] blankScript = new MuleTransformWriteTool()
        .invoke(Map.of("xmlFilePath", xml.toString(), "transformName", "Map Accounts",
            "target", "payload", "dwlScript", "   "), null).get();
    assertEquals(ToolInvocationStatus.error, blankScript[0].getStatus());
    assertEquals(original, Files.readString(xml));
  }

  @Test
  void mulesoftAgentAssetsExposeLocalTransformTools() throws Exception {
    Path repo = findRepoRoot();
    String anypointTemplate = Files.readString(
        repo.resolve("com.microsoft.copilot.eclipse.anypoint/templates/mulesoft-agent.agent.md"));
    String bundledAgent = Files.readString(repo.resolve(
        "com.microsoft.copilot.eclipse.ui/mulesoft-copilot/.github/agents/mulesoft-engineer.agent.md"));

    assertTrue(anypointTemplate.contains("- mule_read_transform"));
    assertTrue(anypointTemplate.contains("- mule_write_transform"));
    assertFalse(anypointTemplate.contains("mulesoft/"));
    assertTrue(bundledAgent.contains("- mule_read_transform"));
    assertTrue(bundledAgent.contains("- mule_write_transform"));
    assertFalse(bundledAgent.contains("mulesoft/"));
  }

  private Path createMuleProject() throws Exception {
    Path project = tempDir.resolve("mule-app");
    Files.createDirectories(project.resolve("src/main/mule"));
    Files.createDirectories(project.resolve("src/main/resources"));
    Files.writeString(project.resolve("pom.xml"), """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>example</groupId>
          <artifactId>mule-app</artifactId>
          <version>1.0.0</version>
          <dependencies>
            <dependency>
              <groupId>org.mule.connectors</groupId>
              <artifactId>mule-http-connector</artifactId>
              <version>1.9.0</version>
            </dependency>
          </dependencies>
          <build>
            <plugins>
              <plugin>
                <artifactId>mule-maven-plugin</artifactId>
              </plugin>
            </plugins>
          </build>
        </project>
        """);
    Files.writeString(project.resolve("mule-artifact.json"), """
        {"minMuleVersion":"4.6.0"}
        """);
    Files.writeString(project.resolve("src/main/mule/api.xml"), """
        <mule xmlns="http://www.mulesoft.org/schema/mule/core"
              xmlns:http="http://www.mulesoft.org/schema/mule/http"
              xmlns:apikit="http://www.mulesoft.org/schema/mule/mule-apikit">
          <http:listener-config name="HTTP_Listener_config" protocol="HTTP">
            <http:listener-connection host="0.0.0.0" port="${http.port}" />
          </http:listener-config>
          <flow name="accounts-get-flow">
            <http:listener config-ref="HTTP_Listener_config" path="/accounts" />
            <logger message="#[payload]" />
          </flow>
        </mule>
        """);
    Files.writeString(project.resolve("src/main/resources/dev.properties"), """
        http.port=8081
        client.secret=literalSecret
        """);
    return project;
  }

  private Path createMuleProjectWithWeakMunit() throws Exception {
    Path project = createMuleProject();
    Files.createDirectories(project.resolve("src/test/munit"));
    Files.writeString(project.resolve("src/main/mule/api.xml"), """
        <mule xmlns="http://www.mulesoft.org/schema/mule/core"
              xmlns:http="http://www.mulesoft.org/schema/mule/http"
              xmlns:apikit="http://www.mulesoft.org/schema/mule/mule-apikit">
          <http:listener-config name="HTTP_Listener_config" protocol="HTTP">
            <http:listener-connection host="0.0.0.0" port="${http.port}" />
          </http:listener-config>
          <http:request-config name="HTTP_Request_config" />
          <flow name="accounts-get-flow">
            <http:listener config-ref="HTTP_Listener_config" path="/accounts" />
            <set-payload value="#[{id: '123'}]" />
            <http:request config-ref="HTTP_Request_config" method="GET" path="/accounts" />
          </flow>
        </mule>
        """);
    Files.writeString(project.resolve("src/test/munit/accounts-test.xml"), """
        <mule xmlns="http://www.mulesoft.org/schema/mule/core"
              xmlns:munit="http://www.mulesoft.org/schema/mule/munit"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="
                http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd
                http://www.mulesoft.org/schema/mule/munit
                http://www.mulesoft.org/schema/mule/munit/current/mule-munit.xsd">
          <munit:config name="accounts-test-suite" />
          <munit:test name="accounts-get-flow-test">
            <munit:execution>
              <flow-ref name="accounts-get-flow" />
            </munit:execution>
            <munit:validation />
          </munit:test>
        </mule>
        """);
    return project;
  }

  private Path createMuleProjectWithTransform(boolean includeSecondTransform) throws Exception {
    Path project = tempDir.resolve(includeSecondTransform ? "mule-transforms-two" : "mule-transforms-one");
    Files.createDirectories(project.resolve("src/main/mule"));
    Files.createDirectories(project.resolve("src/main/resources/dw"));
    Files.writeString(project.resolve("pom.xml"), """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>example</groupId>
          <artifactId>mule-transform-app</artifactId>
          <version>1.0.0</version>
        </project>
        """);
    Files.writeString(project.resolve("src/main/resources/dw/external.dwl"), """
        %dw 2.0
        output application/java
        ---
        "externalValue"
        """);
    String secondTransform = includeSecondTransform ? """
          <ee:transform doc:name="Second Transform" doc:id="second-transform">
            <ee:message>
              <ee:set-payload><![CDATA[%dw 2.0
        output application/json
        ---
        payload
        ]]></ee:set-payload>
            </ee:message>
          </ee:transform>
        """ : "";
    Path xml = project.resolve("src/main/mule/api.xml");
    Files.writeString(xml, """
        <mule xmlns="http://www.mulesoft.org/schema/mule/core"
              xmlns:ee="http://www.mulesoft.org/schema/mule/ee/core"
              xmlns:doc="http://www.mulesoft.org/schema/mule/documentation">
          <flow name="map-flow">
            <ee:transform doc:name="Map Accounts" doc:id="map-accounts">
              <ee:message>
                <ee:set-payload><![CDATA[%dw 2.0
        output application/json
        ---
        payload
        ]]></ee:set-payload>
                <ee:set-attributes><![CDATA[%dw 2.0
        output application/java
        ---
        attributes
        ]]></ee:set-attributes>
              </ee:message>
              <ee:variables>
                <ee:set-variable variableName="customerId"><![CDATA[%dw 2.0
        output application/java
        ---
        vars.customerId
        ]]></ee:set-variable>
                <ee:set-variable variableName="externalVar" resource="dw/external.dwl" />
              </ee:variables>
            </ee:transform>
        ${SECOND_TRANSFORM}  </flow>
        </mule>
        """.replace("${SECOND_TRANSFORM}", secondTransform));
    return xml;
  }

  private Path findRepoRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      if (Files.isDirectory(current.resolve("com.microsoft.copilot.eclipse.anypoint/templates"))
          && Files.isDirectory(current.resolve("com.microsoft.copilot.eclipse.ui/mulesoft-copilot/.github"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Unable to locate repository root from test runtime.");
  }
}
