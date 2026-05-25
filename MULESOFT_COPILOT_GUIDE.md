# MuleSoft Copilot Guide — GitHub Copilot for Anypoint Studio

This guide documents every MuleSoft-specific capability in the Copilot for Eclipse plugin, explains why each feature exists, and shows how to use it effectively.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Slash Commands Reference](#slash-commands-reference)
3. [Smart Console Error Parsing](#smart-console-error-parsing)
4. [Project Scanning — What the AI Sees](#project-scanning--what-the-ai-sees)
5. [Agent Behavior and Built-in Rules](#agent-behavior-and-built-in-rules)
6. [Workspace Custom Instructions Template](#workspace-custom-instructions-template)
7. [MuleSoft MCP Server Setup](#mulesoft-mcp-server-setup)
8. [Preferences and Defaults](#preferences-and-defaults)
9. [Tool Reference](#tool-reference)
10. [Typical Workflows](#typical-workflows)

---

## Quick Start

1. Open a Mule 4 project in Anypoint Studio.
2. Open the Copilot chat panel.
3. Type `/` to see all available MuleSoft slash commands.
4. Run your first review:
   ```
   /mule-code-review
   ```
5. The agent will automatically scan the project, run a code review, and return prioritized findings with recommended fixes.

For best results, add a [`copilot-instructions.md`](#workspace-custom-instructions-template) to your project before starting.

---

## Slash Commands Reference

All commands run in **Agent Mode** and invoke the appropriate local Mule tools automatically. You do not need to specify a project path — the agent uses the open project in the workspace.

### `/mule-code-review`

**Purpose**: General code quality review across all Mule XML, DataWeave, properties, and MUnit suites.

**What it checks**:
- Flow naming conventions (camelCase verb-noun: `getCustomerByIdFlow`)
- Sub-flows vs. private flows — when each is appropriate
- On Error Propagate presence on all HTTP-facing flows
- On Error Continue misuse as a catch-all
- Correlation ID set at HTTP Listener source, propagated in outbound headers
- Global config duplication and hardcoded values
- Property placeholder externalization (`${secure::}` for secrets)
- DataWeave output type declarations and null-safe field access
- APIkit route coverage vs. RAML/OpenAPI spec endpoints

**Example invocation**:
```
/mule-code-review
```

---

### `/mule-security-review`

**Purpose**: Security vulnerability scan for Mule-specific threats.

**What it checks**:
- Hardcoded credentials in XML attributes or property files
- Missing `${secure::}` prefix on sensitive properties
- Missing Mule Secure Configuration Properties module dependency
- **SQL injection**: DB connector queries with string concatenation instead of `:variable` syntax
- **XPath injection**: XPath expressions with user-controlled input
- **XXE**: XML parsing without secure parser settings
- Insecure HTTP Listener endpoints (missing TLS)
- Outbound HTTP Request configs with `insecure="true"` or no TLS context
- Missing authentication mechanism on HTTP-facing flows
- Full payload logging (exposes PII or secrets)
- API policy gaps (rate limiting, authentication enforcement)

**Example invocation**:
```
/mule-security-review
```

---

### `/mule-performance-review`

**Purpose**: Performance and scalability analysis.

**What it checks**:
- DataWeave transforms that materialize large payloads — recommends `streaming=true`
- Nested `map` over large collections (O(n²)) — recommends `groupBy` + lookup
- Inline regex compiled per iteration — recommends pre-compile to a variable
- Batch job `maxRecordsPerBlock` sizing (default 100 is rarely optimal)
- `maxConcurrency` on CPU-intensive vs. IO-bound flows
- DB connector N+1 query patterns (`<db:select>` inside `<foreach>`)
- Missing `minPoolSize`/`maxPoolSize` on DB connector config
- Missing `responseTimeout` on HTTP Request configs
- Missing `reconnect-forever` (infinite retry blocks a thread)
- `<until-successful>` without `maxRetries`
- Caching opportunities for repeated calls to static/slow-changing APIs

**Example invocation**:
```
/mule-performance-review
```

---

### `/deployment-readiness`

**Purpose**: Pre-deployment checklist tailored to the target platform.

**What it checks (all platforms)**:
- `mule-artifact.json` present with correct `minMuleVersion`
- Maven plugin version compatible with target runtime
- All MUnit tests passing
- No hardcoded secrets
- Environment-specific properties externalized to `config-<env>.yaml`
- Log level set to INFO or WARN in production log4j2.xml
- Health endpoint present and returning `{"status": "UP"}`

**Additional checks by platform**:
- **CloudHub 1.0**: Worker sizing, persistent queues, static IP
- **CloudHub 2.0 / Runtime Fabric**: Resource requests/limits, replica count (≥2 for HA), liveness probes
- **On-premises**: Cluster config, JVM heap sizing, process user permissions

**Example invocation**:
```
/deployment-readiness
```
The agent will ask which platform you are targeting if it cannot infer it.

---

### `/api-spec-review`

**Purpose**: API contract governance and APIkit compatibility.

**What it checks**:
- Required metadata (title, version, baseUri/servers)
- All request/response bodies use named schemas (no inline anonymous objects)
- Examples present and valid against their schema
- Error responses defined (400, 401, 404, 500 at minimum)
- Security scheme defined AND applied to all non-public endpoints
- URL versioning (e.g., `/v1/`) present and consistent
- APIkit route coverage: every spec endpoint has a router flow; no orphaned router flows
- RAML library fragments pinned to specific Exchange versions

**Example invocation**:
```
/api-spec-review
```

---

### `/generate-munit-tests`

**Purpose**: Generate comprehensive MUnit test coverage.

**What it generates**:
- Happy path, invalid input (400), connector failure simulation, error-response contract — per flow
- Async flow testing via `munit:run-flow` direct invocation (not scheduler-dependent)
- Batch job tests: unit-test steps in isolation + integration-test with fixture dataset
- Scatter-gather tests: each route mocked independently; one test with a failing route
- Transactional rollback tests: second connector mocked to fail, first write verified rolled back
- Choice router branch tests: one test per when-condition + otherwise
- Correlation ID assertion in Logger calls

After generating, the agent runs `munit_validate_flow_tests` and `run_mule_maven_tests` to confirm correctness.

**Example invocation**:
```
/generate-munit-tests
```
Specify a flow name to generate tests for a single flow:
```
/generate-munit-tests for getCustomerByIdFlow
```

---

### `/dataweave-best-practices`

**Purpose**: DataWeave-specific quality review. There is no other platform with DataWeave — this prompt covers Mule-unique patterns.

**What it checks**:
- Output type declaration on every script
- Null-safe access via `default` operator on all optional fields
- Functional style: `map`, `filter`, `reduce`, `groupBy` over imperative `if/else`
- Nested `map` performance (O(n²)) — recommends `groupBy` indexing
- Inline regex compiled inside `map` — recommends pre-compile to variable
- Unnecessary serialization round-trips (`write` → `read`)
- Streaming appropriateness for large unknown-size payloads
- Repeated DW logic across transforms — recommends extracting to `.dwl` module
- Missing input/output type documentation on complex scripts

**Example invocation**:
```
/dataweave-best-practices
```

---

### `/connector-governance`

**Purpose**: Connector version, configuration, and authentication audit.

**What it checks**:
- Connector versions vs. Mule runtime compatibility matrix
- Deprecated connectors: HTTP v1, File Connector v1, Scripting Module (Groovy/JS/Python)
- Redundant connector configs (two `db:config` pointing to the same DB)
- DB connector: `minPoolSize`, `maxPoolSize`, `maxWait` present
- HTTP connector: `responseTimeout`, `connectionIdleTimeout` present
- `reconnect-forever` (blocks threads in production)
- `reconnect` without finite `count` and `frequency`
- `until-successful` without `maxRetries` and `millisBetweenRetries`
- Authentication method consistency (same upstream service should use same auth type)
- API key passed as query parameter instead of header

**Example invocation**:
```
/connector-governance
```

---

### `/logging-observability`

**Purpose**: Logging quality and Anypoint Monitoring setup.

**What it checks**:
- Correlation ID set at HTTP Listener from `X-Correlation-ID` header (fallback `uuid()`)
- Correlation ID propagated in all outbound HTTP Request headers
- Correlation ID included in all Logger calls in error handlers
- Log levels: INFO for flow entry/exit, ERROR for error handlers, DEBUG disabled in prod
- Structured JSON format in Logger `message` expressions (not string concatenation)
- Full payload logged at INFO (flag as PII/performance risk)
- Passwords/tokens/API keys logged without masking
- log4j2.xml root level (DEBUG/TRACE in production is flagged automatically by the scan)
- Anypoint Monitoring enabled for the deployed application

**Example invocation**:
```
/logging-observability
```

---

### `/error-handling-contract`

**Purpose**: Dedicated review of error handling quality. Consolidates rules scattered across code review, logging, and security prompts.

**What it checks**:
- Every HTTP-facing flow has per-flow `<on-error-propagate>` (not just a global handler)
- `<on-error-propagate>` vs. `<on-error-continue>` used correctly
- All handlers have typed error matchers (`type="HTTP:CONNECTIVITY"` etc.)
- Correlation ID logged in every error handler
- Error responses return consistent JSON shape: `{ "code", "message", "correlationId" }`
- HTTP status codes correct: 400/401/403/404/500/503 (never always 500)
- Raw Mule stack traces not returned to API consumers

Uses `flowErrorHandlerTypes` data from the scan (typed/catch-all/none per flow) to focus on the worst offenders first.

**Example invocation**:
```
/error-handling-contract
```

---

### `/api-led-architecture-review`

**Purpose**: Validates whether the project correctly implements its API-led layer.

**Layer definitions enforced**:
- **Experience API**: consumer-facing, calls Process APIs only
- **Process API**: orchestrates System APIs, no direct backend connectors
- **System API**: one backend system, no business logic, no calls to other APIs

**What it checks**:
- Correct call direction (Experience → Process → System, never upward)
- System API with multiple outbound HTTP connectors (likely doing Process API work)
- Process or Experience API with backend connector configs (DB, Salesforce) — System API layer missing
- Flow naming inconsistent with declared layer
- API spec vocabulary reflects the layer (business terms for xAPI/pAPI, backend terms for sAPI)

**Example invocation**:
```
/api-led-architecture-review
```

---

### `/batch-job-review`

**Purpose**: Dedicated review for Mule batch processing.

**What it checks**:
- `<batch:job>` structure: `batch:input`, at least one `batch:step`, `batch:on-complete`
- `maxRecordsPerBlock` sizing (default 100 — flags if not explicitly set)
- `<batch:aggregator>` with explicit `size` and `streaming="true"` for large sets
- Step-level error handling: On Error Continue for per-record failures, On Error Propagate to abort
- On Complete phase logging: `loadedRecords`, `successfulRecords`, `failedRecords`
- DataWeave inside steps: nested maps, inline regex, unnecessary serialization
- Recommended test fixture: valid record + failing record + boundary record

Only runs when `hasBatchJob=true` in the scan output.

**Example invocation**:
```
/batch-job-review
```

---

### `/async-flow-review`

**Purpose**: Reviews scheduler flows, async scopes, VM queues, and Anypoint MQ listeners.

**What it checks**:
- Scheduler flows: error handler present, correlation ID generated with `uuid()`, INFO logger at entry
- `<async>` scopes: business-critical logic inside async (data loss risk), no internal error handler
- VM connector: no corresponding listener for published messages, no ack/nack strategy
- Anypoint MQ: `acknowledgementMode` and explicit `ack`/`nack` usage, dead-letter queue configured
- Thread pool impact of heavy DataWeave inside async operations
- Graceful shutdown: `shutdownTimeout` appropriate for message processing time

Uses `schedulerFlows` data from the scan to target the right flows.

**Example invocation**:
```
/async-flow-review
```

---

## Smart Console Error Parsing

When you use `@console` in the chat, the plugin automatically enriches the console output if it contains a Mule runtime exception.

**Before enrichment** (raw dump sent to AI):
```
2026-05-22 10:14:32,401 ERROR ... [MuleContainerSystemClassLoader] ...
org.mule.runtime.api.exception.DefaultMuleException: HTTP POST on resource 'https://...' failed: Connection refused.
org.mule.extension.http.api.error.HttpRequestFailedException
    at org.mule.extension.http.internal...
    ... 47 more lines of stack trace
```

**After enrichment** (what the AI receives first):
```
[Mule Error Summary]
Error type: HTTP:CONNECTIVITY
Flow: processOrderFlow
Root cause: Connection refused to https://api.inventory.internal:8081
Component: HTTP_Request @ processOrderFlow/processors/3

[Console Context]
Console: Anypoint Studio Console
Truncated: no
Output:
<console-output>
... full raw output ...
</console-output>
```

The AI immediately knows what failed, in which flow, and what the root cause is — without spending context tokens parsing 50 lines of Java stack trace.

**How to use**: Simply prefix your message with `@console`:
```
@console Why is my flow failing?
```

---

## Project Scanning — What the AI Sees

Every slash command starts with `mule_project_scan`. Understanding what the scan returns helps you interpret agent responses.

### Standard Scan Output

| Field | What it contains |
|---|---|
| `runtimeVersion` | Mule 4.x version from `mule-artifact.json` or `pom.xml` |
| `flows` | All flow names across all XML files |
| `subFlows` | All sub-flow names |
| `globalConfigs` | Global config element names (connector configs, error handlers) |
| `connectors` | Connector artifact IDs from `pom.xml` |
| `munitFiles` | MUnit suite file paths |
| `apiSpecFiles` | RAML/OpenAPI/WSDL/XSD files found in `src/main/resources` |
| `deploymentPlugins` | CloudHub/RTF Maven plugin detected in `pom.xml` |
| `propertyPlaceholders` | All `${...}` placeholder keys found in XML |

### New Fields (Round 2)

| Field | Why it matters |
|---|---|
| `hasApikit` | APIkit router detected — spec/route coverage checks apply |
| `hasSecureProperties` | Secure Configuration Properties module present |
| `hasBatchJob` | `<batch:job>` detected — `/batch-job-review` is relevant |
| `schedulerFlows` | Names of scheduler-triggered flows — need `uuid()` correlation ID, direct MUnit invocation |
| `hasReconnectForever` | `reconnect-forever` detected — production reliability risk |
| `log4j2RootLevel` | Root log level from `log4j2.xml` — DEBUG/TRACE is flagged |
| `hasDbPoolConfig` | DB connector has `minPoolSize`/`maxPoolSize` configured |
| `hasHttpRequestTimeout` | HTTP Request connector has `responseTimeout` configured |
| `flowsWithCorrelationId` | Flows where a `set-variable variableName="correlationId"` is detected |
| `flowErrorHandlerTypes` | Per-flow: `typed`, `catch-all`, or `none` |
| `untilSuccessfulWithoutMaxRetries` | `<until-successful>` without `maxRetries` — runaway retry risk |

### Automatic Diagnostics

The scan automatically flags these issues without requiring a separate review command:

| Severity | Condition | Recommendation |
|---|---|---|
| Medium | `reconnect-forever` detected | Replace with finite `reconnect` |
| Medium | `until-successful` without `maxRetries` | Set `maxRetries` and `millisBetweenRetries` |
| Medium | `log4j2RootLevel` is DEBUG or TRACE | Set root level to INFO before deploying |
| Medium | DB connector present, no pool config | Add `minPoolSize`, `maxPoolSize`, `maxWait` |
| Medium | HTTP connector present, no timeout | Add `responseTimeout` to `http:request-config` |

---

## Agent Behavior and Built-in Rules

The Mulesoft agent (`mulesoft-agent.agent.md`) enforces these rules automatically without needing to be asked:

### API-Led Architecture
```
Experience API → Process API → System API
```
- Never suggests a System API calling a Process or Experience API
- Flags when a project's connectors don't match its declared layer
- Uses layer-appropriate naming conventions in generated flows

### Error Handling Contract
- All HTTP-facing generated flows include `<on-error-propagate type="...">` with typed matchers
- Error handlers always log `correlationId`, `flow.name`, `error.errorType`, `error.description`
- Error responses use `{ "code", "message", "correlationId" }` with correct HTTP status codes
- Never returns a raw Mule error description as the API response body

### DataWeave Standards
- Always reads `mule_read_transform` before modifying a Transform Message
- Generated DataWeave always declares `output` type
- Optional field accesses use `default` operator
- Large-payload transforms use `streaming=true`

### Logging Discipline
- Generated Logger components at INFO include `correlationId` and `flowName`
- No generated code logs `payload` at INFO level
- Structured JSON format in all generated Logger `message` expressions
- DEBUG logging disabled in production

### Connector Governance
- Suggests connector versions compatible with the project's `minMuleVersion`
- Never suggests `reconnect-forever`
- Generated DB configs include pool settings; generated HTTP configs include `responseTimeout`

---

## Workspace Custom Instructions Template

For the best Copilot experience in a Mule project, add a `copilot-instructions.md` to the project. This file is read automatically on every chat turn.

**Setup**:
1. Copy the template from the plugin:
   ```
   com.microsoft.copilot.eclipse.anypoint/templates/copilot-instructions-mule-template.md
   ```
2. Place it at `.github/copilot-instructions.md` in your Mule project root.
3. Fill in the placeholders (runtime version, layer, deployment target, connectors).

**What the template includes**:
- Mule runtime version and API-led layer declaration
- Flow and sub-flow naming conventions
- Secure property usage rules
- Error handling expectations
- Logging format and level expectations
- MUnit coverage requirements
- Connector versions in use

Copilot reads this file automatically and tailors all suggestions to match your project's conventions.

---

## MuleSoft MCP Server Setup

The MuleSoft MCP server enables Copilot to interact directly with the Anypoint Platform — generating flows, deploying applications, searching Exchange, and running DataWeave scripts.

### Prerequisites

- [Node.js](https://nodejs.org/) installed and on `PATH` for the Studio process
- A [MuleSoft Connected App](https://docs.mulesoft.com/access-management/connected-apps-overview) in Anypoint Platform with appropriate scopes

### Configuration Steps

1. **Open preferences**: **Window → Preferences → Copilot → MuleSoft MCP**
2. **Enable**: Check **Enable MuleSoft MCP Server registration**
3. **Enter credentials**:
   - **Client ID**: Your Connected App client ID
   - **Client Secret**: Stored in Eclipse secure storage
   - **Region**: Select from dropdown — `PROD_US`, `PROD_EU`, `PROD_CA`, or `PROD_JP`
4. **Save**: Click **Apply and Close**
5. **Approve**: Open **Preferences → Copilot → MCP Servers** and approve the `mulesoft` server entry

> **Environment variable fallback**: Leave fields blank to use `ANYPOINT_CLIENT_ID`, `ANYPOINT_CLIENT_SECRET`, and `ANYPOINT_REGION` from the Studio process environment.

### Available MCP Tools

Once registered, the following MuleSoft tools become available in Agent Mode:

- `mulesoft/create_mule_project` — scaffold a new Mule 4 project
- `mulesoft/generate_mule_flow` — generate flows from a description
- `mulesoft/run_local_mule_application` — run the app locally
- `mulesoft/generate_api_spec` / `implement_api_spec` / `mock_api_spec`
- `mulesoft/dataweave_run_script_tool` — test DataWeave scripts
- `mulesoft/generate_or_modify_munit_test` — create or update MUnit suites
- `mulesoft/deploy_mule_application` / `update_mule_application`
- `mulesoft/search_asset` — search Anypoint Exchange
- `mulesoft/manage_api_instance_policy` — apply policies
- `mulesoft/get_platform_insights` — application monitoring data
- And 15+ more tools for Exchange assets, Flex Gateway, and Runtime Fabric

---

## Preferences and Defaults

### Changed Defaults

| Preference | Old default | New default | Why |
|---|---|---|---|
| Console context (`@console`) | `false` | `true` | Mule developers rely on console for runtime error context |
| Workspace context (`@workspace`) | `false` | `true` | Multi-project Mule workspaces reference shared RAML specs and DataWeave modules |

### Key Preferences

- **Window → Preferences → Copilot → General**:
  - *Workspace context*: Enables `@workspace` for cross-project RAML/DWL references
  - *Console context*: Enables `@console` for runtime error analysis (now on by default)

- **Window → Preferences → Copilot → Chat**:
  - *Enable sub-agents*: Allows the agent to spawn sub-agents for parallel tasks
  - *Maximum agent requests*: Default 25; increase for complex project-wide reviews

- **Window → Preferences → Copilot → MuleSoft MCP**:
  - Region dropdown (PROD_US/EU/CA/JP) — replaces the free-text field

---

## Tool Reference

These tools are available in Copilot Agent Mode and are invoked automatically by slash commands. You can also ask the agent to use them explicitly.

| Tool | What it returns |
|---|---|
| `mule_project_scan` | Full project metadata + all new diagnostic fields (see [Project Scanning](#project-scanning--what-the-ai-sees)) |
| `mule_code_review` | Code quality findings by severity with file/line references |
| `mule_security_review` | Security findings classified critical/high/medium/low |
| `mule_read_transform` | DataWeave scripts from a specific Transform Message component |
| `mule_write_transform` | Updates DataWeave in a Transform Message (requires confirmation) |
| `api_schema_analyze` | Governance diagnostics for RAML, OpenAPI, WSDL, XSD, AsyncAPI, GraphQL |
| `munit_validate_flow_tests` | MUnit structure validation: namespaces, config, assertions, mock coverage |
| `munit_full_review` | Full suite audit: scenario coverage, assertion quality, test duplication |
| `munit_improvement_suggestions` | Cadence recommendations for happy/negative/edge/failure scenarios |
| `summarize_mule_project` | Human-readable text summary including all new boolean flags and diagnostic count |
| `get_mule_project_errors` | Live project diagnostics from Studio problem markers |
| `run_mule_maven_tests` | Runs Maven tests; supports `mavenProfile` (`-P dev`), MUnit filtering (`-Dmunit.test=`), multi-module (`-pl`) |

---

## Typical Workflows

### New Feature: End-to-End Workflow

```
1. /api-spec-review            — validate the RAML/OpenAPI contract first
2. /mule-code-review           — check flow structure, error handlers, naming
3. /mule-security-review       — scan for injection risks and credential exposure
4. /generate-munit-tests       — generate tests for the new flow
5. /deployment-readiness       — confirm all checklist items before push
```

### Investigating a Production Error

```
1. Paste the console output using @console
   → Copilot extracts [Mule Error Summary] automatically
2. Ask: "Why is this error happening and how do I fix it?"
3. Follow up with /error-handling-contract to improve error handling
```

### Pre-Merge Code Review

```
1. /mule-code-review           — code quality and architecture
2. /mule-security-review       — security vulnerabilities
3. /munit_full_review          — test quality and coverage gaps
```

### DataWeave Optimization

```
1. Ask: "Review the Transform Message in getOrdersFlow for performance"
   → Agent uses mule_read_transform automatically
2. /dataweave-best-practices   — comprehensive DW quality review
```

### Batch Job Implementation

```
1. /batch-job-review           — review existing structure or get guidance for new batch job
2. /generate-munit-tests       — generate batch step unit tests and integration test fixture
3. /mule-performance-review    — verify block sizing and aggregator config
```

---

## File Locations Reference

| File | Purpose |
|---|---|
| `com.microsoft.copilot.eclipse.anypoint/templates/mulesoft-agent.agent.md` | Main MuleSoft agent definition — API-led, error handling, DataWeave, logging, connector governance rules |
| `com.microsoft.copilot.eclipse.ui/mulesoft-copilot/.github/agents/mulesoft-engineer.agent.md` | Engineer agent variant — same rules, slightly different framing |
| `com.microsoft.copilot.eclipse.anypoint/templates/copilot-instructions-mule-template.md` | Project-level custom instructions scaffold — copy to `.github/copilot-instructions.md` |
| `com.microsoft.copilot.eclipse.ui/mulesoft-copilot/.github/prompts/*.prompt.md` | All 13 slash command prompt definitions |
| `com.microsoft.copilot.eclipse.ui/src/.../chat/services/MuleConsoleParser.java` | Mule error parser that enriches `@console` output |
| `com.microsoft.copilot.eclipse.ui/src/.../chat/tools/MuleProjectAnalyzer.java` | Core Mule project scanner and review engine |
| `com.microsoft.copilot.eclipse.ui/src/.../chat/tools/MuleProjectAnalysis.java` | Data model for scan results |
| `com.microsoft.copilot.eclipse.anypoint/src/.../MuleSoftMcpPreferencePage.java` | MCP credentials preference page (region dropdown) |
| `com.microsoft.copilot.eclipse.ui/src/.../preferences/CopilotPreferenceInitializer.java` | Default preferences (console and workspace context now `true`) |
