// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
