---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - mulesoft/get_platform_insights
---
# Logging and Observability Review

Run `mule_project_scan` to identify flows, connectors, and log4j2 configuration. Run `mule_code_review` with reviewType `logging`. Use `mulesoft/get_platform_insights` to check available monitoring metrics in Anypoint Platform.

## Correlation ID Strategy
- Correlation IDs enable distributed tracing across systems. Every inbound HTTP Listener must set a correlation ID at the source: use the `X-Correlation-ID` request header if present, otherwise generate one with `uuid()`.
  - Example: `<set-variable variableName="correlationId" value="#[attributes.headers['X-Correlation-ID'] default uuid()]" />`
- The correlation ID must be propagated in all outbound HTTP Request calls as a request header: `X-Correlation-ID: #[vars.correlationId]`.
- Log the correlation ID at flow entry and in all error handlers. Flag Logger components inside main flows that do not include `correlationId`.

## Log Levels
- **ERROR**: Unexpected exceptions that terminate a flow or cause data loss. Includes connector failures after retries exhausted, unhandled exceptions.
- **WARN**: Recoverable issues that may indicate misconfiguration or degraded behavior: retry attempts, missing optional headers, slow upstream responses.
- **INFO**: Flow lifecycle events: entry and exit of public flows with key metadata (correlation ID, input record count, operation name). Should NOT include full payloads.
- **DEBUG**: Connector call details, DataWeave input/output for diagnostic purposes. Must be disabled in production.
- Flag Logger components using `INFO` inside `<foreach>`, `<parallel-foreach>`, or Batch Steps — these produce one log entry per record at high volume. Log at entry/exit of the outer flow instead with count metadata.

## Structured Logging Format
- Log messages should be structured JSON strings rather than free text, to enable log aggregation and search.
  - Preferred: `{"event":"flowEntry","flowName":"getCustomerFlow","correlationId":"#[vars.correlationId]","inputId":"#[payload.customerId]"}`
  - Avoid: `"Processing customer " ++ payload.customerId ++ " for request " ++ vars.correlationId`
- All Logger `message` expressions should use DataWeave to build a JSON object, not string concatenation.
- Flag Logger messages that include raw `payload` or `attributes` objects — these log the entire request/response body including potentially sensitive fields.

## PII and Secrets in Logs
- Flag Logger components that log `payload` fields containing: names, email addresses, phone numbers, SSNs, credit card numbers, passwords, tokens, or API keys.
- Use field masking in the log message DataWeave: `output application/json --- { "email": payload.email[0..2] ++ "***" }`.
- The `mule_security_review` tool flags hardcoded secrets; this review focuses on runtime log data.

## Error Handler Logging
- Every `<on-error-propagate>` and `<on-error-continue>` should include a Logger at ERROR or WARN level with: correlation ID, flow name, error type, and a summary message. Do NOT log the full error payload.
- Flag error handlers with no Logger component — silent error handling makes production diagnosis impossible.
- Recommended error log format: `{"event":"flowError","flowName":"#[flow.name]","correlationId":"#[vars.correlationId default 'none']","errorType":"#[error.errorType]","errorMessage":"#[error.description]"}`

## Log4j2 Configuration
- Production `log4j2.xml` should set root logger to `INFO`. Flag `DEBUG` or `TRACE` at root level — these flood logs with Mule internals.
- CloudHub log forwarding to external aggregators (Splunk, ELK) requires the async appender to be configured. Flag missing async appender for high-throughput applications.
- JSON layout appender preferred for machine-parseable logs: `<JsonTemplateLayout eventTemplateUri="classpath:EcsLayout.json" />` or similar.

## Anypoint Monitoring and Metrics
- Use `mulesoft/get_platform_insights` to verify that the application has Anypoint Monitoring enabled in the target environment.
- Flag applications deployed without Anypoint Monitoring — no visibility into response times, error rates, or connector health.
- Custom metrics can be emitted from Mule flows using the Anypoint Monitoring Custom Metrics feature. Recommend adding custom metrics for: processing time per record, error counts by type, integration payload sizes.
- CloudHub 2 and Runtime Fabric deployments should also expose a `/metrics` endpoint compatible with Prometheus scraping if the operations team uses Prometheus/Grafana.

## Output
Return findings grouped by category: correlation ID gaps, log level violations, PII/secrets exposure risks, missing error handler logging, log4j2 configuration issues, and monitoring gaps. Include the specific Logger or flow element reference, the issue, and the corrected Logger message expression.
