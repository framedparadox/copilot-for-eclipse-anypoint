# GitHub Copilot for Eclipse (MuleSoft Anypoint Studio)

GitHub Copilot for Eclipse brings AI-assisted coding to the Eclipse IDE with these core capabilities:

- **Code completions** for in-editor suggestions from code context or natural-language comments.
- **Next Edit Suggestions** provide context-aware suggestions for your next code edits.
- **Agent Mode** for conversational help and more autonomous, project-aware assistance.
- **Model Context Protocol (MCP)** integration to connect Copilot with external tools and services.
- **Advanced Agentic Capabilities** include Custom Agents, Isolated Subagents, and Plan Agent, with more agentic capabilities coming soon.
- **Anypoint Studio Integration** connects Copilot with the official MuleSoft MCP server for AI-assisted Mule application development directly in Anypoint Studio.

## Usage-based billing support

Starting from version **0.19.0**, we have added internal support for the upcoming [usage-based billing experience](https://github.blog/news-insights/company-news/github-copilot-is-moving-to-usage-based-billing/), including experience updates to the usage panel, usage notifications, and model picker. These changes will become visible once usage-based billing is rolled out.

To ensure compatibility with the new billing experience, we strongly recommend upgrading the plugin to **0.19.0 or later** as soon as possible.

Clients using older plugin versions will continue to function. However, the billing and usage experience may not be optimal and may not accurately reflect the latest usage-based billing experience.


## Getting access to GitHub Copilot

Sign up for [GitHub Copilot Free](https://github.com/settings/copilot?utm_source=vscode-chat-readme&utm_medium=second&utm_campaign=2025mar-em-MSFT-signup), or request access from your enterprise admin.

To use GitHub Copilot, an active subscription is required. Learn more about business and individual plans at [github.com/features/copilot](https://github.com/features/copilot?utm_source=vscode-chat&utm_medium=readme&utm_campaign=2025mar-em-MSFT-signup).

## Prerequisites

- [Eclipse IDE](https://www.eclipse.org/downloads/)
- An active [GitHub Copilot subscription](https://github.com/features/copilot)

## Install and set up

### Option 1: Eclipse Marketplace

1. Open [Eclipse Marketplace](https://marketplace.eclipse.org/) and go to the [GitHub Copilot plugin page](https://marketplace.eclipse.org/content/github-copilot).
2. Drag **Install** to your running Eclipse workspace.
3. Restart Eclipse.
4. Sign in to GitHub Copilot from Eclipse.

### Option 2: Install from update site

1. In Eclipse, open **Help → Install New Software…**
2. Add this update site URL: `https://azuredownloads-g3ahgwb5b8bkbxhd.b01.azurefd.net/github-copilot/`
3. Select **GitHub Copilot** and complete installation.
4. Restart Eclipse and sign in.

## Core capabilities

### Code completions

Inline suggestions (ghost text) appear as you type in the editor. Suggestions can range from small edits to multi-line changes.

### Next Edit Suggestions

Next Edit Suggestions predict your next edit location and propose the next change based on your recent edits and context.

### Agent and Ask Mode

**Ask Mode** provides conversational AI assistance for explaining code, generating code from requirements, suggesting refactors, and providing debugging guidance.

**Agent Mode** works autonomously across your project context to identify and fix issues, propose implementation steps, and support larger coding tasks with iterative guidance.

### Model Context Protocol (MCP)

MCP support enables integrating external tools and services into Copilot workflows where configured.

### Advanced Agentic Capabilities

- **Custom Agents** allow users to create personalized agents with specific instructions and behaviors.
- **Isolated Subagents** can be spawned by the main agent to handle specific tasks or contexts independently.
- **Plan Agent** can generate multi-step plans to accomplish complex tasks, breaking them down into manageable actions.
- **Skills** are reusable, specialized AI assistant templates that enrich chat context in Agent Mode. Skills are defined as `SKILL.md` files and can be scoped to a workspace or shared globally.

  - Creating Skills

    Place a `SKILL.md` file in any of these directories:

    - **Project-scoped:** `.github/skills/<skill-name>/`, `.claude/skills/<skill-name>/`, `.agents/skills/<skill-name>/`
    - **User-scoped (global):** `~/.copilot/skills/<skill-name>/`, `~/.claude/skills/<skill-name>/`, `~/.agents/skills/<skill-name>/`

    Each `SKILL.md` file can include YAML front matter with metadata (name, description) followed by Markdown content that provides domain knowledge, workflows, or instructions for the AI assistant.

    Skills are automatically discovered and available in Agent Mode. You can enable or disable skills in **Window → Preferences → Copilot → Chat → Enable Skills**.

For other available features in Eclipse, see the [Copilot feature matrix](https://docs.github.com/en/copilot/reference/copilot-feature-matrix?tool=eclipse).

## Anypoint Studio Integration

This plugin includes a dedicated integration for [MuleSoft Anypoint Studio](https://www.mulesoft.com/platform/studio), enabling AI-assisted Mule 4 application development through Copilot's Agent Mode and MCP.

### Slash Commands for MuleSoft Workflows

Type `/` in the Copilot chat to see all available MuleSoft slash commands. Each command runs a pre-configured agent workflow tailored to a specific Mule development task:

| Command | What it does |
|---|---|
| `/mule-code-review` | Reviews flow naming, error handlers, global configs, DataWeave, and APIkit route coverage |
| `/mule-security-review` | Detects hardcoded secrets, SQL/XPath injection risks, missing TLS, authentication gaps, and policy coverage |
| `/mule-performance-review` | Identifies DataWeave materialization, batch sizing issues, missing connector pooling, N+1 queries, and caching opportunities |
| `/deployment-readiness` | Platform-specific deployment checklist for CloudHub, Runtime Fabric, or standalone; includes health endpoints and smoke tests |
| `/api-spec-review` | Validates RAML/OpenAPI governance, APIkit router binding, security scheme implementation, and error response contracts |
| `/generate-munit-tests` | Generates MUnit tests covering happy path, error path, connector failure, async flows, batch jobs, and scatter-gather patterns |
| `/dataweave-best-practices` | Reviews Transform Message components for null safety, streaming, functional patterns, and module reuse |
| `/connector-governance` | Audits connector versions against the Mule runtime, flags deprecated connectors, missing pooling, and retry strategy gaps |
| `/logging-observability` | Reviews correlation ID propagation, structured log format, log levels, PII exposure, and Anypoint Monitoring setup |
| `/error-handling-contract` | Audits On Error Propagate/Continue usage, typed matchers, correlation ID in error handlers, and HTTP status codes |
| `/api-led-architecture-review` | Validates API-led layer assignment (Experience/Process/System), call direction rules, and connector placement |
| `/batch-job-review` | Reviews batch job structure, block sizing, aggregator config, step error handling, and On Complete logging |
| `/async-flow-review` | Reviews scheduler flows, VM/MQ listener patterns, async scope usage, and graceful shutdown configuration |

### Smart Console Error Parsing

When you use `@console` in the chat with Anypoint Studio console output that contains a Mule runtime exception, the plugin automatically prepends a structured summary before the raw output:

```
[Mule Error Summary]
Error type: HTTP:CONNECTIVITY
Flow: get-customer-main
Root cause: Connection refused to https://api.example.com:443
Component: HTTP_Request @ get-customer-main/processors/2
```

This lets the AI immediately understand the error context without parsing through Java stack trace noise.

### Project Scanning

The `mule_project_scan` tool (invoked automatically by most slash commands) returns rich Mule-specific analysis including:

- Runtime version, flows, sub-flows, global configs, connectors, MUnit suite coverage
- Per-flow error handler classification: `typed` (has type matchers), `catch-all` (no type), or `none`
- Flows where a correlation ID is set at the source
- Scheduler-triggered flows (require different MUnit strategy from HTTP flows)
- `log4j2RootLevel` — flags DEBUG/TRACE in production
- DB connection pool config presence and HTTP request timeout presence
- `reconnect-forever` and `until-successful` without `maxRetries` detection
- `hasBatchJob`, `hasApikit`, `hasSecureProperties` presence flags

### MuleSoft MCP Server

The plugin can automatically register the official [`mulesoft-mcp-server`](https://www.npmjs.com/package/mulesoft-mcp-server) as an MCP server in Copilot Agent Mode. This unlocks MuleSoft-native tools such as:

- Creating and validating Mule projects
- Generating and implementing API specs (RAML/OAS)
- Running DataWeave scripts and generating sample data
- Creating, running, and reviewing MUnit tests
- Searching Anypoint Exchange assets
- Deploying to CloudHub, Runtime Fabric, and Flex Gateway

### Configuring MuleSoft MCP

1. In Anypoint Studio, open **Window → Preferences → Copilot → MuleSoft MCP**.
2. Check **Enable MuleSoft MCP Server registration**.
3. Enter your **Anypoint Platform Connected App** credentials:
   - **Client ID** – the connected app client ID.
   - **Client Secret** – stored securely in Eclipse secure storage.
   - **Region** *(optional)* – select from the dropdown: `PROD_US`, `PROD_EU`, `PROD_CA`, or `PROD_JP`. Defaults to `PROD_US` when left blank.
4. Click **Apply and Close**, then approve the `mulesoft` server entry in **Preferences → Copilot → MCP Servers**.

> **Tip:** If any field is left blank, the integration falls back to the `ANYPOINT_CLIENT_ID`, `ANYPOINT_CLIENT_SECRET`, and `ANYPOINT_REGION` environment variables set in the Studio process environment.

### Workspace-Level Custom Instructions

For best results, add a `copilot-instructions.md` file to your Mule project at `.github/copilot-instructions.md`. This file is read automatically on every chat turn and tells Copilot about your project's runtime version, API-led layer, connector conventions, error handling strategy, and MUnit expectations.

A ready-to-use template is bundled at:
```
com.microsoft.copilot.eclipse.anypoint/templates/copilot-instructions-mule-template.md
```
Copy it to `.github/copilot-instructions.md` in your Mule project and fill in the placeholders.

### MuleSoft Agent Template

A pre-built agent template (`mulesoft-agent.agent.md`) is bundled with the plugin. It configures a specialized Copilot agent scoped to Mule 4 development, automatically wiring the relevant MuleSoft MCP tools alongside built-in tools. The agent enforces:

- **API-led architecture** — Experience → Process → System call direction rules
- **Error handling contract** — typed On Error Propagate handlers, correlation ID logging, consistent error response shape
- **DataWeave standards** — output type declaration, null-safe access, streaming for large payloads
- **Logging discipline** — structured JSON format, correlation IDs at INFO, no PII in logs
- **Connector governance** — version compatibility, pooling config, no `reconnect-forever` in production

### Prerequisites for MuleSoft MCP

- [Node.js](https://nodejs.org/) (includes `npx`) must be available on the `PATH` used by the Anypoint Studio process.
- A [MuleSoft Connected App](https://docs.mulesoft.com/access-management/connected-apps-overview) with the necessary scopes for the tools you intend to use.

---

## Privacy and responsible use

We follow responsible practices in accordance with our
[Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement).

To get the latest security fixes, please use the latest version of GitHub Copilot for Eclipse.

## Data and telemetry

The GitHub Copilot for Eclipse plugin collects usage data and sends it to Microsoft to help improve our products and services. Read our [privacy statement](https://privacy.microsoft.com/privacystatement) to learn more.

## Security

Please do not report security vulnerabilities in public issues.

See [SECURITY.md](SECURITY.md) for vulnerability reporting instructions.

## Support

For bug reports and feature requests, use this repository’s Issues.

For support guidance, see [SUPPORT.md](SUPPORT.md).

## Contributing

This project welcomes contributions and suggestions. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on how to get started, build the project, submit pull requests, and follow our code style guidelines.

Most contributions require you to agree to a Contributor License Agreement (CLA) declaring that you have the right to, and actually do, grant us the rights to use your contribution. For details, visit [Contributor License Agreements](https://cla.opensource.microsoft.com).

## Trademarks

This project may contain trademarks or logos for projects, products, or services. Authorized use of Microsoft
trademarks or logos is subject to and must follow
[Microsoft's Trademark & Brand Guidelines](https://www.microsoft.com/legal/intellectualproperty/trademarks/usage/general).
Use of Microsoft trademarks or logos in modified versions of this project must not cause confusion or imply Microsoft sponsorship.
Any use of third-party trademarks or logos are subject to those third-party's policies.

For clarity: product and company names used in this repository (including but not limited to "GitHub", "GitHub Copilot", "Microsoft", "MuleSoft", and "Anypoint Studio") are the trademarks or registered trademarks of their respective owners. The presence of these names or logos in this project does not imply endorsement, sponsorship, or formal affiliation by the trademark owners.

## License

Copyright (c) Microsoft Corporation. All rights reserved.

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.