// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.services;

import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.copilot.eclipse.ui.chat.services.TransformEditorContextService.TransformEditorSnapshot;
import com.microsoft.copilot.eclipse.ui.chat.services.TransformEditorContextService.TransformEditorSnapshot.ScriptEntry;
import com.microsoft.copilot.eclipse.ui.chat.services.TransformEditorContextService.TransformEditorSnapshot.TransformEntry;

/**
 * Applies explicit @transform chat context to the message sent to the language server.
 */
public final class TransformEditorContextPromptProcessor {
  private static final String TRANSFORM_CONTEXT_BLOCK_TITLE = "[Transform Context]";

  private TransformEditorContextPromptProcessor() {
    // Utility class.
  }

  /**
   * Adds transform context to the server payload when the prompt starts with @transform and the feature is available.
   *
   * @param message original user message
   * @param enabled whether transform context is enabled
   * @param supportedMode whether the active chat mode supports transform context
   * @param snapshotSupplier supplier for the current transform editor snapshot
   * @return processed message details
   */
  public static ProcessedMessage process(String message, boolean enabled, boolean supportedMode,
      Supplier<TransformEditorSnapshot> snapshotSupplier) {
    if (!enabled || !supportedMode || !startsWithTransformCommand(message)) {
      return new ProcessedMessage(message, false);
    }

    String promptWithoutCommand = stripTransformCommand(message);
    TransformEditorSnapshot snapshot = snapshotSupplier.get();
    String serverMessage = appendTransformContext(promptWithoutCommand, snapshot);
    return new ProcessedMessage(serverMessage, true);
  }

  /**
   * Checks whether a message starts with @transform as a full first token.
   *
   * @param message user message
   * @return true when @transform is the leading command
   */
  public static boolean startsWithTransformCommand(String message) {
    String trimmed = StringUtils.trimToEmpty(message);
    String command = ChatCompletionService.AGENT_MARK + ChatCompletionService.TRANSFORM_CONTEXT_COMMAND;
    if (!trimmed.startsWith(command)) {
      return false;
    }
    return trimmed.length() == command.length() || Character.isWhitespace(trimmed.charAt(command.length()));
  }

  private static String stripTransformCommand(String message) {
    String trimmed = StringUtils.trimToEmpty(message);
    int commandLength = (ChatCompletionService.AGENT_MARK + ChatCompletionService.TRANSFORM_CONTEXT_COMMAND).length();
    return StringUtils.stripStart(trimmed.substring(commandLength), null);
  }

  private static String appendTransformContext(String prompt, TransformEditorSnapshot snapshot) {
    StringBuilder builder = new StringBuilder(StringUtils.defaultString(prompt).stripTrailing());
    if (!builder.isEmpty()) {
      builder.append("\n\n");
    }
    builder.append(TRANSFORM_CONTEXT_BLOCK_TITLE).append('\n');

    if (snapshot == null || !snapshot.isAvailable()) {
      String reason = snapshot != null ? snapshot.unavailableReason() : "Transform context is unavailable.";
      builder.append("Transform context unavailable: ").append(reason);
      return builder.toString();
    }

    if (snapshot.isFromPropertiesView()) {
      builder.append("source: properties-view\n");
    }
    builder.append("file: ").append(snapshot.xmlFilePath()).append('\n');
    builder.append("transforms: ").append(snapshot.transformCount()).append('\n');

    if (snapshot.isEmpty()) {
      builder.append("Transform context: No ee:transform elements found in this file.");
      return builder.toString();
    }

    for (TransformEntry entry : snapshot.transforms()) {
      builder.append('\n');
      builder.append("--- Transform: ").append(entry.label()).append(" ---\n");
      for (ScriptEntry script : entry.scripts()) {
        builder.append("target: ").append(script.target()).append('\n');
        if (!script.outputType().isBlank()) {
          builder.append("outputType: ").append(script.outputType()).append('\n');
        }
        builder.append("script:\n");
        builder.append(script.script().stripTrailing()).append('\n');
      }
    }

    if (snapshot.truncated()) {
      builder.append("\n(Note: one or more DataWeave scripts were truncated due to length.)");
    }

    return builder.toString();
  }

  /**
   * Silently appends transform context to the message without requiring an explicit {@code @transform}
   * command. Returns the original message unchanged when context is unavailable or empty — no error
   * block is emitted, since the user did not ask for it.
   *
   * @param message original user message (no @transform prefix expected)
   * @param enabled whether transform context is enabled
   * @param supportedMode whether the active chat mode supports transform context
   * @param snapshotSupplier supplier for the current transform editor snapshot
   * @return processed message details
   */
  public static ProcessedMessage processAutoInject(String message, boolean enabled, boolean supportedMode,
      Supplier<TransformEditorSnapshot> snapshotSupplier) {
    if (!enabled || !supportedMode) {
      return new ProcessedMessage(message, false);
    }
    TransformEditorSnapshot snapshot = snapshotSupplier.get();
    if (snapshot == null || !snapshot.isAvailable() || snapshot.isEmpty()) {
      return new ProcessedMessage(message, false);
    }
    String serverMessage = appendTransformContext(message, snapshot);
    return new ProcessedMessage(serverMessage, true);
  }

  /**
   * Result of processing a chat message for transform context.
   */
  public record ProcessedMessage(String serverMessage, boolean transformContextRequested) {
  }
}
