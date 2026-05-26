// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static analysis helper for DataWeave 2.0 scripts. Detects common performance anti-patterns,
 * null-safety gaps, and documentation gaps.
 */
final class DwlAnalyzer {

  private static final Pattern NESTED_MAP_FILTER =
      Pattern.compile("map\\s*\\([^)]*->\\s*[^)]*filter\\b|filter\\s*\\([^)]*->\\s*[^)]*map\\b",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern INLINE_REGEX_IN_MAP =
      Pattern.compile("(?:map|filter)\\s*[({][^)}]*\\/[^/\\n]+\\/[^)}]*[)}]",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern ROUND_TRIP_WRITE_READ =
      Pattern.compile("write\\s*\\([^)]*read\\s*\\(|read\\s*\\([^)]*write\\s*\\(",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern FIELD_ACCESS_WITHOUT_DEFAULT =
      Pattern.compile("payload\\.\\w+(?!\\s+default\\b)", Pattern.CASE_INSENSITIVE);
  private static final Pattern FUN_DECL =
      Pattern.compile("^(\\s*)fun\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
  private static final Pattern COMMENT_BEFORE_LINE =
      Pattern.compile("//[^\\n]*\\n\\s*fun\\s+|/\\*\\*[\\s\\S]*?\\*/\\s*fun\\s+",
          Pattern.CASE_INSENSITIVE);

  record Issue(String type, int line, String description, String suggestion) {
  }

  private DwlAnalyzer() {
  }

  /**
   * Analyzes a DataWeave script for common issues.
   *
   * @param script the full script text
   * @return list of issues found (may be empty)
   */
  static List<Issue> analyze(String script) {
    List<Issue> issues = new ArrayList<>();
    if (script == null || script.isBlank()) {
      return issues;
    }

    String[] lines = script.split("\\r?\\n", -1);

    // Check 1: Missing %dw 2.0 header
    if (!script.stripLeading().startsWith("%dw 2.0")) {
      issues.add(new Issue("missing-dw-header", 1,
          "Script is missing the '%dw 2.0' header directive.",
          "%dw 2.0\noutput application/json\n---\n" + script.stripLeading()));
    }

    // Check 2: Missing output directive
    boolean hasOutput = false;
    for (String line : lines) {
      if (line.trim().startsWith("output ")) {
        hasOutput = true;
        break;
      }
    }
    if (!hasOutput) {
      issues.add(new Issue("missing-output-directive", 1,
          "No 'output' directive found. Add 'output application/json' (or the appropriate type) after the %dw 2.0 header.",
          "output application/json"));
    }

    // Check 3: Nested map+filter (O(n×m) pattern)
    if (NESTED_MAP_FILTER.matcher(script).find()) {
      int lineNum = findPatternLine(NESTED_MAP_FILTER, script);
      issues.add(new Issue("nested-map-filter", lineNum,
          "Nested map+filter detected — this is O(n×m). Pre-index the inner array with 'groupBy' and look up in O(1).",
          "var indexedB = arrayB groupBy $.id\narrayA map (a -> indexedB[a.id][0] default {})"));
    }

    // Check 4: Inline regex inside map/filter
    if (INLINE_REGEX_IN_MAP.matcher(script).find()) {
      int lineNum = findPatternLine(INLINE_REGEX_IN_MAP, script);
      issues.add(new Issue("inline-regex-in-map", lineNum,
          "Regex literal inside map/filter is compiled on every iteration. Extract to a 'var' before the map.",
          "var namePattern = /^[A-Z].*/\npayload map (item -> item.name matches namePattern)"));
    }

    // Check 5: Round-trip serialization (write then read, or read then write)
    if (ROUND_TRIP_WRITE_READ.matcher(script).find()) {
      int lineNum = findPatternLine(ROUND_TRIP_WRITE_READ, script);
      issues.add(new Issue("round-trip-serialization", lineNum,
          "write() immediately followed by read() (or vice versa) is a no-op round-trip. Remove both calls.",
          "// Remove the write(...) and read(...) pair — pass the value directly"));
    }

    // Check 6: Field access without null guard (heuristic — flags bare payload.field patterns)
    Matcher nullMatcher = FIELD_ACCESS_WITHOUT_DEFAULT.matcher(script);
    int nullIssues = 0;
    while (nullMatcher.find() && nullIssues < 3) {
      int lineNum = lineNumberAt(script, nullMatcher.start());
      String access = nullMatcher.group().trim();
      issues.add(new Issue("missing-null-guard", lineNum,
          "'" + access + "' accessed without a 'default' guard. If this field is optional, add 'default'.",
          access + " default \"\""));
      nullIssues++;
    }

    // Check 7: Undocumented fun declarations
    Matcher funMatcher = FUN_DECL.matcher(script);
    while (funMatcher.find()) {
      int lineNum = lineNumberAt(script, funMatcher.start());
      String funName = funMatcher.group(2);
      boolean hasComment = hasCommentBefore(script, funMatcher.start());
      if (!hasComment) {
        issues.add(new Issue("undocumented-function", lineNum,
            "Function '" + funName + "' has no documentation comment.",
            "// " + funName + ": describe what this function does, its parameters and return type"));
      }
    }

    return issues;
  }

  /**
   * Adds documentation comments before undocumented {@code fun} declarations in the script.
   *
   * @param script the original DataWeave script
   * @return the script with comment stubs inserted
   */
  static String addComments(String script) {
    if (script == null || script.isBlank()) {
      return script;
    }
    StringBuilder result = new StringBuilder();
    String[] lines = script.split("\\r?\\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();
      if (trimmed.startsWith("fun ")) {
        boolean alreadyCommented = (i > 0 && lines[i - 1].trim().startsWith("//"))
            || (i > 0 && lines[i - 1].trim().startsWith("*"))
            || (i > 0 && lines[i - 1].trim().startsWith("/**"));
        if (!alreadyCommented) {
          String indent = line.substring(0, line.length() - line.stripLeading().length());
          String funName = trimmed.replaceFirst("fun\\s+(\\w+).*", "$1");
          result.append(indent).append("// ").append(funName)
              .append(": describe what this function does, its parameters and return type")
              .append(System.lineSeparator());
        }
      }
      result.append(line);
      if (i < lines.length - 1) {
        result.append(System.lineSeparator());
      }
    }
    return result.toString();
  }

  private static int findPatternLine(Pattern pattern, String script) {
    Matcher m = pattern.matcher(script);
    if (m.find()) {
      return lineNumberAt(script, m.start());
    }
    return 0;
  }

  private static int lineNumberAt(String script, int charIndex) {
    int line = 1;
    for (int i = 0; i < charIndex && i < script.length(); i++) {
      if (script.charAt(i) == '\n') {
        line++;
      }
    }
    return line;
  }

  private static boolean hasCommentBefore(String script, int funStart) {
    int lineStart = script.lastIndexOf('\n', funStart - 1);
    if (lineStart < 0) {
      return false;
    }
    String prevLine = script.substring(script.lastIndexOf('\n', lineStart - 1) + 1, lineStart).trim();
    return prevLine.startsWith("//") || prevLine.startsWith("*") || prevLine.startsWith("/**");
  }
}
