---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - mule_read_transform
  - munit_validate_flow_tests
  - run_mule_maven_tests
---
# Batch Job Review

Run `mule_project_scan` first. Check `hasBatchJob` in the scan output. If `hasBatchJob=false`, this prompt does not apply — inform the user there are no batch jobs in the project. If `hasBatchJob=true`, proceed with the review below.

## Batch Job Structure
Every Mule 4 batch job must have:
- `<batch:job>` with a `name` attribute.
- `<batch:input>` phase: defines the data source (DB query, file read, MQ consumer). Must return an iterable collection — flag if the input produces a single non-iterable object.
- At least one `<batch:step>` with a meaningful `name`.
- `<batch:on-complete>` phase: logs job summary (total records, success count, failure count). Flag if missing — silent batch completion makes production monitoring impossible.

## Record Block Sizing (`maxRecordsPerBlock`)
- Default `maxRecordsPerBlock` is 100. For most integrations this is too small (low throughput) or too large (high memory per block).
- Rule of thumb: `maxRecordsPerBlock` × (average record size in bytes) should not exceed 10 MB.
  - For small records (< 1 KB): `maxRecordsPerBlock=500–1000` is appropriate.
  - For large records (> 10 KB): `maxRecordsPerBlock=10–50` is safer.
- Flag jobs where `maxRecordsPerBlock` is not set (uses default 100 without intentional sizing).

## Step-Level Error Handling
- Each `<batch:step>` should have an `acceptPolicy` or filter expression to skip records that fail validation before processing.
- Connector failures inside a step mark the record as failed. The job continues processing remaining records by default. Verify this behavior is intentional — flag steps where a connector failure should abort the entire job instead.
- Add `<on-error-continue>` inside the step scope when per-record failures should be tracked but not abort the job. Add `<on-error-propagate>` when a single failure must stop all processing.
- The `<batch:on-complete>` phase receives `batchJobInstanceId`, `loadedRecords`, `successfulRecords`, `failedRecords`, and `elapsedTimeInMillis`. All should be logged.

## Batch Aggregator
- `<batch:aggregator>` collects records into a buffer before writing. Use for bulk database inserts, bulk API calls, or file writes.
- Set `size` on the aggregator to match the target system's bulk operation limit (e.g., Salesforce upsert max 200 records, DB bulk insert batch size).
- For large aggregated payloads, use `streaming="true"` on the aggregator. Without streaming, the full buffer is materialized in memory.
- Flag aggregators without explicit `size` — they default to the full block which may exceed the target system's limit.

## DataWeave Inside Batch
- Use `mule_read_transform` on Transform Message components inside batch steps.
- DataWeave transforms inside batch steps run per record. Flag: nested map over sub-collections (O(n) per record × n records = O(n²) total), regex compiled inline (compile outside the step via a variable), and `write()`/`read()` round-trips that are unnecessary.
- For large record fields, use `output application/json streaming=true` to avoid materializing the record in heap.

## Testing Batch Jobs
- Unit-test individual batch steps by invoking the step's flow directly via `munit:run-flow` with a single fixture record.
- Integration-test the full batch job with a small fixture dataset containing:
  - One valid record (verifies happy path).
  - One record that triggers a step failure (verifies failure counting and `on-complete` logging).
  - One boundary record (empty field, null, max-length string).
- Verify `On Complete` phase: assert `failedRecords` count and log output.
- Use `munit_validate_flow_tests` after generating tests.

## Output
Return: batch job structure findings (missing on-complete, unsized blocks, unsized aggregators), step-level error handling gaps, DataWeave performance risks, and recommended MUnit fixture scenarios.
