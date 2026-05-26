# MuleSoft Copilot Chat Improvements

**Date**: May 21, 2026  
**Scope**: Enhanced agent instructions, prompt files, and tool descriptions for Mulesoft/Anypoint Studio integration  
**Impact**: Domain-specific guidance for niche Mulesoft platform; addresses gaps in API-led architecture, DataWeave, error handling, security threats, logging, connector governance, and deployment

---

## Why These Changes

The GitHub Copilot Chat plugin was already well-integrated with Mulesoft (14 local tools, MCP server, 6 prompts), but because **Mulesoft is a niche platform** unlike Python or Java:

1. **Thin prompts**: Original 6 prompts were 4–9 lines, naming what to check but not **how** or **what good looks like**
2. **Generic tool descriptions**: Tools didn't explain *why* each matters for Mulesoft or *when* to invoke them
3. **Missing domain depth**: No guidance on API-led layering, Mulesoft-specific injection attacks (XXE, XPath), DataWeave patterns, or platform-specific deployment
4. **Agent instructions too surface-level**: Mentioned API-led architecture without defining it; error handling without specific patterns

**Result**: The AI had good mechanics but lacked the **Mulesoft expertise** to give developer advice beyond generic integration patterns.

---

## What Changed

### 1. Six Prompt Files Expanded (4–9 lines → 40–70 lines)

Prompt files are templates that guide the AI when a user invokes a slash command like `/mule-code-review`. Each was rewritten with **concrete guidance, Mulesoft anti-patterns, and actionable checklists**.

#### `mule-code-review.prompt.md`
**Purpose**: General code quality review  
**New Content**:
- Flow naming conventions: camelCase verb-noun (e.g., `getCustomerByIdFlow`)
- When to use sub-flows vs. private flows
- Every HTTP-facing flow **must** have an `<on-error-propagate>` handler
- On Error Continue vs. On Error Propagate rules (Continue only for optional enrichment)
- Correlation ID must be set at HTTP Listener and propagated in logs and outbound calls
- Global config deduplication rules
- DataWeave null-safety patterns (`default` operator on optional fields)
- APIkit route coverage — every endpoint in RAML/OpenAPI must have a router flow

**Use it**: User types `/mule-code-review` → Agent gets detailed checklist instead of generic "review flows"

---

#### `mule-security-review.prompt.md`
**Purpose**: Security vulnerability detection  
**New Content** (Mulesoft-specific threats):
- **XPath injection**: Parameterize XPath expressions or reject user input in XPath queries
- **XML External Entity (XXE)**: Secure XML parsers to prevent external entity expansion
- **SQL injection**: Database connector queries must be parameterized (`:variable`), never concatenated
- **Insecure deserialization**: DataWeave `read()` on untrusted input without schema validation
- **Transport security**: HTTPS only on public endpoints, TLS context configured, no `insecure="true"`
- **Authentication**: Every public flow must validate credentials (API key, OAuth, JWT, Basic Auth)
- **Logging safety**: Never log passwords, tokens, PII fields without masking
- **Secure properties**: All secrets must use `${secure::property.name}`, not plain `${property.name}`

**Use it**: User types `/mule-security-review` → Agent scans for Mulesoft-specific injection attacks, not just secrets

---

#### `mule-performance-review.prompt.md`
**Purpose**: Performance and scalability  
**New Content** (Mulesoft-specific optimization):
- **DataWeave streaming**: Payloads > 1 MB should use `streaming=true` to avoid materializing in memory
- **Nested maps (O(n²) anti-pattern)**: Flag and rewrite using `groupBy` for indexing
- **Batch job sizing**: Balance memory pressure vs. throughput; default 100 records/block may be wrong
- **maxConcurrency tuning**: Match CPU cores for compute-bound, higher for IO-bound flows
- **Scatter-gather**: Flag when maxConcurrency is not set and route count is dynamic
- **Database connector pooling**: minPoolSize, maxPoolSize, maxWait must be configured
- **N+1 queries**: Detect DB select inside loops; recommend bulk queries with `IN (...)` or joins
- **Caching**: In-memory cache via `<ee:cache>` for repeated static/slow-changing data

