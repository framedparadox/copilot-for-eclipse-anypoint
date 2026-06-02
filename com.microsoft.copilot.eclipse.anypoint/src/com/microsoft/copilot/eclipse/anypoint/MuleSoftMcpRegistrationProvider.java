// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.anypoint;

import java.util.concurrent.CompletableFuture;

import com.microsoft.copilot.eclipse.ui.extensions.IMcpRegistrationProvider;

/**
 * Registers the official MuleSoft MCP server with Copilot when configured by the user.
 */
public class MuleSoftMcpRegistrationProvider implements IMcpRegistrationProvider {

  @Override
  public CompletableFuture<String> getMcpServerConfigurations() {
    if (!MuleSoftMcpSettings.isEnabled()) {
      return CompletableFuture.completedFuture("");
    }
    String json = MuleSoftMcpConfiguration.buildJson(MuleSoftMcpSettings.getClientId(),
        MuleSoftMcpSettings.getClientSecret(), MuleSoftMcpSettings.getRegion());
    return CompletableFuture.completedFuture(json);
  }
}
