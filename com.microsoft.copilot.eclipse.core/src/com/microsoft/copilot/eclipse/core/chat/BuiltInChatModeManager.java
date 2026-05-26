// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.core.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import com.microsoft.copilot.eclipse.core.CopilotCore;
import com.microsoft.copilot.eclipse.core.chat.service.BuiltInChatModeService;

/**
 * Singleton manager for built-in chat modes. Built-in modes are loaded once from the LSP API at startup.
 */
public enum BuiltInChatModeManager {
  INSTANCE;

  private final BuiltInChatModeService service;
  private List<BuiltInChatMode> builtInModes;

  BuiltInChatModeManager() {
    this.service = new BuiltInChatModeService();
    this.builtInModes = new CopyOnWriteArrayList<>();
    loadModesSync();
  }

  private void loadModesSync() {
    try {
      List<BuiltInChatMode> modes = service.loadBuiltInModes().get();
      this.builtInModes = new CopyOnWriteArrayList<>(modes);
    } catch (Exception e) {
      // Initialize with empty list on failure
      this.builtInModes = new CopyOnWriteArrayList<>();
    }
  }

  public List<BuiltInChatMode> getBuiltInModes() {
    return new ArrayList<>(builtInModes);
  }

  /**
   * Retrieves a built-in chat mode by its display name.
   *
   * @param displayName the display name of the mode to retrieve (case-insensitive)
   * @return the built-in chat mode with the matching display name, or null if not found
   */
  public BuiltInChatMode getBuiltInModeByDisplayName(String displayName) {
    return builtInModes.stream().filter(mode -> mode.getDisplayName().equalsIgnoreCase(displayName)).findFirst()
        .orElse(null);
  }

  /**
   * Retrieves a built-in chat mode by its ID.
   *
   * @param id the ID of the mode to retrieve
   * @return the built-in chat mode with the matching ID, or null if not found
   */
  public BuiltInChatMode getBuiltInModeById(String id) {
    return builtInModes.stream().filter(mode -> mode.getId().equals(id)).findFirst().orElse(null);
  }

  /**
   * Reloads built-in chat modes from the LSP API synchronously. Blocks the calling thread until
   * the LSP responds. Prefer {@link #reloadModesAsync()} to avoid blocking the UI thread.
   */
  public void reloadModes() {
    loadModesSync();
  }

  /**
   * Reloads built-in chat modes from the LSP API asynchronously. Safe to call from the UI thread.
   * The returned future completes once the modes list has been updated.
   */
  public CompletableFuture<Void> reloadModesAsync() {
    return service.loadBuiltInModes().thenAccept(modes -> {
      if (modes != null && !modes.isEmpty()) {
        this.builtInModes = new CopyOnWriteArrayList<>(modes);
      }
    }).exceptionally(ex -> {
      CopilotCore.LOGGER.error("Failed to reload built-in modes asynchronously", ex);
      return null;
    });
  }
}