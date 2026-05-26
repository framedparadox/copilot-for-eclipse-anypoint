---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - munit_validate_flow_tests
  - get_mule_project_errors
---
# Async Flow Review

Run `mule_project_scan` first. Check `schedulerFlows` for scheduler-triggered flows and `connectors` for VM or Anypoint MQ connectors. If neither is present, async patterns may still exist via async scopes — proceed with the review below.

## Scheduler-Triggered Flows
- Scheduler flows must not block for long periods. All downstream connector calls should have timeouts configured.
- Flag scheduler flows without error handlers — an uncaught exception in a scheduler flow produces a `MULE:UNKNOWN` error with no response to return, making it silent unless logged.
- Scheduler flows with correlation IDs: since there is no inbound HTTP request, the correlation ID should be generated with `uuid()` at the start of the flow.
- Check `schedulerFlows` from the scan. For each scheduler flow: verify it has an error handler, a correlation ID set-variable, and a Logger at INFO on entry.

## Async Scope (`<async>`)
- The `<async>` scope runs its processors in a separate thread without blocking the main flow. Use when a side effect (audit log, notification) should not delay the response.
- Flag `<async>` scopes that contain business-critical logic (DB writes, external API calls that the caller depends on) — if the async thread fails, the main flow is unaware.
- `<async>` scopes cannot propagate errors to the parent flow. Any error handler inside `<async>` must handle the error completely. Flag `<async>` scopes with no internal error handler.
- Do not use `<async>` to call downstream APIs where the caller needs a response — use synchronous flow-ref or HTTP request instead.

## VM Connector Patterns
- VM queues are in-memory and not clustered by default in CloudHub. For production cross-application messaging, use Anypoint MQ or JMS instead.
- VM `publish` + `consume` within the same application is acceptable for decoupling processing stages.
- Flag VM `publish` without a corresponding VM listener flow — published messages will accumulate with no consumer.
- VM listeners must have error handlers. An unhandled error in a VM listener logs an error and discards the message with no retry.
- For reliable messaging: use `transactional="true"` on the VM publish if the source operation should roll back when the consumer fails.

## Anypoint MQ Patterns
- Anypoint MQ listener flows must have error handlers. Unacknowledged messages return to the queue and will be redelivered (causing duplicate processing) if error handling does not ack or nack explicitly.
- Use `<anypoint-mq:ack>` on success and `<anypoint-mq:nack>` on failure when `acknowledgementMode="MANUAL"` is set.
- Flag MQ listener flows with `acknowledgementMode` not set (defaults to AUTO) where business logic can fail after ack — message loss risk.
- Dead-letter queues should be configured on the MQ destination for messages that fail after max redeliveries.

## Thread Pool Impact
- Async scopes and MQ/VM listeners consume threads from the `IO` or `CPU_LITE` thread pool depending on the operation type.
- Heavy DataWeave transformations inside async scopes should be run in a `CPU_INTENSIVE` pool — wrap them in `<ee:async>` (Enterprise Edition) or annotate flows with `processingStrategy`.
- Flag async scopes with nested HTTP calls — these block an IO thread while waiting for the response.

## Graceful Shutdown
- Scheduler flows stop automatically on application shutdown. For VM/MQ listeners, in-flight messages should complete before shutdown. Configure `shutdownTimeout` on the Mule runtime if processing time per message can exceed the default 5-second shutdown window.
- Flag applications with MQ listeners and no `shutdownTimeout` setting where message processing can take more than 5 seconds.

## Testing Async Flows
- Scheduler-triggered flows: invoke directly with `munit:run-flow` — do not rely on the scheduler firing in tests.
- VM publish/consume: publish a test message to the VM queue in `munit:execution`, then use `munit:assert-that` on the side effect (DB record, variable) after a brief wait or `munit:run-flow` on the listener directly.
- Anypoint MQ: mock the MQ connector with `munit:mock-when` matching `doc:name`. Assert the downstream processing result.
- Verify correlation ID appears in Logger calls inside the async/listener flow using `munit-tools:verify-call`.

## Output
Return findings grouped by pattern (scheduler, async scope, VM, MQ): missing error handlers, correlation ID gaps, thread pool risks, acknowledgement mode issues, and recommended test scenarios for each async entry point.
