// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.services;

import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.copilot.eclipse.ui.chat.services.ConsoleContextService.ConsoleSnapshot;

/**
 * Applies explicit @console chat context to the message sent to the language server.
 */
public final class ConsoleContextPromptProcessor {
  private static final String CONSOLE_CONTEXT_BLOCK_TITLE = "[Console Context]";

  private ConsoleContextPromptProcessor() {
    // Utility class.
  }

  /**
   * Adds console context to the server payload when the prompt starts with @console and the feature is available.
   *
   * @param message original user message
   * @param enabled whether console context is enabled
   * @param supportedMode whether the active chat mode supports console context
   * @param snapshotSupplier supplier for the current console snapshot
   * @return processed message details
   */
  public static ProcessedMessage process(String message, boolean enabled, boolean supportedMode,
      Supplier<ConsoleSnapshot> snapshotSupplier) {
    if (!enabled || !supportedMode || !startsWithConsoleCommand(message)) {
      return new ProcessedMessage(message, false);
    }

    String promptWithoutCommand = stripConsoleCommand(message);
    ConsoleSnapshot snapshot = snapshotSupplier.get();
    String serverMessage = appendConsoleContext(promptWithoutCommand, snapshot);
    return new ProcessedMessage(serverMessage, true);
  }

  /**
   * Checks whether a message starts with @console as a full first token.
   *
   * @param message user message
   * @return true when @console is the leading command
   */
  public static boolean startsWithConsoleCommand(String message) {
    String trimmed = StringUtils.trimToEmpty(message);
    if (!trimmed.startsWith(ChatCompletionService.AGENT_MARK + ChatCompletionService.CONSOLE_CONTEXT_COMMAND)) {
      return false;
    }

    int commandLength = (ChatCompletionService.AGENT_MARK + ChatCompletionService.CONSOLE_CONTEXT_COMMAND).length();
    return trimmed.length() == commandLength || Character.isWhitespace(trimmed.charAt(commandLength));
  }

  private static String stripConsoleCommand(String message) {
    String trimmed = StringUtils.trimToEmpty(message);
    int commandLength = (ChatCompletionService.AGENT_MARK + ChatCompletionService.CONSOLE_CONTEXT_COMMAND).length();
    return StringUtils.stripStart(trimmed.substring(commandLength), null);
  }

  private static String appendConsoleContext(String prompt, ConsoleSnapshot snapshot) {
    StringBuilder builder = new StringBuilder(StringUtils.defaultString(prompt).stripTrailing());
    if (!builder.isEmpty()) {
      builder.append("\n\n");
    }
    builder.append(CONSOLE_CONTEXT_BLOCK_TITLE).append('\n');

    if (snapshot == null || !snapshot.isAvailable()) {
      String reason = snapshot != null ? snapshot.unavailableReason() : "Console context is unavailable.";
      builder.append("Console context unavailable: ").append(reason);
      return builder.toString();
    }

    builder.append("Console: ").append(snapshot.consoleName()).append('\n');
    builder.append("Truncated: ").append(snapshot.truncated() ? "yes" : "no").append('\n');

    if (snapshot.isEmpty()) {
      builder.append("Output: Console output is empty.");
      return builder.toString();
    }

    builder.append("Output:\n<console-output>\n");
    builder.append(enrichConsoleOutput(snapshot.consoleName(), snapshot.output()).stripTrailing());
    builder.append("\n</console-output>");
    return builder.toString();
  }

  private static String enrichConsoleOutput(String consoleName, String rawOutput) {
    if (consoleName != null) {
      String lowerName = consoleName.toLowerCase();
      if (lowerName.contains("maven") || lowerName.contains("mvn")) {
        return MavenConsoleParser.enrich(rawOutput);
      }
    }
    return MuleConsoleParser.enrich(rawOutput);
  }

  /**
   * Result of processing a chat message for console context.
   */
  public record ProcessedMessage(String serverMessage, boolean consoleContextRequested) {
  }
}
