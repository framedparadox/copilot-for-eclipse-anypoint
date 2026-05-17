// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.MultipleHyperlinkPresenter;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.mylyn.wikitext.markdown.MarkdownLanguage;
import org.eclipse.mylyn.wikitext.parser.builder.HtmlDocumentBuilder;
import org.eclipse.mylyn.wikitext.parser.css.CssParser;
import org.eclipse.mylyn.wikitext.ui.viewer.MarkupViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;

import com.microsoft.copilot.eclipse.core.CopilotCore;
import com.microsoft.copilot.eclipse.ui.CopilotUi;
import com.microsoft.copilot.eclipse.ui.swt.CssConstants;
import com.microsoft.copilot.eclipse.ui.utils.UiUtils;

class ChatMarkupViewer extends MarkupViewer {

  public ChatMarkupViewer(Composite parent, int styles) {
    super(parent, null, styles);
    this.setMarkupLanguage(new MarkdownLanguage());
    this.setDisplayImages(false);

    IHyperlinkDetector[] hyperlinkDetectors = { new FileAnnotationHyperlinkDetector() };
    this.setHyperlinkDetectors(hyperlinkDetectors, SWT.NONE);

    MultipleHyperlinkPresenter hyperlinkPresenter = new MultipleHyperlinkPresenter((RGB) null);
    this.setHyperlinkPresenter(hyperlinkPresenter);

    applyMessageBackground();

    // Register for chat font updates via centralized service
    var chatServiceManager = CopilotUi.getPlugin().getChatServiceManager();
    if (chatServiceManager != null) {
      chatServiceManager.getChatFontService().registerControl(getTextWidget());
    }
    loadStylesheet();
  }

  private void loadStylesheet() {
    if (UiUtils.isDarkTheme()) {
      URL cssUrl = CopilotUi.getPlugin().getBundle().getEntry("css/markup-viewer-dark.css");
      if (cssUrl != null) {
        try (Reader reader = new InputStreamReader(cssUrl.openStream(), StandardCharsets.UTF_8)) {
          this.setStylesheet(new CssParser().parse(reader));
        } catch (IOException e) {
          CopilotCore.LOGGER.error("Failed to load dark mode stylesheet for markup viewer", e);
        }
      }
    }
  }

  // MarkupViewer will write errors when failed to parse the markup, which will send the error to the Copilot.
  // so overwrite the setMarkup method to avoid sending the error.
  @Override
  public void setMarkup(String source) {
    try {
      String htmlText = this.computeHtml(source);
      setHtml(htmlText);
      applyMessageBackground();
      // reset text presentation to update the style, otherwise the style won't be updated
      this.setTextPresentation(getTextPresentation());
    } catch (Throwable t) {
      if (getTextPresentation() != null) {
        getTextPresentation().clear();
      }
      setDocumentNoMarkup(new Document(source), new AnnotationModel());
      applyMessageBackground();
      // TODO: Whether we should track the parse exception?
    }
  }

  private void applyMessageBackground() {
    StyledText textWidget = getTextWidget();
    if (textWidget == null || textWidget.isDisposed()) {
      return;
    }
    Object currentClassNames = textWidget.getData(CssConstants.CSS_CLASS_NAME_KEY);
    if (currentClassNames instanceof String classNames && !classNames.contains("chat-message-text")) {
      textWidget.setData(CssConstants.CSS_CLASS_NAME_KEY, classNames + " chat-message-text");
    } else if (!(currentClassNames instanceof String)) {
      textWidget.setData(CssConstants.CSS_CLASS_NAME_KEY, "chat-message-text");
    }
    if (!UiUtils.isDarkTheme()) {
      textWidget.setBackground(textWidget.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
    }
  }

  // computeHtml(String) is a private method in MarkupViewer, so copy it here.
  private String computeHtml(String markupContent) {
    StringWriter out = new StringWriter();
    HtmlDocumentBuilder builder = new HtmlDocumentBuilder(out);
    builder.setFilterEntityReferences(true);

    getParser().setBuilder(builder);
    getParser().parse(markupContent);
    getParser().setBuilder(null);

    String htmlText = out.toString();
    return htmlText;
  }
}
