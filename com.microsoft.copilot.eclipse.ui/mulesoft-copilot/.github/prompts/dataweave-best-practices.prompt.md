---
mode: agent
tools:
  - mule_project_scan
  - mule_read_transform
  - mulesoft/dataweave_run_script_tool
  - mulesoft/dataweave_get_project_metadata
  - mulesoft/dataweave_create_documentation
---
# DataWeave Best Practices Review

Run `mule_project_scan` to find Transform Message components. Use `mule_read_transform` on each one before reviewing or rewriting DataWeave scripts.

## Output Type Declaration
- Every DataWeave script must declare an output directive: `output application/json`, `output application/xml`, `output application/java`, etc.
- Missing output directives cause the runtime to infer type, which can produce unexpected results and suppress compile-time errors.
- Input types should be declared when the upstream content-type is ambiguous: `input payload application/json`.

## Null Safety
- Use the `default` operator for every field access that may be absent: `payload.customer.name default "Unknown"`.
- For nested access chains, each level must be null-safe: `payload.order.items[0].price default 0`.
- Prefer `if (payload.field != null)` over `try(() -> payload.field) default null` — the latter silences real errors.
- Flag scripts that access payload fields without null guards when the input schema has optional fields.

## Functional Patterns (prefer over imperative)
- Use `map` to transform each element of an array. Use `filter` to exclude elements. Use `reduce` to aggregate. Avoid `if/else` inside `map` when `filter` pre-passes the array.
- Use `groupBy` to index an array into a map keyed by a field — avoids O(n²) nested `map` with inner `filter` lookups.
- Use `distinctBy` to deduplicate collections before processing.
- Flag `do { var ... }` patterns that re-compute the same expression inside a `map` on every iteration. Pre-compute to a variable outside the `map`.

## Performance Anti-Patterns
- **Nested maps over large collections**: `arrayA map (a -> arrayB filter (b -> b.id == a.id))` is O(n×m). Replace with `groupBy` on `arrayB` and then lookup inside `map` on `arrayA`.
- **Inline regex**: `payload map (item -> item.name matches /^[A-Z].*/)` compiles the regex on every iteration. Extract: `var namePattern = /^[A-Z].*/` and reference it in the map.
- **Unnecessary serialization**: `write(payload, "application/json")` followed immediately by `read(..., "application/json")` is a no-op round-trip. Remove it.
- **Large `output application/java` objects**: materializing large Java maps/lists loses streaming. Use `output application/json` and let the next connector handle deserialization.

## Streaming for Large Payloads
- When processing payloads where size is unknown at design time (file processing, DB result sets, API pagination), use DataWeave streaming: `output application/json streaming=true`.
- Streaming transforms cannot use `sizeOf()`, `[-1]` (last element), or `reverse()` since these require the full collection in memory. Flag these operations in streaming transforms.
- Batch jobs processing large files should use `<batch:job>` with `<batch:input>` reading from a streaming source rather than loading the full file into payload.

## Modularity and Reuse
- Repeated DataWeave logic (date formatting, error response building, field masking) should be extracted to a DataWeave module (`.dwl` file in `src/main/resources/dwl/`) and imported with `import` directive.
- Flag copy-pasted DataWeave snippets that appear in 3 or more Transform Message components — these are candidates for a shared module.
- Use `mulesoft/dataweave_create_documentation` to document complex module functions.

## Type Safety and Documentation
- Complex scripts should document the expected input type as an inline comment: `// Input: { orderId: String, items: Array<{sku: String, qty: Number}> }`.
- Use named types in DataWeave type system for shared structures: `type OrderItem = { sku: String, qty: Number }`.
- When using `mulesoft/dataweave_run_script_tool` to test a script, always test with: a valid input, a null/empty input, and a malformed input to verify null-safety and error handling.

## Output
Return findings per Transform Message component: component name/ID, file reference, issues found, corrected DataWeave snippet, and a test command using `mulesoft/dataweave_run_script_tool` to validate the fix.
