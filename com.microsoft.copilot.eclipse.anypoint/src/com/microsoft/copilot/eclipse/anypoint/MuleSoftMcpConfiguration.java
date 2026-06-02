// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.anypoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Builds the MCP server configuration consumed by Copilot's MCP registration extension point.
 */
public final class MuleSoftMcpConfiguration {
  public static final String SERVER_NAME = "mulesoft";
  public static final String ANYPOINT_CLIENT_ID = "ANYPOINT_CLIENT_ID";
  public static final String ANYPOINT_CLIENT_SECRET = "ANYPOINT_CLIENT_SECRET";
  public static final String ANYPOINT_REGION = "ANYPOINT_REGION";

  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  private MuleSoftMcpConfiguration() {
  }

  /**
   * Builds a JSON configuration for the official MuleSoft MCP server.
   */
  public static String buildJson(String clientId, String clientSecret, String region) {
    if (isBlank(clientId) || isBlank(clientSecret)) {
      return "";
    }

    Map<String, Object> env = new LinkedHashMap<>();
    env.put(ANYPOINT_CLIENT_ID, clientId.trim());
    env.put(ANYPOINT_CLIENT_SECRET, clientSecret.trim());
    if (!isBlank(region)) {
      env.put(ANYPOINT_REGION, region.trim());
    }

    Map<String, Object> server = new LinkedHashMap<>();
    server.put("command", "npx");
    server.put("args", List.of("-y", "mulesoft-mcp-server", "start"));
    server.put("env", env);

    Map<String, Object> servers = new LinkedHashMap<>();
    servers.put(SERVER_NAME, server);

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("servers", servers);
    return GSON.toJson(root);
  }

  static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
