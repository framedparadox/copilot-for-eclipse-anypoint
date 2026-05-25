---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - get_mule_project_errors
---
# Error Handling Contract Review

Run `mule_project_scan` first. The scan now returns `flowErrorHandlerTypes` (typed/catch-all/none per flow) and `flowsWithCorrelationId`. Use these to focus the review on flows that are missing typed handlers or correlation IDs.

## Required Error Handler Presence
- Every flow exposed via HTTP Listener, Anypoint MQ listener, or Scheduler must have its own `<on-error-propagate>` or `<on-error-continue>`. A global default error handler is a fallback, not a substitute.
- Flag flows where `flowErrorHandlerTypes` shows `"none"` — these will expose raw Mule stack traces on failure.
- Flag flows where `flowErrorHandlerTypes` shows `"catch-all"` — catch-all handlers mask errors and make debugging difficult. Typed handlers are required.

## On Error Propagate vs. On Error Continue
- `<on-error-propagate>`: re-throws the error after the handler runs. Use for all HTTP-facing flows — the caller needs a proper error response.
- `<on-error-continue>`: swallows the error and lets the flow complete "successfully." Use only when failure of a step is truly optional (e.g., best-effort audit logging, non-critical enrichment). Never use as a default catch-all.
- Flag any `<on-error-continue>` without a `type` attribute — this is a catch-all that silences all errors.

## Typed Error Matching
- Error handlers must declare typed matchers: `type="HTTP:CONNECTIVITY"`, `type="DB:QUERY_EXECUTION"`, `type="MULE:EXPRESSION"`, etc.
- Multiple types can be combined with a comma: `type="HTTP:CONNECTIVITY, HTTP:RESPONSE_VALIDATION"`.
- Flag `<on-error-propagate>` or `<on-error-continue>` elements with no `type` attribute on HTTP-facing flows.
- Common Mule 4 error type namespaces: `HTTP`, `DB`, `SALESFORCE`, `JMS`, `VM`, `MULE`, `APIKIT`, `VALIDATION`.

## Correlation ID in Error Handlers
- Every error handler in an HTTP-facing flow must log the correlation ID. Without it, production incidents cannot be traced across systems.
- Check that `flowsWithCorrelationId` from the scan includes all public flows. If a flow is missing from that set, flag it.
- Required Logger format in error handlers:
  ```
  #[output application/json --- {
    "event": "flowError",
    "flowName": flow.name,
    "correlationId": vars.correlationId default "none",
    "errorType": error.errorType,
    "errorMessage": error.description
  }]
  ```

## Consistent Error Response Shape
- All HTTP-facing `<on-error-propagate>` handlers must set the HTTP status code explicitly via `<http:response statusCode="...">` or via an `<ee:transform>` that sets the appropriate status variable.
- Error responses must follow a consistent JSON shape:
  ```json
  { "code": "ERROR_TYPE", "message": "Human-readable message", "correlationId": "..." }
  ```
- Flag flows that return raw Mule error descriptions (`error.description`) directly as the response body — these expose internal stack trace fragments to API consumers.
- HTTP status code mapping: validation errors → 400, auth failures → 401/403, not found → 404, connector failures → 503, unexpected → 500. Never always return 500.

## Global Error Handlers
- Global `<error-handler>` elements are acceptable as a last-resort fallback (catches errors not caught by flow-level handlers).
- The global handler should log with correlation ID and return a 500 response. It should NOT be the primary handler for known error types.
- Flag projects where the only error handler is a global one — this indicates no per-flow error handling exists.

## Output
Return findings grouped by flow: flow name, current error handler type (from scan data), missing typed matchers, correlation ID gap, and the corrected error handler XML snippet.
