---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - mule_read_transform
  - get_mule_project_errors
---
# MuleSoft Code Review

Run `mule_project_scan` first to establish the project baseline. Then run `mule_code_review` across Mule XML, DataWeave, properties, API specs, MUnit suites, and POM metadata.

## Flow Structure
- Flows should use camelCase verb-noun naming (e.g., `getCustomerByIdFlow`, `postOrderFlow`). Sub-flows use the same convention with a descriptive qualifier.
- Prefer sub-flows for reusable logic called from multiple flows. Use private flows only for asynchronous branching (async scope, VM).
- Every flow exposed via HTTP or a message source must have an On Error Propagate at the flow level with at least one specific error type. Do not rely solely on global default error handlers.
- On Error Continue is appropriate only when the flow must complete successfully despite the error (e.g., optional enrichment steps). On Error Propagate re-throws and should be the default for public-facing flows.
- Correlation IDs must be set at the HTTP Listener or message source (e.g., `correlationId` attribute), logged at flow entry, and propagated through all flow-refs and async calls.

## Global Configuration
- No duplicate global configs. One `<http:request-config>`, one `<db:config>`, etc. per logical target. Duplicates cause confusion and runtime precedence issues.
- All sensitive values (passwords, tokens, client secrets) must use `${secure::property.name}` — never plain `${property.name}` and never hardcoded values in XML.
- All environment-specific values (hosts, ports, paths) must use `${property.name}` with corresponding `config-<env>.yaml` or `.properties` files.

## DataWeave
- Read DataWeave scripts with `mule_read_transform` before recommending changes.
- Output type must be declared (`output application/json`, `output application/xml`, etc.).
- Null-safe patterns required: use `default` operator for optional fields (e.g., `payload.name default "Unknown"`).
- Prefer `map`, `filter`, `reduce` over `if/else` imperative patterns. Flag nested `map` calls on large collections as performance risks.

## MUnit Coverage
- Every public flow (HTTP listener, scheduler, connector source) should have at least one MUnit test.
- Flag flows with zero test coverage. Flag suites that test only happy-path without any error-path or connector-failure scenario.

## APIkit
- If APIkit router is present, verify every endpoint in the RAML/OpenAPI spec has a corresponding router flow (`get:\resource:api-config` naming pattern).
- Flag router flows that exist in XML but have no corresponding spec endpoint (orphaned routes).

## Output
Prioritize findings as critical, high, medium, low. For each finding: file reference, line or element, issue, recommended fix, and a validation command (e.g., Maven test or Studio validation).
