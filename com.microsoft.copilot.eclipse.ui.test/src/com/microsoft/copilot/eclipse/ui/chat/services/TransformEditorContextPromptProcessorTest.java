// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.microsoft.copilot.eclipse.ui.chat.services.TransformEditorContextPromptProcessor.ProcessedMessage;
import com.microsoft.copilot.eclipse.ui.chat.services.TransformEditorContextService.TransformEditorSnapshot;
import com.microsoft.copilot.eclipse.ui.chat.services.TransformEditorContextService.TransformEditorSnapshot.ScriptEntry;
import com.microsoft.copilot.eclipse.ui.chat.services.TransformEditorContextService.TransformEditorSnapshot.TransformEntry;

class TransformEditorContextPromptProcessorTest {

  private static TransformEditorSnapshot singleTransformSnapshot() {
    ScriptEntry payloadScript = new ScriptEntry("payload", "application/json",
        "%dw 2.0\noutput application/json\n---\n{ id: payload.customerId }");
    TransformEntry entry = new TransformEntry("MyTransform [id=abc-123]", "MyTransform", "abc-123",
        List.of(payloadScript));
    return TransformEditorSnapshot.available("/project/src/main/mule/main.xml", 1, List.of(entry), false);
  }

  @Test
  void processAddsTransformContextWhenLeadingCommandIsEnabledAndSupported() {
    ProcessedMessage result = TransformEditorContextPromptProcessor.process(
        "@transform help me improve this mapping", true, true, () -> singleTransformSnapshot());

    assertTrue(result.transformContextRequested());
    assertTrue(result.serverMessage().startsWith("help me improve this mapping"));
    assertTrue(result.serverMessage().contains("[Transform Context]"));
    assertTrue(result.serverMessage().contains("file: /project/src/main/mule/main.xml"));
    assertTrue(result.serverMessage().contains("transforms: 1"));
    assertTrue(result.serverMessage().contains("--- Transform: MyTransform [id=abc-123] ---"));
    assertTrue(result.serverMessage().contains("target: payload"));
    assertTrue(result.serverMessage().contains("outputType: application/json"));
    assertTrue(result.serverMessage().contains("{ id: payload.customerId }"));
    assertFalse(result.serverMessage().contains("@transform"));
  }

  @Test
  void processOnlyConsumesLeadingTransformCommand() {
    AtomicBoolean supplierCalled = new AtomicBoolean(false);

    ProcessedMessage result = TransformEditorContextPromptProcessor.process(
        "please look at @transform output", true, true, () -> {
          supplierCalled.set(true);
          return singleTransformSnapshot();
        });

    assertFalse(result.transformContextRequested());
    assertEquals("please look at @transform output", result.serverMessage());
    assertFalse(supplierCalled.get());
  }

  @Test
  void processDoesNotMatchWhenCommandFollowedByNonWhitespace() {
    ProcessedMessage result = TransformEditorContextPromptProcessor.process(
        "@transform-mapper improve", true, true, () -> singleTransformSnapshot());

    assertFalse(result.transformContextRequested());
    assertEquals("@transform-mapper improve", result.serverMessage());
  }

  @Test
  void processLeavesMessageUnchangedWhenFeatureDisabled() {
    ProcessedMessage result = TransformEditorContextPromptProcessor.process(
        "@transform explain", false, true, () -> singleTransformSnapshot());

    assertFalse(result.transformContextRequested());
    assertEquals("@transform explain", result.serverMessage());
  }

  @Test
  void processLeavesMessageUnchangedWhenModeUnsupported() {
    ProcessedMessage result = TransformEditorContextPromptProcessor.process(
        "@transform explain", true, false, () -> singleTransformSnapshot());

    assertFalse(result.transformContextRequested());
    assertEquals("@transform explain", result.serverMessage());
  }

  @Test
  void processAddsUnavailableNoteInsteadOfFailing() {
    ProcessedMessage result = TransformEditorContextPromptProcessor.process(
        "@transform explain", true, true,
        () -> TransformEditorSnapshot.unavailable("No active Mule XML editor is open."));

    assertTrue(result.transformContextRequested());
    assertTrue(result.serverMessage()
        .contains("Transform context unavailable: No active Mule XML editor is open."));
  }

