// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.preferences;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.microsoft.copilot.eclipse.core.CopilotCore;
import com.microsoft.copilot.eclipse.ui.swt.CssConstants;
import com.microsoft.copilot.eclipse.ui.utils.UiUtils;

/**
 * Utility class for Copilot preference pages.
 */
public final class PreferencePageUtils {
  private static final String TEXT_INPUT_CSS_CLASS = "copilot-preference-text-input";

  /**
   * Target content height, in pixels, for Copilot preference pages. JFace grows the shared Preferences dialog to
   * the tallest page's preferred height and never shrinks it, so each page keeps its scrollable content within
   * this height to hold the dialog at a stable size while the user navigates. Pages enforce it differently:
   * {@code McpPreferencePage} divides it across two stacked groups; {@code AutoApprovePreferencePage} caps its
   * {@code ScrolledComposite} at this height.
   */
  public static final int STANDARD_CONTENT_HEIGHT = 520;

  // Private constructor to prevent instantiation
  private PreferencePageUtils() {
  }

  /**
   * Creates an external link that opens the given URL in the system browser.
   *
   * @param composite the parent composite
   * @param label the link label (can contain <a/> tags)
   * @param tooltip the tooltip text
   */
  public static void createExternalLink(Composite composite, String label, String tooltip) {
    createLink(composite, label, tooltip, PreferencePageUtils::openUrlInBrowser);
  }

  /**
   * Creates a link that opens the given preference page.
   *
   * @param shell the parent shell
   * @param composite the parent composite
   * @param label the label
   * @param tooltip the tooltip
   * @param preferenceId the preference page ID
   */
  public static void createPreferenceLink(Shell shell, Composite composite, String label, String tooltip,
      String preferenceId) {
    createLink(composite, label, tooltip, event -> openPreferencePage(shell, preferenceId, event));
  }

  /**
   * Creates a link with common setup and custom selection behavior.
   *
   * @param composite the parent composite
   * @param label the link label
   * @param tooltip the tooltip text
   * @param selectionHandler the selection event handler
   */
  private static void createLink(Composite composite, String label, String tooltip,
      Consumer<SelectionEvent> selectionHandler) {
    final Link link = new Link(composite, SWT.NONE);
    link.setText(label);
    link.setToolTipText(tooltip);
    link.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1));
    inheritParentBackground(link);
    link.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        selectionHandler.accept(e);
      }
    });
  }

  /**
   * Opens a URL in the system browser.
   *
   * @param event the selection event containing the URL
   */
  private static void openUrlInBrowser(SelectionEvent event) {
    try {
      PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(event.text));
    } catch (PartInitException | MalformedURLException e) {
      CopilotCore.LOGGER.error("Failed to open URL: " + event.text, e);
    }
  }

  /**
   * Opens a preference page.
   *
   * @param shell the parent shell
   * @param preferenceId the preference page ID
   * @param event the selection event
   */
  private static void openPreferencePage(Shell shell, String preferenceId, SelectionEvent event) {
    PreferencesUtil.createPreferenceDialogOn(shell, preferenceId, null, event);
  }

  /**
   * Applies a control's parent background to layout-only preference controls and their children.
   *
   * @param control the control whose background should match its parent
   */
  public static void inheritParentBackground(Control control) {
    if (control == null || control.isDisposed() || control.getParent() == null
        || control.getParent().isDisposed()) {
      return;
    }

    applyBackground(control, control.getParent().getBackground());
  }

  /**
   * Applies readable dark-mode colors to editable text inputs in Copilot preference surfaces.
   *
   * @param text the text input to style
   */
  public static void styleTextInput(Text text) {
    if (text == null || text.isDisposed() || !UiUtils.isDarkTheme()) {
      return;
    }

    appendCssClass(text, TEXT_INPUT_CSS_CLASS);
    Color background = CssConstants.getChatBackgroundColor(text.getDisplay());
    Color foreground = CssConstants.getChatForegroundColor(text.getDisplay());
    applyTextInputColors(text, background, foreground);
    text.getDisplay().asyncExec(() -> applyTextInputColors(text, background, foreground));
    text.addListener(SWT.Settings, e -> applyTextInputColors(text, background, foreground));
    text.addListener(SWT.FocusIn, e -> applyTextInputColors(text, background, foreground));
    text.addListener(SWT.FocusOut, e -> applyTextInputColors(text, background, foreground));
    text.addListener(SWT.Modify, e -> applyTextInputColors(text, background, foreground));
    text.addDisposeListener(e -> {
      disposeColor(background);
      disposeColor(foreground);
    });
  }

  private static void applyTextInputColors(Text text, Color background, Color foreground) {
    if (text == null || text.isDisposed() || background == null || background.isDisposed()
        || foreground == null || foreground.isDisposed()) {
      return;
    }

    text.setBackground(background);
    text.setForeground(foreground);
    text.redraw();
  }

  private static void appendCssClass(Control control, String className) {
    Object currentClassNames = control.getData(CssConstants.CSS_CLASS_NAME_KEY);
    if (currentClassNames instanceof String names && !names.isBlank()) {
      if (!List.of(names.split("\\s+")).contains(className)) {
        control.setData(CssConstants.CSS_CLASS_NAME_KEY, names + " " + className);
      }
      return;
    }

    control.setData(CssConstants.CSS_CLASS_NAME_KEY, className);
  }

  private static void applyBackground(Control control, Color background) {
    if (control == null || control.isDisposed() || background == null || background.isDisposed()) {
      return;
    }

    control.setBackground(background);
    if (control instanceof Composite composite) {
      composite.setBackgroundMode(SWT.INHERIT_FORCE);
      for (Control child : composite.getChildren()) {
        applyBackground(child, background);
      }
    }
  }

  private static void disposeColor(Color color) {
    if (color != null && !color.isDisposed()) {
      color.dispose();
    }
  }
}
