// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.anypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for MuleSoft MCP configuration generation.
 */
class MuleSoftMcpConfigurationTest {

  @Test
  void buildJsonReturnsEmptyWithoutCredentials() {
    assertEquals("", MuleSoftMcpConfiguration.buildJson("", "secret", "PROD_US"));
    assertEquals("", MuleSoftMcpConfiguration.buildJson("client", "", "PROD_US"));
  }

  @Test
  void buildJsonIncludesOfficialServerCommandAndEnvironment() {
    String json = MuleSoftMcpConfiguration.buildJson("client", "secret", "PROD_US");

    assertTrue(json.contains("\"mulesoft\""));
    assertTrue(json.contains("\"command\":\"npx\""));
    assertTrue(json.contains("\"-y\""));
    assertTrue(json.contains("\"mulesoft-mcp-server\""));
    assertTrue(json.contains("\"start\""));
    assertTrue(json.contains("\"ANYPOINT_CLIENT_ID\":\"client\""));
    assertTrue(json.contains("\"ANYPOINT_CLIENT_SECRET\":\"secret\""));
    assertTrue(json.contains("\"ANYPOINT_REGION\":\"PROD_US\""));
  }
}
