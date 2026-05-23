// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.anypoint;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * Preferences for the MuleSoft MCP bridge used by Anypoint Studio.
 */
public class MuleSoftMcpPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  public static final String ID = "com.microsoft.copilot.eclipse.anypoint.preferences.MuleSoftMcpPreferencePage";

  private static final String[] REGION_OPTIONS = { "", "PROD_US", "PROD_EU", "PROD_CA", "PROD_JP" };

  private Button enabledButton;
  private Text clientIdText;
  private Text clientSecretText;
  private Combo regionCombo;

  @Override
  public void init(IWorkbench workbench) {
    setDescription("Configure the official MuleSoft MCP Server for Copilot Agent Mode in Anypoint Studio.");
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite container = new Composite(parent, SWT.NONE);
    container.setLayout(new GridLayout(2, false));
    container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    container.setBackground(parent.getBackground());

    enabledButton = new Button(container, SWT.CHECK);
    enabledButton.setText("Enable MuleSoft MCP Server registration");
    enabledButton.setLayoutData(spanTwoColumns());
    useParentBackground(enabledButton);

    createLabel(container, "Client ID:");
    clientIdText = createText(container, SWT.BORDER);

    createLabel(container, "Client Secret:");
    clientSecretText = createText(container, SWT.BORDER | SWT.PASSWORD);

    createLabel(container, "Region:");
    regionCombo = new Combo(container, SWT.DROP_DOWN | SWT.READ_ONLY);
    regionCombo.setItems(REGION_OPTIONS);
    regionCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    Label statusLabel = new Label(container, SWT.WRAP);
    statusLabel.setText("Status: MCP server registration is managed in Preferences → Copilot → MCP Servers. "
        + "After saving, approve the mulesoft server entry in that page for it to become active.");
    statusLabel.setLayoutData(spanTwoColumns());
    useParentBackground(statusLabel);

    Label note = new Label(container, SWT.WRAP);
    note.setText("Credentials are stored in Eclipse secure storage. If a field is blank, the integration also checks "
        + "ANYPOINT_CLIENT_ID, ANYPOINT_CLIENT_SECRET, and ANYPOINT_REGION from the Studio process environment.");
    note.setLayoutData(spanTwoColumns());
    useParentBackground(note);

    loadSettings();
    return container;
  }

  @Override
  public boolean performOk() {
    String selectedRegion = regionCombo.getSelectionIndex() > 0 ? regionCombo.getText() : "";
    MuleSoftMcpSettings.save(enabledButton.getSelection(), clientIdText.getText(), clientSecretText.getText(),
        selectedRegion);
    if (enabledButton.getSelection() && MuleSoftMcpConfiguration.buildJson(clientIdText.getText(),
        clientSecretText.getText(), selectedRegion).isEmpty()) {
      MessageDialog.openWarning(getShell(), "MuleSoft MCP Server",
          "MuleSoft MCP is enabled, but Client ID and Client Secret are empty. "
              + "Set them here or in the Studio process environment before approving the MCP server.");
    }
    return true;
  }

  private void loadSettings() {
    enabledButton.setSelection(MuleSoftMcpSettings.isEnabled());
    clientIdText.setText(MuleSoftMcpSettings.getClientId());
    clientSecretText.setText(MuleSoftMcpSettings.getClientSecret());
    String savedRegion = MuleSoftMcpSettings.getRegion();
    regionCombo.select(0);
    for (int i = 0; i < REGION_OPTIONS.length; i++) {
      if (REGION_OPTIONS[i].equals(savedRegion)) {
        regionCombo.select(i);
        break;
      }
    }
  }

  private static void createLabel(Composite container, String text) {
    Label label = new Label(container, SWT.NONE);
    label.setText(text);
    useParentBackground(label);
  }

  private static Text createText(Composite container, int style) {
    Text text = new Text(container, style);
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return text;
  }

  private static GridData spanTwoColumns() {
    GridData gridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    gridData.horizontalSpan = 2;
    return gridData;
  }

  private static void useParentBackground(Control control) {
    if (control != null && !control.isDisposed() && control.getParent() != null
        && !control.getParent().isDisposed()) {
      control.setBackground(control.getParent().getBackground());
    }
  }
}
