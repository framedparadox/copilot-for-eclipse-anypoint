// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.anypoint;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;

import com.microsoft.copilot.eclipse.core.CopilotCore;

/**
 * Secure preference access for MuleSoft MCP settings.
 */
public final class MuleSoftMcpSettings {
  private static final String NODE = "/com.microsoft.copilot.eclipse.anypoint/mulesoftMcp";
  private static final String KEY_ENABLED = "enabled";
  private static final String KEY_CLIENT_ID = "clientId";
  private static final String KEY_CLIENT_SECRET = "clientSecret";
  private static final String KEY_REGION = "region";

  private MuleSoftMcpSettings() {
  }

  /**
   * Returns whether the MuleSoft MCP server registration is enabled.
   */
  public static boolean isEnabled() {
    try {
      return node().getBoolean(KEY_ENABLED, false);
    } catch (StorageException e) {
      CopilotCore.LOGGER.error("Failed to read MuleSoft MCP enabled preference", e);
      return false;
    }
  }

  /**
   * Stores whether the MuleSoft MCP server registration is enabled.
   */
  public static void setEnabled(boolean enabled) {
    try {
      node().putBoolean(KEY_ENABLED, enabled, false);
    } catch (StorageException e) {
      CopilotCore.LOGGER.error("Failed to write MuleSoft MCP enabled preference", e);
    }
    flush();
  }

  public static String getClientId() {
    return getSecure(KEY_CLIENT_ID, System.getenv(MuleSoftMcpConfiguration.ANYPOINT_CLIENT_ID));
  }

  public static String getClientSecret() {
    return getSecure(KEY_CLIENT_SECRET, System.getenv(MuleSoftMcpConfiguration.ANYPOINT_CLIENT_SECRET));
  }

  public static String getRegion() {
    return getSecure(KEY_REGION, System.getenv(MuleSoftMcpConfiguration.ANYPOINT_REGION));
  }

  /**
   * Saves all MuleSoft MCP settings.
   */
  public static void save(boolean enabled, String clientId, String clientSecret, String region) {
    ISecurePreferences node = node();
    try {
      node.putBoolean(KEY_ENABLED, enabled, false);
    } catch (StorageException e) {
      CopilotCore.LOGGER.error("Failed to write MuleSoft MCP enabled preference", e);
    }
    putSecure(KEY_CLIENT_ID, clientId);
    putSecure(KEY_CLIENT_SECRET, clientSecret);
    putSecure(KEY_REGION, region);
    flush();
  }

  private static String getSecure(String key, String fallback) {
    try {
      return node().get(key, fallback == null ? "" : fallback);
    } catch (StorageException e) {
      CopilotCore.LOGGER.error("Failed to read MuleSoft MCP secure preference: " + key, e);
      return fallback == null ? "" : fallback;
    }
  }

  private static void putSecure(String key, String value) {
    try {
      node().put(key, value == null ? "" : value.trim(), true);
    } catch (StorageException e) {
      CopilotCore.LOGGER.error("Failed to write MuleSoft MCP secure preference: " + key, e);
    }
  }

  private static void flush() {
    try {
      node().flush();
    } catch (Exception e) {
      CopilotCore.LOGGER.error("Failed to flush MuleSoft MCP secure preferences", e);
    }
  }

  private static ISecurePreferences node() {
    return SecurePreferencesFactory.getDefault().node(NODE);
  }
}
