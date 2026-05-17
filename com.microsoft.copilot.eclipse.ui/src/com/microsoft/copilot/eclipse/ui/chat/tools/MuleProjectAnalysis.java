// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory summary of a Mule project.
 */
final class MuleProjectAnalysis {
  final Path projectPath;
  final List<String> muleXmlFiles = new ArrayList<>();
  final List<String> resourceFiles = new ArrayList<>();
  final List<String> apiSpecFiles = new ArrayList<>();
  final List<String> munitFiles = new ArrayList<>();
  final Set<String> flows = new LinkedHashSet<>();
  final Set<String> subFlows = new LinkedHashSet<>();
  final Set<String> globalConfigs = new LinkedHashSet<>();
  final Set<String> namespaces = new LinkedHashSet<>();
  final Set<String> placeholders = new LinkedHashSet<>();
  final Set<String> connectorDependencies = new LinkedHashSet<>();
  final Set<String> deploymentPlugins = new LinkedHashSet<>();
  final Map<String, Integer> processorCounts = new LinkedHashMap<>();
  final List<MuleDiagnostic> diagnostics = new ArrayList<>();
  String muleRuntimeVersion = "";
  boolean hasPom;
  boolean hasMuleArtifact;
  boolean hasApikit;
  boolean hasSecureProperties;

  MuleProjectAnalysis(Path projectPath) {
    this.projectPath = projectPath;
  }
}
