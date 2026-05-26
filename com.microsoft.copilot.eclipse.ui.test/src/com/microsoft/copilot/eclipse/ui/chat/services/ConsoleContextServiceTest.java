// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.junit.jupiter.api.Test;

import com.microsoft.copilot.eclipse.ui.chat.services.ConsoleContextService.ConsoleSnapshot;

class ConsoleContextServiceTest {

  @Test
  void captureConsoleReturnsTextConsoleOutput() {
    MessageConsole console = new MessageConsole("Build", null);
    console.getDocument().set("line 1\nline 2");

    ConsoleSnapshot snapshot = new ConsoleContextService().captureConsole(console,
        ConsoleContextService.DEFAULT_MAX_CHARS);

    assertTrue(snapshot.isAvailable());
    assertEquals("Build", snapshot.consoleName());
    assertEquals("line 1\nline 2", snapshot.output());
    assertFalse(snapshot.truncated());
  }

  @Test
  void captureConsoleReturnsEmptySnapshotForEmptyTextConsole() {
    MessageConsole console = new MessageConsole("Empty", null);
    console.getDocument().set("");

    ConsoleSnapshot snapshot = new ConsoleContextService().captureConsole(console,
        ConsoleContextService.DEFAULT_MAX_CHARS);

    assertTrue(snapshot.isAvailable());
    assertTrue(snapshot.isEmpty());
    assertFalse(snapshot.truncated());
  }

  @Test
  void captureConsoleTruncatesAtLineBoundary() {
    MessageConsole console = new MessageConsole("Long", null);
    console.getDocument().set("line 1\nline 2\nline 3");

    ConsoleSnapshot snapshot = new ConsoleContextService().captureConsole(console, 10);

    assertTrue(snapshot.isAvailable());
    assertEquals("line 3", snapshot.output());
    assertTrue(snapshot.truncated());
  }

  @Test
  void captureConsoleReturnsUnavailableForMissingConsole() {
    ConsoleSnapshot snapshot = new ConsoleContextService().captureConsole(null,
        ConsoleContextService.DEFAULT_MAX_CHARS);

    assertFalse(snapshot.isAvailable());
    assertTrue(snapshot.unavailableReason().contains("No active console"));
  }

  @Test
  void captureConsoleReturnsUnavailableForNonTextConsole() {
    ConsoleSnapshot snapshot = new ConsoleContextService().captureConsole(mock(IConsole.class),
        ConsoleContextService.DEFAULT_MAX_CHARS);

    assertFalse(snapshot.isAvailable());
    assertTrue(snapshot.unavailableReason().contains("not text-backed"));
  }
}
