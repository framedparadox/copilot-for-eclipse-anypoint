// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Maven build output from the Eclipse console and prepends a structured summary block.
 * This surfaces the build result, error count, and warning count before the full log dump,
 * reducing noise in the context window.
 */
final class MavenConsoleParser {

  private static final Pattern MAVEN_MARKER =
      Pattern.compile("\\[(INFO|WARNING|ERROR|WARN)\\]|BUILD (SUCCESS|FAILURE)", Pattern.CASE_INSENSITIVE);
  private static final Pattern BUILD_RESULT_PATTERN =
      Pattern.compile("BUILD (SUCCESS|FAILURE)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ERROR_LINE_PATTERN =
      Pattern.compile("^\\[ERROR\\]", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  private static final Pattern WARNING_LINE_PATTERN =
      Pattern.compile("^\\[WARNING\\]", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

  private MavenConsoleParser() {
  }

  /**
   * Inspects raw console output. If Maven build output is detected, prepends a structured
   * [Maven Build Summary] block so the model can orient to the build result before reading the
   * full log.
   *
   * @param rawOutput the raw console snapshot text
   * @return enriched text with summary prepended, or the original text if no Maven output found
   */
  static String enrich(String rawOutput) {
    if (rawOutput == null || rawOutput.isBlank() || !isMavenOutput(rawOutput)) {
      return rawOutput;
    }

    StringBuilder summary = new StringBuilder("[Maven Build Summary]\n");

    Matcher resultMatcher = BUILD_RESULT_PATTERN.matcher(rawOutput);
    if (resultMatcher.find()) {
      summary.append("Result: ").append(resultMatcher.group(0)).append('\n');
    }

    summary.append("Errors: ").append(countMatches(ERROR_LINE_PATTERN, rawOutput)).append('\n');
    summary.append("Warnings: ").append(countMatches(WARNING_LINE_PATTERN, rawOutput)).append('\n');
    summary.append('\n');

    return summary + rawOutput;
  }

  private static boolean isMavenOutput(String text) {
    return MAVEN_MARKER.matcher(text).find();
  }

  private static int countMatches(Pattern pattern, String text) {
    int count = 0;
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}