**Use it**: User types `/mule-performance-review` → Agent identifies DataWeave materialization, batch sizing, pooling config issues, not just generic optimization

---

#### `deployment-readiness.prompt.md`
**Purpose**: Pre-deployment validation  
**New Content** (Platform-specific):
- **Universal**: Health endpoint contract, log levels, MUnit pass rate, secure properties
- **CloudHub 1.0**: Worker sizing (minimum `Medium`), persistent queues for VM/MQ, static IP
- **CloudHub 2.0 / Runtime Fabric**: Resource limits, replica count (2+ for HA), liveness probes
- **On-premises**: Cluster config, JVM heap tuning, application path permissions
- **Smoke test checklist**: Post-deployment validation steps (health endpoint, key endpoint, logs, monitoring)

**Use it**: User types `/deployment-readiness` → Agent asks for target platform and returns platform-specific checklist

---

#### `api-spec-review.prompt.md`
**Purpose**: API contract validation  
**New Content**:
- **APIkit binding**: Every spec endpoint **must** have a corresponding router flow; flag orphaned routes
- **Error contract**: Error codes in spec must match error handlers in Mule; error responses must match schema
- **Examples validation**: Examples must be valid against their declared schema
- **Security scheme**: OAuth/API key/JWT must be defined **and implemented** in flows
- **Versioning**: Use URL path (e.g., `/v1/`) not header-based versioning; document breaking changes
- **Named schemas**: Avoid inline anonymous objects; all reusable types in `types` section

**Use it**: User types `/api-spec-review` → Agent validates spec completeness and APIkit router coverage

---

#### `generate-munit-tests.prompt.md`
**Purpose**: Test generation and coverage  
**New Content** (Mulesoft test scenarios):
- **Happy path, negative path, error path, edge data**: Four required scenarios per flow
- **Async flow testing**: Use VM queue polling with timeout; assert side effects (DB writes, MQ publishes)
- **Batch job testing**: Unit-test individual steps, integration-test full batch with 3–5 fixture records including one that fails
- **Scatter-gather**: Mock each route independently; add one test with a failing route
- **Transactional flows**: Mock second connector to fail and verify first connector's write was rolled back
- **Choice router branches**: Each when-condition + otherwise branch needs its own test
- **Correlation ID assertions**: Verify correlation ID is logged in all flow entry/error handler outputs
- **Test naming**: Descriptive names (`getCustomer_validId_returns200`) not `test1`

**Use it**: User types `/generate-munit-tests` → Agent generates tests covering async, batch, scatter-gather, transactional flows—not just happy path

---

### 2. Three New Prompt Files (90–120 lines each)

These address domains that had no dedicated prompt in the original configuration.

#### `dataweave-best-practices.prompt.md`
**Why**: DataWeave is Mulesoft's unique transformation language. No other platform has it. No dedicated quality review existed.

**Content**:
- **Output type declaration**: Mandatory `output application/json` etc.; missing output causes type inference bugs
- **Null-safety**: `default` operator on all optional accesses; no exception-based null handling
- **Functional patterns**: `map`, `filter`, `reduce`, `groupBy` over imperative `if/else` loops
- **Performance anti-patterns**: Nested maps (O(n²)), inline regex compiled per iteration, unnecessary serialization
- **Streaming**: `streaming=true` for unknown-size payloads; know the limitations (no `sizeOf()`, `[-1]`, `reverse()`)
- **Modularity**: Repeated logic extracted to `.dwl` modules in `src/main/resources/dwl/`
- **Type safety**: Document input types; use named type definitions

**Use it**: User types `/dataweave-best-practices` → Agent reviews all Transform Message components for null-safety, performance, and functional patterns

---

#### `connector-governance.prompt.md`
**Why**: Connector configuration was mentioned but never systematically reviewed. Gaps in version compatibility, pooling, and deprecated connectors.

**Content**:
- **Version compatibility**: Check Mulesoft compatibility matrix; flag EOL connectors (HTTP v1, File v1)
- **Redundant connectors**: Flag two versions of same connector in POM or duplicate global configs
- **Connection pooling**: Database/JMS/HTTP must have explicit pool config (minPoolSize, maxPoolSize, maxWait, connectionIdleTimeout)
- **Timeout and retry**: Every HTTP Request must have `responseTimeout`; flag `reconnect-forever` in production
- **Authentication consistency**: Same upstream service should use same auth method across flows
- **Deprecated and risky**: Flag Groovy/JS/Python scripting, Java Module static method calls, VM for cross-app messaging

