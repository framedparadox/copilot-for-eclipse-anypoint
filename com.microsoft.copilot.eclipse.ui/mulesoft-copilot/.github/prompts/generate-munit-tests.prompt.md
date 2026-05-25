---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - mule_read_transform
  - munit_validate_flow_tests
  - munit_full_review
  - munit_improvement_suggestions
  - get_mule_project_errors
  - run_mule_maven_tests
  - mulesoft/generate_or_modify_munit_test
---
# Generate MUnit Tests

Run `mule_project_scan` to identify flows and existing MUnit suites. Run `munit_validate_flow_tests` on existing suites first to understand current coverage gaps. Then use `mulesoft/generate_or_modify_munit_test` to create or update tests.

## Required Coverage for Every Flow
- **Happy path**: Valid input, all connectors succeed, expected payload/variable in response.
- **Negative path**: Invalid or missing input returning correct error response (correct HTTP status and error body).
- **Error path**: Simulated connector failure (e.g., `munit:mock-when` with `thenFail`) verifying On Error Propagate behavior and error response shape.
- **Boundary/edge data**: Empty collections, null optional fields, maximum length strings, zero-value numerics.

## Mocking Strategy
- Mock every external connector call: HTTP Request, Database, Salesforce, MQ, etc. Use `munit:mock-when` with `munit:with-attributes` to match the specific processor by `doc:name` or flow path.
- Do NOT mock sub-flow calls — test sub-flows through the parent flow invocation. Mock only connectors that reach outside the Mule runtime.
- For scheduler-triggered flows, use `munit:run-flow` to invoke the flow directly; do not rely on scheduler firing in tests.

## Choice Router Branch Coverage
- Each `<choice>` router requires one test per when-condition plus one test for the otherwise branch.
- Flag test suites that cover the default route only or only a subset of branches.

## Scatter-Gather Testing
- Each route in a scatter-gather must be independently mocked. Verify the aggregated payload contains contributions from all routes.
- Add one test with a failing route to confirm the scatter-gather error handler behavior.

## Batch Job Testing
- Unit-test individual Batch Step flows in isolation via `munit:run-flow`.
- Integration-test the full batch job with a small fixture dataset (3–5 records) including: one valid record, one record that triggers a step failure, and one boundary record.
- Verify the On Complete phase logging and output variables.

## Async Flow Testing
- Flows using VM Publish-Consume or async scopes: use `munit:run-flow` and then poll/assert the VM queue or output variable with a reasonable timeout.
- For purely async flows (VM Publish with no response), assert side effects: DB records written, MQ messages published (via mock verify-call), or variables set.

## Transactional Flows
- Test rollback: mock the second connector in a try scope to fail, verify the first connector's write was rolled back (assert mock was called, DB record not committed).
- Verify the error handler returns the correct HTTP status and body when a transaction rolls back.

## Correlation ID Propagation
- Every test that simulates an inbound HTTP request should set a `MULE_CORRELATION_ID` attribute on the mock message source.
- Assert that Logger calls within the flow include the correlation ID in structured output.

## Test Naming Convention
- Use descriptive test names that state intent: `given_validRequest_when_getCustomer_then_returns200`, or shorter `getCustomer_validId_returns200`.
- Avoid names like `test1`, `happyPath`, or the flow name alone.

## Validation
- After generating, validate the suite with `munit_validate_flow_tests`. Address any missing MUnit namespaces, munit:config, execution, assertion, or mock issues before finalizing.
- Run `run_mule_maven_tests` to confirm all tests pass.
- Include the Maven command to run only this suite: `mvn test -Dmunit.test=<suite-name>.xml`.
