// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Mule 4 runtime exception output from the Anypoint Studio console and prepends a structured
 * summary block. This reduces noise in the context window by surfacing the most actionable fields —
 * error type, flow name, root cause, and component location — before the raw console dump.
 */
final class MuleConsoleParser {

  private static final Pattern ERROR_TYPE_PATTERN =
      Pattern.compile("error type:\\s*([A-Z][A-Z0-9_]*:[A-Z][A-Z0-9_]*)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ROOT_CAUSE_PATTERN =
      Pattern.compile("(?:Caused by|root cause):\\s*(.{1,200})", Pattern.CASE_INSENSITIVE);
  private static final Pattern COMPONENT_PATTERN =
      Pattern.compile("at ([\\w\\-]+)\\s+@\\s+([\\w\\-./]+/processors/\\d+(?:/\\d+)?)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern FLOW_PATTERN =
      Pattern.compile("(?:Flow name:\\s*|at flow:\\s*|\\bflow=)([\\w\\-.:]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern MULE_EXCEPTION_MARKER =
      Pattern.compile("org\\.mule\\.runtime|MuleRuntimeException|MuleException");

  private MuleConsoleParser() {
  }

  /**
   * Inspects raw console output. If a Mule runtime exception is detected, prepends a structured
   * [Mule Error Summary] block so the model can orient to the error before reading the full dump.
   *
   * @param rawOutput the raw console snapshot text
   * @return enriched text with summary prepended, or the original text if no Mule exception found
   */
  static String enrich(String rawOutput) {
    if (rawOutput == null || rawOutput.isBlank() || !isMuleException(rawOutput)) {
      return rawOutput;
    }

    StringBuilder summary = new StringBuilder("[Mule Error Summary]\n");

    String errorType = extractFirst(ERROR_TYPE_PATTERN, rawOutput, 1);
    if (!errorType.isBlank()) {
      summary.append("Error type: ").append(errorType).append('\n');
    }

    String flow = extractFirst(FLOW_PATTERN, rawOutput, 1);
    if (!flow.isBlank()) {
      summary.append("Flow: ").append(flow).append('\n');
    }

    String rootCause = extractFirst(ROOT_CAUSE_PATTERN, rawOutput, 1);
    if (!rootCause.isBlank()) {
      summary.append("Root cause: ").append(rootCause.trim()).append('\n');
    }

    String component = extractComponent(rawOutput);
    if (!component.isBlank()) {
      summary.append("Component: ").append(component).append('\n');
    }

    summary.append('\n');
    return summary + rawOutput;
  }

  private static boolean isMuleException(String text) {
    return MULE_EXCEPTION_MARKER.matcher(text).find();
  }

  private static String extractFirst(Pattern pattern, String text, int group) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? matcher.group(group).trim() : "";
  }

  private static String extractComponent(String text) {
    Matcher matcher = COMPONENT_PATTERN.matcher(text);
    return matcher.find() ? matcher.group(1).trim() + " @ " + matcher.group(2).trim() : "";
  }
}