**Use it**: User types `/connector-governance` → Agent audits connector versions, pooling config, and deprecated patterns

---

#### `logging-observability.prompt.md`
**Why**: Logging was reduced to "redact PII"—no positive guidance on structured logging, correlation IDs, or metrics.

**Content**:
- **Correlation ID strategy**: Set at HTTP Listener from header or generate `uuid()`; propagate in all outbound HTTP headers
- **Log levels**: ERROR for failures, WARN for retries, INFO for flow entry/exit, DEBUG for diagnostics (prod disabled)
- **Structured logging**: JSON format not string concatenation; include `correlationId`, `flowName`, `operation`, metadata
- **PII/secrets**: Never log passwords/tokens/keys; mask if unavoidable (e.g., `email[0..2] ++ "***"`)
- **Error handler logging**: Every `<on-error-propagate>` logs with `correlationId`, `flowName`, `errorType`, `errorDescription`
- **Log4j2 config**: Root logger `INFO` in prod; async appender for high throughput; JSON layout preferred
- **Anypoint Monitoring**: Enable for visibility; custom metrics for performance tracking

**Use it**: User types `/logging-observability` → Agent reviews log4j2, Logger components, and correlation ID propagation

---

### 3. Both Agent Files Deepened (65 lines → 110 lines)

The two main agent definitions (`mulesoft-agent.agent.md` and `mulesoft-engineer.agent.md`) now include concrete sections instead of surface-level guidance.

#### New Sections in Both Agents:

**API-Led Architecture**
```
- Experience API: consumer-facing, routes to Process APIs
- Process API: orchestrates business logic across System APIs
- System API: one-to-one backend adapter, no business logic
Rule: Never let System API call Experience API; preserve boundaries
```

**Error Handling Contract**
```
- All HTTP-facing flows must have <on-error-propagate> with typed error matchers
- <on-error-continue> only for truly optional enrichment steps
- Every error handler logs: correlationId, flow.name, error.errorType, error.description (JSON)
- Error responses: consistent JSON shape { "code", "message", "correlationId" }, correct HTTP status codes
- Correlation ID set at HTTP Listener, propagated in all outbound HTTP headers and logs
```

**DataWeave Standards**
```
- Always read_transform before editing
- Every script must declare output type
- All optional field accesses use default operator
- Prefer map/filter/reduce over imperative patterns
- Flag nested maps over large collections (pre-index with groupBy)
- Streaming: output application/json streaming=true for unknown/large payloads
- Extract repeated logic to .dwl modules
```

**Logging Discipline**
```
- INFO: flow entry/exit with correlationId, flowName, key identifiers (no full payload)
- ERROR: every <on-error-propagate> with correlationId, flowName, errorType, errorDescription
- DEBUG: connector calls, DataWeave diagnostics (disabled in production)
- Never log passwords, tokens, API keys, or PII fields without masking
- Use structured JSON format in Logger message expressions
```

**Connector Governance**
```
- Align versions with Mule runtime compatibility matrix
- Database configs: set minPoolSize, maxPoolSize, maxWait
- HTTP Request configs: set responseTimeout
- Outbound HTTP: HTTPS only, TLS context configured, insecure="true" never allowed
- Retry: finite reconnect with count/frequency, never reconnect-forever in production
- Flag deprecated: HTTP v1, File Connector v1, Scripting Module (Groovy/JS/Python)
```

**MUnit Testing**
```
- Every public flow: happy path, invalid input, connector failure, error-response contract
- Mock all external connectors by doc:name, not sub-flows
- Cover every Choice branch including otherwise
- Run munit_validate_flow_tests after generating
- Use munit_full_review for suite audits before release
```

**Use it**: The agent now asks smarter questions and catches issues automatically because it understands these Mulesoft patterns.

---

### 4. Six Tool Descriptions Enriched (3–4 lines → 8–15 lines)

