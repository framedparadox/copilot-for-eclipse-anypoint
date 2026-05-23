// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.services;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.TextConsole;

import com.microsoft.copilot.eclipse.core.CopilotCore;

/**
 * Captures bounded output from the active Eclipse console for chat context.
 */
public class ConsoleContextService {
  public static final int DEFAULT_MAX_CHARS = 12_000;

  /**
   * Captures output from the console currently selected in the Eclipse Console view.
   *
   * @return a console snapshot, or an unavailable snapshot when no active text console can be read
   */
  public ConsoleSnapshot captureActiveConsole() {
    IConsole activeConsole = getActiveConsole();
    return captureConsole(activeConsole, DEFAULT_MAX_CHARS);
  }

  /**
   * Captures a bounded tail from the given console.
   *
   * @param console the console to read
   * @param maxChars maximum number of characters to include
   * @return a console snapshot
   */
  public ConsoleSnapshot captureConsole(IConsole console, int maxChars) {
    if (console == null) {
      return ConsoleSnapshot.unavailable("No active console is selected.");
    }
    if (!(console instanceof TextConsole textConsole)) {
      return ConsoleSnapshot.unavailable("The active console is not text-backed.");
    }

    IDocument document = textConsole.getDocument();
    if (document == null) {
      return ConsoleSnapshot.unavailable("The active console has no readable document.");
    }

    String output = document.get();
    if (output == null) {
      output = StringUtils.EMPTY;
    }

    return ConsoleSnapshot.available(console.getName(), tailAtLineBoundary(output, maxChars),
        output.length() > maxChars);
  }

  private IConsole getActiveConsole() {
    try {
      // Note: PlatformUI.getWorkbench() and workbench page methods must be called on the SWT UI thread.
      // This method is safe when called from onSendInternal() in ChatView, which runs on the UI dispatch thread.
      // If called from a background thread (e.g., job), it will throw SWTException: invalid thread access.
      IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
      if (window == null) {
        return null;
      }
      IWorkbenchPage page = window.getActivePage();
      if (page == null) {
        return null;
      }
      if (page.findView(IConsoleConstants.ID_CONSOLE_VIEW) instanceof IConsoleView consoleView) {
        IConsole active = consoleView.getConsole();
        if (active != null) {
          return active;
        }
      }
    } catch (Exception e) {
      CopilotCore.LOGGER.error("Failed to capture active console context", e);
    }
    return findPreferredMuleConsole();
  }

  /**
   * Searches all registered Eclipse consoles and returns the best match for Mule-related output.
   * Priority order: Mule runtime console > MUnit console > Maven/mvn console.
   *
   * @return the preferred console, or null if none found
   */
  IConsole findPreferredMuleConsole() {
    try {
      IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
      if (manager == null) {
        return null;
      }
      IConsole muleConsole = null;
      IConsole munitConsole = null;
      IConsole mavenConsole = null;
      for (IConsole console : manager.getConsoles()) {
        String name = console.getName().toLowerCase();
        if (name.contains("munit")) {
          if (munitConsole == null) {
            munitConsole = console;
          }
        } else if (name.contains("mule")) {
          if (muleConsole == null) {
            muleConsole = console;
          }
        } else if (name.contains("maven") || name.contains("mvn")) {
          if (mavenConsole == null) {
            mavenConsole = console;
          }
        }
      }
      if (muleConsole != null) {
        return muleConsole;
      }
      if (munitConsole != null) {
        return munitConsole;
      }
      return mavenConsole;
    } catch (Exception e) {
      CopilotCore.LOGGER.error("Failed to find preferred Mule console", e);
      return null;
    }
  }

  private String tailAtLineBoundary(String output, int maxChars) {
    // When maxChars <= 0, return full output (guards against nonsensical limits gracefully).
    // Otherwise, trim from the end to stay within maxChars and align to line boundaries.
    if (maxChars <= 0 || output.length() <= maxChars) {
      return output;
    }

    int start = output.length() - maxChars;
    int nextLineBreak = output.indexOf('\n', start);
    if (nextLineBreak >= 0 && nextLineBreak + 1 < output.length()) {
      return output.substring(nextLineBreak + 1);
    }

    return output.substring(start);
  }

  /**
   * Snapshot of console context for a chat turn.
   */
  public record ConsoleSnapshot(String consoleName, String output, boolean truncated, String unavailableReason) {
    public static ConsoleSnapshot available(String consoleName, String output, boolean truncated) {
      return new ConsoleSnapshot(StringUtils.defaultIfBlank(consoleName, "Console"),
          StringUtils.defaultString(output), truncated, null);
    }

    public static ConsoleSnapshot unavailable(String reason) {
      return new ConsoleSnapshot(null, StringUtils.EMPTY, false, reason);
    }

    public boolean isAvailable() {
      return unavailableReason == null;
    }

    public boolean isEmpty() {
      return StringUtils.isBlank(output);
    }
  }
}
