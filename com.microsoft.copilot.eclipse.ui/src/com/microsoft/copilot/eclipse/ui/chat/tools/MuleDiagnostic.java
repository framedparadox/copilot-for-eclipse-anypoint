// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

/**
 * Finding emitted by MuleSoft analysis tools.
 */
final class MuleDiagnostic {
  private final String severity;
  private final String file;
  private final int line;
  private final String message;
  private final String recommendation;

  MuleDiagnostic(String severity, String file, int line, String message, String recommendation) {
    this.severity = severity;
    this.file = file;
    this.line = line;
    this.message = message;
    this.recommendation = recommendation;
  }

  static MuleDiagnostic info(String file, String message, String recommendation) {
    return new MuleDiagnostic("info", file, 0, message, recommendation);
  }

  static MuleDiagnostic low(String file, int line, String message, String recommendation) {
    return new MuleDiagnostic("low", file, line, message, recommendation);
  }

  static MuleDiagnostic medium(String file, int line, String message, String recommendation) {
    return new MuleDiagnostic("medium", file, line, message, recommendation);
  }

  static MuleDiagnostic high(String file, int line, String message, String recommendation) {
    return new MuleDiagnostic("high", file, line, message, recommendation);
  }

  static MuleDiagnostic critical(String file, int line, String message, String recommendation) {
    return new MuleDiagnostic("critical", file, line, message, recommendation);
  }
}
