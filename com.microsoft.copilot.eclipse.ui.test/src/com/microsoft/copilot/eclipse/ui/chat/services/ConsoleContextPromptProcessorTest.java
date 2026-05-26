// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.microsoft.copilot.eclipse.ui.chat.services.ConsoleContextPromptProcessor.ProcessedMessage;
import com.microsoft.copilot.eclipse.ui.chat.services.ConsoleContextService.ConsoleSnapshot;

class ConsoleContextPromptProcessorTest {

  @Test
  void processAddsConsoleContextWhenLeadingCommandIsEnabledAndSupported() {
    ProcessedMessage result = ConsoleContextPromptProcessor.process("@console explain the failure", true, true,
        () -> ConsoleSnapshot.available("Maven", "BUILD FAILURE", false));

    assertTrue(result.consoleContextRequested());
    assertTrue(result.serverMessage().startsWith("explain the failure"));
    assertTrue(result.serverMessage().contains("[Console Context]"));
    assertTrue(result.serverMessage().contains("Console: Maven"));
    assertTrue(result.serverMessage().contains("Truncated: no"));
    assertTrue(result.serverMessage().contains("BUILD FAILURE"));
    assertTrue(result.serverMessage().contains("[Maven Build Summary]"));
    assertFalse(result.serverMessage().contains("@console"));
  }

  @Test
  void processUsesMavenParserWhenConsoleNameContainsMaven() {
    ProcessedMessage result = ConsoleContextPromptProcessor.process("@console check build", true, true,
        () -> ConsoleSnapshot.available("Maven Build", "[INFO] BUILD SUCCESS", false));

    assertTrue(result.serverMessage().contains("[Maven Build Summary]"));
    assertTrue(result.serverMessage().contains("Result: BUILD SUCCESS"));
    assertFalse(result.serverMessage().contains("[Mule Error Summary]"));
  }

  @Test
  void processUsesMuleParserForNonMavenConsoleName() {
    String muleExceptionOutput = "org.mule.runtime.core.internal.exception.MessagingException\n"
        + "error type: EXPRESSION:INVALID_EXPRESSION\nFlow name: myFlow";
    ProcessedMessage result = ConsoleContextPromptProcessor.process("@console check error", true, true,
        () -> ConsoleSnapshot.available("Mule Application", muleExceptionOutput, false));

    assertTrue(result.serverMessage().contains("[Mule Error Summary]"));
    assertFalse(result.serverMessage().contains("[Maven Build Summary]"));
  }

  @Test
  void processOnlyConsumesLeadingConsoleCommand() {
    AtomicBoolean supplierCalled = new AtomicBoolean(false);

    ProcessedMessage result = ConsoleContextPromptProcessor.process("please inspect @console output", true, true, () -> {
      supplierCalled.set(true);
      return ConsoleSnapshot.available("Console", "output", false);
    });

    assertFalse(result.consoleContextRequested());
    assertEquals("please inspect @console output", result.serverMessage());
    assertFalse(supplierCalled.get());
  }

  @Test
  void processDoesNotMatchWhenCommandFollowedByNonWhitespace() {
    ProcessedMessage result = ConsoleContextPromptProcessor.process("@console-output explain", true, true,
        () -> ConsoleSnapshot.available("Console", "output", false));

    assertFalse(result.consoleContextRequested());
    assertEquals("@console-output explain", result.serverMessage());
  }

  @Test
  void processLeavesMessageUnchangedWhenFeatureDisabled() {
    ProcessedMessage result = ConsoleContextPromptProcessor.process("@console explain", false, true,
        () -> ConsoleSnapshot.available("Console", "output", false));

    assertFalse(result.consoleContextRequested());
    assertEquals("@console explain", result.serverMessage());
  }

  @Test
  void processLeavesMessageUnchangedWhenModeUnsupported() {
    ProcessedMessage result = ConsoleContextPromptProcessor.process("@console explain", true, false,
        () -> ConsoleSnapshot.available("Console", "output", false));

    assertFalse(result.consoleContextRequested());
    assertEquals("@console explain", result.serverMessage());
  }

  @Test
  void processAddsUnavailableNoteInsteadOfFailing() {
    ProcessedMessage result = ConsoleContextPromptProcessor.process("@console explain", true, true,
        () -> ConsoleSnapshot.unavailable("No active console is selected."));

    assertTrue(result.consoleContextRequested());
    assertTrue(result.serverMessage().contains("Console context unavailable: No active console is selected."));
  }

  @Test
  void processAddsEmptyOutputNote() {
    ProcessedMessage result = ConsoleContextPromptProcessor.process("@console explain", true, true,
        () -> ConsoleSnapshot.available("Console", "", false));

    assertTrue(result.consoleContextRequested());
    assertTrue(result.serverMessage().contains("Output: Console output is empty."));
  }

  @Test
  void processHandlesConsoleCommandAloneWithNoPrompt() {
    ProcessedMessage result = ConsoleContextPromptProcessor.process("@console", true, true,
        () -> ConsoleSnapshot.available("Build", "BUILD FAILURE", false));

    assertTrue(result.consoleContextRequested());
    // After stripping @console, the prompt is empty, so the server message is just the context block
    assertTrue(result.serverMessage().contains("[Console Context]"));
    assertTrue(result.serverMessage().contains("Console: Build"));
    assertTrue(result.serverMessage().contains("BUILD FAILURE"));
    // No leading user prompt, just the context block
    assertTrue(result.serverMessage().startsWith("[Console Context]"));
  }
}
