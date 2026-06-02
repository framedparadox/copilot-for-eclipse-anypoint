// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.copilot.eclipse.ui.chat.services.ChatCompletionService;
import com.microsoft.copilot.eclipse.ui.chat.services.ChatServiceManager;
import com.microsoft.copilot.eclipse.ui.chat.services.UserPreferenceService;

@ExtendWith(MockitoExtension.class)
class ChatAssistProcessorTest {
  @Mock
  private ChatServiceManager chatServiceManager;

  @Mock
  private ChatCompletionService chatCompletionService;

  @Mock
  private UserPreferenceService userPreferenceService;

  private ChatAssistProcessor processor;

  @BeforeEach
  void setUp() {
    when(chatServiceManager.getChatCompletionService()).thenReturn(chatCompletionService);
    when(chatServiceManager.getUserPreferenceService()).thenReturn(userPreferenceService);
    processor = new ChatAssistProcessor(null, chatServiceManager);
  }

  @Test
  void consoleProposalIsAvailableInBuiltInAskAgentAndPlanModes() {
    assertConsoleProposal("Ask");
    assertConsoleProposal("Agent");
    assertConsoleProposal("Plan");
  }

  @Test
  void consoleProposalIsNotAvailableForCustomAgents() {
    String customModeId = "file:///workspace/.github/agents/custom.agent.md";
    when(userPreferenceService.getActiveModeNameOrId()).thenReturn(customModeId);
    when(chatCompletionService.isConsoleContextCommandAvailable(customModeId)).thenReturn(false);
    when(chatCompletionService.isAgentsReady()).thenReturn(false);

    ICompletionProposal[] proposals = processor.createCopilotCompletionAgentProposals("con");

    assertEquals(0, proposals.length);
  }

  @Test
  void consoleProposalIsNotAvailableWhenPreferenceIsDisabled() {
    when(userPreferenceService.getActiveModeNameOrId()).thenReturn("Ask");
    when(chatCompletionService.isConsoleContextCommandAvailable("Ask")).thenReturn(false);
    when(chatCompletionService.isAgentsReady()).thenReturn(false);

    ICompletionProposal[] proposals = processor.createCopilotCompletionAgentProposals("con");

    assertEquals(0, proposals.length);
  }

  private void assertConsoleProposal(String modeName) {
    when(userPreferenceService.getActiveModeNameOrId()).thenReturn(modeName);
    when(chatCompletionService.isConsoleContextCommandAvailable(modeName)).thenReturn(true);
    when(chatCompletionService.isAgentsReady()).thenReturn(false);

    ICompletionProposal[] proposals = processor.createCopilotCompletionAgentProposals("con");

    assertEquals(1, proposals.length);
    assertEquals("@console", proposals[0].getDisplayString());
  }
}
