// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MavenConsoleParserTest {

  @Test
  void enrich_returnsOriginalWhenNoMavenOutput() {
    String input = "some generic console output\nno maven markers here";
    assertEquals(input, MavenConsoleParser.enrich(input));
  }

  @Test
  void enrich_returnsNullForNullInput() {
    assertNull(MavenConsoleParser.enrich(null));
  }

  @Test
  void enrich_returnsBlankForBlankInput() {
    assertEquals("   ", MavenConsoleParser.enrich("   "));
  }

  @Test
  void enrich_prependsSummaryForBuildSuccess() {
    String input = "[INFO] Scanning for projects...\n[INFO] BUILD SUCCESS\n[INFO] Total time: 2.5 s";

    String result = MavenConsoleParser.enrich(input);

    assertTrue(result.startsWith("[Maven Build Summary]"));
    assertTrue(result.contains("Result: BUILD SUCCESS"));
    assertTrue(result.contains("Errors: 0"));
    assertTrue(result.contains(input));
  }

  @Test
  void enrich_prependsSummaryForBuildFailure() {
    String input = "[INFO] Scanning for projects...\n[ERROR] Compilation failure\n[INFO] BUILD FAILURE";

    String result = MavenConsoleParser.enrich(input);

    assertTrue(result.startsWith("[Maven Build Summary]"));
    assertTrue(result.contains("Result: BUILD FAILURE"));
    assertTrue(result.contains("Errors: 1"));
    assertTrue(result.contains(input));
  }

  @Test
  void enrich_countsMultipleErrorsAndWarnings() {
    String input = "[ERROR] src/Foo.java:10: error\n"
        + "[ERROR] src/Bar.java:20: error\n"
        + "[WARNING] deprecated API\n"
        + "[WARNING] unchecked cast\n"
        + "[WARNING] unused import\n"
        + "[INFO] BUILD FAILURE";

    String result = MavenConsoleParser.enrich(input);

    assertTrue(result.contains("Errors: 2"));
    assertTrue(result.contains("Warnings: 3"));
  }

  @Test
  void enrich_isCaseInsensitiveForMarkers() {
    String input = "[info] Scanning...\n[error] Compilation failure\nbuild failure";

    String result = MavenConsoleParser.enrich(input);

    assertTrue(result.startsWith("[Maven Build Summary]"));
    assertTrue(result.contains("Errors: 1"));
  }

  @Test
  void enrich_doesNotDoubleWrapAlreadyEnrichedOutput() {
    String input = "[INFO] BUILD SUCCESS";
    String enrichedOnce = MavenConsoleParser.enrich(input);

    assertTrue(enrichedOnce.startsWith("[Maven Build Summary]"));
    assertFalse(enrichedOnce.substring(1).contains("[Maven Build Summary]"));
  }

  @Test
  void enrich_includesRawOutputAfterSummary() {
    String input = "[INFO] BUILD SUCCESS\n[INFO] Total time: 1 s";

    String result = MavenConsoleParser.enrich(input);

    assertTrue(result.contains(input));
    int summaryEnd = result.indexOf(input);
    assertTrue(result.substring(0, summaryEnd).contains("[Maven Build Summary]"));
  }
}
