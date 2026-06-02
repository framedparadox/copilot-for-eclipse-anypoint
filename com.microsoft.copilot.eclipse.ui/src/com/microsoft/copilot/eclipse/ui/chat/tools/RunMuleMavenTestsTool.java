// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;

import com.microsoft.copilot.eclipse.core.lsp.protocol.ConfirmationMessages;
import com.microsoft.copilot.eclipse.core.lsp.protocol.InputSchema;
import com.microsoft.copilot.eclipse.core.lsp.protocol.InputSchemaPropertyValue;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolResult.ToolInvocationStatus;
import com.microsoft.copilot.eclipse.ui.chat.ChatView;

/**
 * Runs Maven validation for Mule projects.
 */
public class RunMuleMavenTestsTool extends BaseTool {
  private static final String TOOL_NAME = "run_mule_maven_tests";
  private static final String PROJECT_PATH = "projectPath";
  private static final String GOALS = "goals";
  private static final String MAX_OUTPUT_CHARS = "maxOutputChars";
  private static final String MAVEN_PROFILE = "mavenProfile";
  private static final int DEFAULT_MAX_OUTPUT_CHARS = 12000;
  private static final Duration TIMEOUT = Duration.ofMinutes(10);

  /**
   * Creates a Mule Maven validation tool.
   */
  public RunMuleMavenTestsTool() {
    this.name = TOOL_NAME;
  }

  @Override
  public LanguageModelToolInformation getToolInformation() {
    LanguageModelToolInformation toolInfo = super.getToolInformation();
    toolInfo.setName(TOOL_NAME);
    toolInfo.setDisplayDescription("Run Maven or MUnit validation for a Mule project");
    toolInfo.setDescription("""
        Run Maven validation for a MuleSoft project and return command output.
        Use this after generating or modifying Mule XML, DataWeave, RAML/OpenAPI, or MUnit tests.
        Default goal is "test" which runs the full Maven test lifecycle including MUnit suites.
        MUnit-specific flags: pass "-Dmunit.test=<suite-name>.xml" in goals to run a single suite,
        or "-DskipMunitTests=false" to force MUnit execution when tests are skipped by profile.
        Multi-module projects: add "-pl <module-name>" to the goals array to target a specific module.
        Use mavenProfile to activate an environment-specific Maven profile (e.g., "dev", "test").
        """);
    InputSchema inputSchema = new InputSchema();
    inputSchema.setType("object");
    Map<String, InputSchemaPropertyValue> properties = new LinkedHashMap<>();
    properties.put(PROJECT_PATH, new InputSchemaPropertyValue("string", "Absolute path to the Mule project folder"));
    InputSchemaPropertyValue goals = new InputSchemaPropertyValue("array",
        "Maven goals and arguments, e.g. [\"test\"] or [\"-Dmunit.test=mySuite.xml\", \"test\"]");
    goals.setItems(new InputSchemaPropertyValue("string", "A Maven goal or argument flag"));
    properties.put(GOALS, goals);
    properties.put(MAVEN_PROFILE,
        new InputSchemaPropertyValue("string", "Optional Maven profile to activate with -P, e.g. dev or test"));
    properties.put(MAX_OUTPUT_CHARS, new InputSchemaPropertyValue("number", "Maximum output characters to return"));
    inputSchema.setProperties(properties);
    inputSchema.setRequired(List.of(PROJECT_PATH));
    toolInfo.setInputSchema(inputSchema);
    return toolInfo;
  }

  @Override
  public boolean needConfirmation() {
    return true;
  }

  @Override
  public ConfirmationMessages getConfirmationMessages() {
    return new ConfirmationMessages("Run Mule Maven validation",
        "Copilot wants to run Maven in a Mule project. Review the project path and goals before continuing.");
  }

  @Override
  public CompletableFuture<LanguageModelToolResult[]> invoke(Map<String, Object> input, ChatView chatView) {
    return CompletableFuture.supplyAsync(() -> {
      LanguageModelToolResult result = new LanguageModelToolResult();
      try {
        Path projectPath = getProjectPath(input.get(PROJECT_PATH));
        if (projectPath == null) {
          result.setStatus(ToolInvocationStatus.error);
          result.addContent("projectPath must be an absolute path to an existing Mule project folder.");
          return new LanguageModelToolResult[] { result };
        }

        List<String> command = buildCommand(projectPath, input.get(GOALS), input.get(MAVEN_PROFILE));
        ProcessResult processResult = run(projectPath, command, getMaxOutputChars(input.get(MAX_OUTPUT_CHARS)));
        result.setStatus(processResult.exitCode == 0 ? ToolInvocationStatus.success : ToolInvocationStatus.error);
        result.addContent(processResult.render(command));
      } catch (Exception e) {
        result.setStatus(ToolInvocationStatus.error);
        result.addContent("Failed to run Mule Maven validation: " + e.getMessage());
      }
      return new LanguageModelToolResult[] { result };
    });
  }

  private ProcessResult run(Path projectPath, List<String> command, int maxOutputChars) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(projectPath.toFile());
    builder.redirectErrorStream(true);
    Process process = builder.start();
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (output.length() < maxOutputChars) {
          output.append(line).append(System.lineSeparator());
        }
      }
    }
    if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
      process.destroyForcibly();
      return new ProcessResult(-1, "Timed out after " + TIMEOUT.toMinutes() + " minutes.");
    }
    return new ProcessResult(process.exitValue(), output.toString());
  }

  private List<String> buildCommand(Path projectPath, Object goalsInput, Object profileInput) {
    List<String> command = new ArrayList<>();
    command.add(findMavenExecutable(projectPath));
    List<String> goals = parseGoals(goalsInput);
    command.addAll(goals.isEmpty() ? List.of("test") : goals);
    if (profileInput instanceof String profile && !profile.isBlank()) {
      command.add("-P");
      command.add(profile.trim());
    }
    return command;
  }

  private List<String> parseGoals(Object goalsInput) {
    if (goalsInput instanceof List<?> values && values.stream().allMatch(String.class::isInstance)) {
      return values.stream().map(String.class::cast).filter(value -> !value.isBlank()).toList();
    }
    if (goalsInput instanceof String value && !value.isBlank()) {
      return Arrays.asList(value.trim().split("\\s+"));
    }
    return List.of();
  }

  private String findMavenExecutable(Path projectPath) {
    boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
    Path wrapper = projectPath.resolve(windows ? "mvnw.cmd" : "mvnw");
    if (Files.isRegularFile(wrapper)) {
      return wrapper.toFile().getAbsolutePath();
    }
    return windows ? "mvn.cmd" : "mvn";
  }

  private int getMaxOutputChars(Object value) {
    if (value instanceof Number number) {
      return Math.max(1000, number.intValue());
    }
    return DEFAULT_MAX_OUTPUT_CHARS;
  }

  @Nullable
  private static Path getProjectPath(Object value) {
    if (!(value instanceof String pathString) || pathString.isBlank()) {
      return null;
    }
    Path path = Path.of(pathString).toAbsolutePath().normalize();
    return Files.isDirectory(path) ? path : null;
  }

  private record ProcessResult(int exitCode, String output) {
    private String render(List<String> command) {
      return "Command: " + String.join(" ", command) + System.lineSeparator() + "Exit code: " + exitCode
          + System.lineSeparator() + output;
    }
  }
}
