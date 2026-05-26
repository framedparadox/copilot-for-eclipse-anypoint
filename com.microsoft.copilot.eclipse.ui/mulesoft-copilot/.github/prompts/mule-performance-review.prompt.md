---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - mule_read_transform
---
# MuleSoft Performance Review

Run `mule_project_scan` first to identify the runtime version, connectors, and batch jobs. Then run `mule_code_review` with reviewType `performance`. Use `mule_read_transform` to inspect DataWeave scripts in Transform Message components.

## DataWeave and Payload Handling
- Payloads larger than 1 MB should use streaming rather than materializing the full payload in memory. Flag any `output application/json` or `output application/xml` transforms that do not set `streaming=true` where size is unknown at design time.
- Flag nested `map` calls over large collections. These are O(n²) or worse. Prefer a single-pass `map` with a nested `reduce` or a lookup map pre-built with `groupBy`.
- `write()` and `read()` calls that convert between formats unnecessarily inflate memory. Flag DataWeave that serializes then immediately re-parses.
- Flag inline regex patterns inside `map` loops — compile regex outside the loop via a variable.

## Batch Processing
- Batch job step size (records per block) should balance memory pressure and throughput. The default (100) is often too small for high-volume jobs and too large for memory-constrained runtimes. Recommend reviewing `maxRecordsPerBlock` against the payload size per record.
- Flag Batch Aggregator steps without explicit `streaming="true"` when processing large result sets.
- Flag Batch jobs that have no `On Complete` phase logging — without it, failures are silent.

## Concurrency and Threading
- `maxConcurrency` on a flow defaults to the listener thread pool. For CPU-intensive DataWeave, set `maxConcurrency` to number of CPU cores; for IO-bound flows (HTTP, DB), allow higher values.
- Scatter-gather with `maxConcurrency` equal to the number of routes is fine for small sets. Flag scatter-gather where `maxConcurrency` is not set and route count is dynamic or could exceed 20.
- `until-successful` without `maxRetries` and `millisBetweenRetries` defaults can cause threads to block indefinitely. Flag unconfigured `until-successful`.

## Connector Configuration
- HTTP Request configs without explicit `responseTimeout` and `connectionIdleTimeout` will hold threads open on slow upstreams. Both should be set.
- Database connector configs should have `minPoolSize`, `maxPoolSize`, and `maxWait` configured. Default unlimited pooling exhausts DB connections under load.
- JMS/ActiveMQ connector should have prefetch and consumer count tuned to match processing throughput.
- Flag any connector using `reconnect-forever` without a `blocking="false"` strategy — this can deadlock the flow dispatcher thread.

## Database Queries
- Flag N+1 query patterns: a `<db:select>` inside a `<foreach>` or `<parallel-foreach>` over a collection. Prefer a single bulk query with `IN (...)` or a join.
- Flag missing pagination on `<db:select>` queries that could return unbounded row counts. Use `LIMIT`/`OFFSET` or cursor-based pagination.
- Flag `fetchSize` not set on large result sets — defaults can cause full result materialization in the JDBC driver.

## Logging Volume
- Logger components in tight loops (inside `<foreach>`, `<parallel-foreach>`, Batch steps) at INFO or DEBUG level produce enormous log volume under load. Log entry/exit of the outer flow instead.
- Flag full payload logging at INFO — use DEBUG and structured field extraction instead.

## Caching
- Flag repeated calls to the same external API or DB within a single request that return static or slowly-changing data. Recommend Mule Cache Scope (`<ee:cache>`) with an appropriate TTL.
- Distributed cache (e.g., Redis via Object Store v2) should be used for clustered deployments. In-memory cache is invalidated on worker restart and inconsistent across CloudHub workers.

## Output
Return prioritized findings (critical, high, medium, low), tuning recommendations with specific configuration values, key metrics to monitor (response time, GC pressure, thread pool saturation, connector pool wait), and a suggested load test scenario for the highest-risk flow.