  @Test
  void processAddsEmptyTransformNote() {
    TransformEditorSnapshot emptySnapshot = TransformEditorSnapshot.available(
        "/project/src/main/mule/main.xml", 0, List.of(), false);
    ProcessedMessage result = TransformEditorContextPromptProcessor.process(
        "@transform explain", true, true, () -> emptySnapshot);

    assertTrue(result.transformContextRequested());
    assertTrue(result.serverMessage().contains("No ee:transform elements found in this file."));
  }

  @Test
  void processHandlesTransformCommandAloneWithNoPrompt() {
    ProcessedMessage result = TransformEditorContextPromptProcessor.process(
        "@transform", true, true, () -> singleTransformSnapshot());

    assertTrue(result.transformContextRequested());
    assertTrue(result.serverMessage().contains("[Transform Context]"));
    assertTrue(result.serverMessage().contains("MyTransform"));
    // No leading user prompt; context block starts the server message
    assertTrue(result.serverMessage().startsWith("[Transform Context]"));
  }

  @Test
  void processTruncatedFlagAppearsInOutput() {
    ScriptEntry payloadScript = new ScriptEntry("payload", "application/json", "...large script...");
    TransformEntry entry = new TransformEntry("T", "T", "t1", List.of(payloadScript));
    TransformEditorSnapshot truncatedSnapshot = TransformEditorSnapshot.available(
        "/project/src/main/mule/main.xml", 1, List.of(entry), true);

    ProcessedMessage result = TransformEditorContextPromptProcessor.process(
        "@transform check this", true, true, () -> truncatedSnapshot);

    assertTrue(result.transformContextRequested());
    assertTrue(result.serverMessage().contains("truncated due to length"));
  }

  @Test
  void processAutoInject_appendsContextWhenEnabledAndSnapshotAvailable() {
    ProcessedMessage result = TransformEditorContextPromptProcessor.processAutoInject(
        "explain the error", true, true, () -> singleTransformSnapshot());

    assertTrue(result.transformContextRequested());
    assertTrue(result.serverMessage().contains("explain the error"));
    assertTrue(result.serverMessage().contains("[Transform Context]"));
    assertTrue(result.serverMessage().contains("target: payload"));
    assertTrue(result.serverMessage().contains("@transform") == false);
  }

  @Test
  void processAutoInject_doesNotInjectWhenDisabled() {
    ProcessedMessage result = TransformEditorContextPromptProcessor.processAutoInject(
        "explain the error", false, true, () -> singleTransformSnapshot());

    assertFalse(result.transformContextRequested());
    assertEquals("explain the error", result.serverMessage());
  }

  @Test
  void processAutoInject_doesNotInjectWhenModeUnsupported() {
    ProcessedMessage result = TransformEditorContextPromptProcessor.processAutoInject(
        "explain the error", true, false, () -> singleTransformSnapshot());

    assertFalse(result.transformContextRequested());
    assertEquals("explain the error", result.serverMessage());
  }

  @Test
  void processAutoInject_doesNotInjectWhenSnapshotUnavailable() {
    ProcessedMessage result = TransformEditorContextPromptProcessor.processAutoInject(
        "explain the error", true, true,
        () -> TransformEditorSnapshot.unavailable("No active Mule XML editor is open."));

    assertFalse(result.transformContextRequested());
    assertEquals("explain the error", result.serverMessage());
    assertFalse(result.serverMessage().contains("unavailable"));
  }

  @Test
  void processAutoInject_doesNotInjectWhenSnapshotEmpty() {
    TransformEditorSnapshot emptySnapshot = TransformEditorSnapshot.available(
        "/project/src/main/mule/main.xml", 0, List.of(), false);

    ProcessedMessage result = TransformEditorContextPromptProcessor.processAutoInject(
        "explain the error", true, true, () -> emptySnapshot);

    assertFalse(result.transformContextRequested());
    assertEquals("explain the error", result.serverMessage());
  }
}
