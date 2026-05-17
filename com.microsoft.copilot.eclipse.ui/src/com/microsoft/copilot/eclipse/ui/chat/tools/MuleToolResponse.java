// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Structured response shape for MuleSoft agent tools.
 */
final class MuleToolResponse {
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

  private final String status;
  private final String summary;
  private final List<String> artifacts = new ArrayList<>();
  private final List<MuleDiagnostic> diagnostics = new ArrayList<>();
  private final List<String> patches = new ArrayList<>();
  private final List<String> nextActions = new ArrayList<>();

  MuleToolResponse(String status, String summary) {
    this.status = status;
    this.summary = summary;
  }

  void addArtifact(String artifact) {
    if (artifact != null && !artifact.isBlank()) {
      artifacts.add(artifact);
    }
  }

  void addDiagnostic(MuleDiagnostic diagnostic) {
    if (diagnostic != null) {
      diagnostics.add(diagnostic);
    }
  }

  void addDiagnostics(List<MuleDiagnostic> values) {
    if (values != null) {
      values.forEach(this::addDiagnostic);
    }
  }

  void addNextAction(String nextAction) {
    if (nextAction != null && !nextAction.isBlank()) {
      nextActions.add(nextAction);
    }
  }

  String toJson() {
    return GSON.toJson(this);
  }
}
