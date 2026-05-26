// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.lsp4j.Position;

import com.microsoft.copilot.eclipse.core.CopilotCore;
import com.microsoft.copilot.eclipse.core.completion.CompletionListener;
import com.microsoft.copilot.eclipse.core.completion.CompletionProvider;
import com.microsoft.copilot.eclipse.core.lsp.CopilotLanguageServerConnection;
import com.microsoft.copilot.eclipse.core.lsp.protocol.CompletionItem;
import com.microsoft.copilot.eclipse.core.utils.FileUtils;
import com.microsoft.copilot.eclipse.ui.utils.UiUtils;

/**
 * Content assist processor that delivers Copilot inline completions inside DataWeave (.dwl) editors.
 *
 * <p>Eclipse registers this processor via the {@code org.eclipse.ui.workbench.texteditor.contentAssist}
 * extension point for the {@code com.microsoft.copilot.eclipse.ui.dataweaveFile} content type. When the
 * user triggers content assist (Ctrl+Space) in a .dwl editor, Eclipse calls
 * {@link #computeCompletionProposals}, which fires a Copilot LSP completion request and waits briefly for
 * the result before returning proposals to the standard Eclipse content assist popup.
 */
public class DataWeaveContentAssistProcessor implements IContentAssistProcessor {

  private static final long COMPLETION_WAIT_MS = 3_000;

  @Override
  public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
    IDocument document = viewer.getDocument();
    if (document == null) {
      return new ICompletionProposal[0];
    }

    IFile file = getFileForDocument(document);
    if (file == null) {
      // Unable to determine the file for this document
      return new ICompletionProposal[0];
    }

    Position position = toPosition(document, offset);
    if (position == null) {
      return new ICompletionProposal[0];
    }

    // Ensure the document is connected to the Copilot LSP. EditorLifecycleListener handles this for
    // standard ITextEditor parts, but the DataWeave embedded editor is a custom SWT widget that does
    // not adapt to ITextEditor, so connectDocumentIfNecessary skips it. We connect here explicitly
    // using the IDocument we already have from the viewer.
    CopilotLanguageServerConnection lsConnection = CopilotCore.getPlugin().getCopilotLanguageServer();
    if (lsConnection != null) {
      lsConnection.connectDocument(document, file);
    }

    CompletionProvider provider = CopilotCore.getPlugin().getCompletionProvider();
    if (provider == null) {
      return new ICompletionProposal[0];
    }

    String fileUri = FileUtils.getResourceUri(file);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<List<CompletionItem>> resultRef = new AtomicReference<>();

    CompletionListener listener = new CompletionListener() {
      @Override
      public void onCompletionResolved(String uriString, List<CompletionItem> completions) {
        if (fileUri != null && fileUri.equals(uriString)) {
          resultRef.set(completions);
          latch.countDown();
        }
      }
    };

    provider.addCompletionListener(listener);
    try {
      int documentVersion = document.hashCode();
      provider.triggerCompletion(file, position, documentVersion, false);
      latch.await(COMPLETION_WAIT_MS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      provider.removeCompletionListener(listener);
    }

    List<CompletionItem> items = resultRef.get();
    if (items == null || items.isEmpty()) {
      return new ICompletionProposal[0];
    }

    return toProposals(items, offset);
  }

  @Override
  public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
    return null;
  }

  @Override
  public char[] getCompletionProposalAutoActivationCharacters() {
    return null;
  }

  @Override
  public char[] getContextInformationAutoActivationCharacters() {
    return null;
  }

  @Override
  public String getErrorMessage() {
    return null;
  }

  @Override
  public IContextInformationValidator getContextInformationValidator() {
    return null;
  }

  private IFile getFileForDocument(IDocument document) {
    // Try to get the file from the active editor
    IFile file = UiUtils.getCurrentFile();
    if (file != null) {
      return file;
    }

    // Fallback: look for an open .dwl file. This helps support custom editors
    // that don't integrate fully with Eclipse's editor infrastructure.
    try {
      List<IFile> openFiles = UiUtils.getOpenedFiles();
      for (IFile openFile : openFiles) {
        if ("dwl".equals(openFile.getFileExtension()) && openFile.exists()) {
          return openFile;
        }
      }
    } catch (Exception e) {
      // Ignore and continue
    }

    return null;
  }

  private Position toPosition(IDocument document, int offset) {
    try {
      int line = document.getLineOfOffset(offset);
      int lineStart = document.getLineOffset(line);
      return new Position(line, offset - lineStart);
    } catch (Exception e) {
      return null;
    }
  }

  private ICompletionProposal[] toProposals(List<CompletionItem> items, int offset) {
    List<ICompletionProposal> proposals = new ArrayList<>();
    for (CompletionItem item : items) {
      String insertText = item.getText();
      if (insertText == null || insertText.isBlank()) {
        continue;
      }
      // Use the displayText as the proposal label, falling back to the insert text itself
      String displayText = item.getDisplayText();
      String label = (displayText != null && !displayText.isBlank()) ? displayText : insertText;
      // Replace from the start of the current token (offset) with the full completion text
      proposals.add(new CompletionProposal(insertText, offset, 0, insertText.length(), null, label, null, null));
    }
    return proposals.toArray(new ICompletionProposal[0]);
  }
}