Tool descriptions appear when the AI considers whether to invoke a tool. Enriched descriptions help the AI know **when and why** to use each tool.

#### Before vs. After Examples:

**MuleProjectScanTool**
- Before: "Scan Mule project structure and metadata"
- After: Includes what data is returned (runtime version, connectors, flows, API specs, MUnit coverage, diagnostics), how to use results (check version compatibility, identify missing test coverage), and when to invoke (before code review or XML edits)

**MuleSecurityReviewTool**
- Before: "Hardcoded secrets, insecure HTTP, missing validation, unsafe logging"
- After: Specific Mulesoft threats (SQL injection via DB connector string concatenation, XXE in XML parsing, XPath injection, parameter masking), common high-severity findings, classification severity levels

**ApiSchemaAnalyzeTool**
- Before: "Governance-oriented diagnostics"
- After: Concrete governance issues (missing metadata, inline schemas vs. named types, missing error responses, no security scheme, examples don't validate against schema), APIkit compatibility checks

**MunitValidateFlowTestsTool**
- Before: "Validate structure and flow coverage"
- After: Specific checks (munit:config presence, assertion elements, mock-when usage, no assertions = always passes, untested Choice branches), use before running Maven

**MunitFullReviewTool**
- Before: "Broad suite reviews"
- After: Full scenario coverage analysis (happy, negative, error, edge, connector-failure, error-contract), assertion quality, mock completeness, branching coverage, test duplication, use before release

---

## How to Use These Improvements

### For End Users (Developers in Anypoint Studio)

All improvements are **automatically available** via slash commands in the chat. No configuration needed.

#### Example Workflow:

1. **New project?** Start with:
   ```
   /mule-code-review
   ```
   Agent runs `mule_project_scan` → `mule_code_review` and returns findings on flow naming, error handlers, global configs, correlation IDs, API-led boundaries

2. **Before pushing to production?**
   ```
   /mule-security-review
   /mule-performance-review
   /deployment-readiness
   ```
   Agent detects injection risks, DataWeave materialization, batch sizing, pooling config, log levels, health endpoints

3. **Writing a DataWeave transform?**
   ```
   /dataweave-best-practices
   ```
   Agent reviews all Transform Message components for null-safety, streaming, functional patterns

4. **Configuring connectors?**
   ```
   /connector-governance
   ```
   Agent audits connector versions, pooling, timeouts, deprecated patterns

5. **Adding logs or monitoring?**
   ```
   /logging-observability
   ```
   Agent reviews correlation ID propagation, log levels, structured format, Anypoint Monitoring setup

6. **Generating MUnit tests?**
   ```
   /generate-munit-tests
   ```
   Agent creates tests covering happy, negative, error, edge, async, batch, scatter-gather, transactional scenarios

---

### For Developers Asking General Questions

The agent instructions now guide the AI to ask smarter clarifying questions:

**Before:**
- User: "How should I structure my error handling?"
- Agent: "Use error handlers. Try/catch blocks are good."

**After:**
- User: "How should I structure my error handling?"
- Agent: "Are this flows HTTP-facing or internal? If HTTP-facing, every public flow needs `<on-error-propagate>` with typed error matchers, correlation ID logging, and consistent error response shape. What's your target platform—CloudHub, Runtime Fabric, or on-prem?"

---

### For Code Review

Agent can now provide **Mulesoft-expert-level** code reviews:

**Before:**
- Review flow: "Flow looks OK. Add error handling."

**After:**
- Review flow: "Flow `getCustomer` lacks on-error-propagate. HTTP Listener must catch connectivity errors from DB connector. Global config `dbConnConfig` is missing pool settings; set minPoolSize=2, maxPoolSize=10, maxWait=5000. Transform Message lacks output directive. Tests missing correlation ID assertion."

---

## Files Modified

| Type | Files | Changes |
|------|-------|---------|
| **Prompts Expanded** | 6 files in `.github/prompts/` | 4–9 lines → 40–70 lines each |
| **Prompts New** | 3 new files in `.github/prompts/` | 90–120 lines each |
| **Agents Deepened** | 2 files: `mulesoft-agent.agent.md`, `mulesoft-engineer.agent.md` | 65 lines → 110 lines each |
| **Tool Descriptions** | 6 Java files in `chat/tools/` | 3–4 lines → 8–15 lines each |

---

## Validation

To verify the improvements:

1. **Open Eclipse Anypoint Studio** with the plugin installed
2. **Open Chat panel** (Copilot icon)
3. **Type `/` to see all commands** — you should see all 9 slash commands (6 original + 3 new prompts):
   - `/mule-code-review`
   - `/mule-security-review`
   - `/mule-performance-review`
   - `/deployment-readiness`
   - `/api-spec-review`
   - `/generate-munit-tests`
   - `/dataweave-best-practices` (new)
   - `/connector-governance` (new)
   - `/logging-observability` (new)

4. **Test a prompt**: Invoke `/mule-code-review` on a sample Mule project
   - Agent should run `mule_project_scan` automatically
   - Agent should mention flow naming conventions, error handlers, global config dedup, correlation ID propagation, APIkit coverage
   - Not generic "review flows" advice

5. **Test tool descriptions**: In the chat, look for tool names in agent output
   - `mule_project_scan` description should mention "runtime version, connector inventory, flow/sub-flow counts, diagnostics"
   - `mule_security_review` should mention "XXE, XPath injection, SQL injection, insecure deserialization"
   - Not generic "scan project" or "review security"

---

## Impact Summary

| Category | Before | After |
|----------|--------|-------|
| **Prompt Depth** | 4–9 lines, naming what to check | 40–70 lines, concrete guidance + anti-patterns |
| **New Prompts** | 0 specialized prompts for DataWeave, connectors, logging | 3 dedicated prompts |
| **Tool Descriptions** | Generic ("review flows", "check security") | Specific ("check flow naming, error handlers, correlation ID", "detect XXE, SQL injection, deserialization risks") |
| **Agent Instructions** | Surface-level mention of API-led, error handling | Detailed rules: layer hierarchy, error contract, DataWeave patterns, logging discipline |
| **Domain Knowledge** | Generic integration patterns | Mulesoft-specific: DataWeave streaming, batch sizing, connector pooling, XXE/XPath/SQL injection, correlation ID propagation |

---

## Example: Before vs. After

### User Request: "Review my Mule flow for security issues"

**Before:**
```
Agent: "Let me review your flows for security issues.
- Check for hardcoded secrets? Yes, I see some.
- Missing secure properties? Yes.
- Insecure HTTP? Yes.
Good luck fixing those. Try using ${secure::property} syntax."
```

**After:**
```
Agent: "Let me scan your project first...
[runs mule_project_scan]

High-risk findings:
1. Database connector query in <db:select> concatenates user input: 
   <db:select>SELECT * FROM customers WHERE id = '${payload.customerId}'</db:select>
   → SQL injection risk. Use parameterized query: SELECT * FROM customers WHERE id = :customerId

2. HTTP Listener on public endpoint has no <on-error-propagate>:
   If DB connector fails, unhandled exception returns 500 with stack trace.
   → Add error handler that catches DB:QUERY_EXECUTION and returns { "code": "DB_ERROR", "message": "Customer lookup failed", "correlationId": "..." }

3. Secure Configuration Properties module not in POM:
   ${secure::dbPassword} won't encrypt values at rest.
   → Add dependency: mule-secure-configuration-property-module

4. HTTP Request to external service has no TLS context:
   <http:request-config ... />
   → Add tlsContext and remove insecure="true"

[Validation commands provided]
Test: mvn test -Dmunit.test=*Test.xml
```

---

## Next Steps for the Team

1. **Commit and merge** these changes to the `dev/mule-support` branch
2. **Test in Eclipse Anypoint Studio** with sample projects
3. **Gather feedback** from developers using the prompts
4. **Iterate** if new Mulesoft-specific patterns emerge (e.g., batch error handling, Runtime Fabric deployment specifics)

---

## Notes

- All improvements are **backward-compatible**. Existing tool registrations and agent setup unchanged.
- New prompts are automatically indexed by the language server and appear in slash command autocomplete.
- Tool descriptions are used by the AI to decide when to invoke tools and how to interpret results—no UI changes needed.
- Agent instructions are loaded into the AI's context at the start of each chat session—developers see improvements immediately.

