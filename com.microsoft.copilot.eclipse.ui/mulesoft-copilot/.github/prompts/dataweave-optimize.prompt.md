---
mode: agent
tools:
  - mule_project_scan
  - mule_read_dwl_file
  - mule_write_dwl_file
  - mule_optimize_dwl
  - mule_read_transform
  - mule_write_transform
  - mulesoft/dataweave_run_script_tool
  - mulesoft/dataweave_create_documentation
---
# DataWeave Optimization

Scan, analyze, and optimize all DataWeave scripts in the project — both inline Transform Message
components and standalone `.dwl` module files.

## Workflow

1. Run `mule_project_scan` to inventory the project. Identify:
   - All Mule XML files with `ee:transform` components
   - All `.dwl` module files in `src/main/resources/dwl/`

2. For each **standalone `.dwl` module**:
   - Read with `mule_read_dwl_file`
   - Run `mule_optimize_dwl` (includeComments=true, applyFixes=false) to preview issues
   - Present findings to the user with type, line, description, and suggested fix
   - If the user approves, apply with `mule_optimize_dwl` (applyFixes=true) or `mule_write_dwl_file`
   - Validate with `mulesoft/dataweave_run_script_tool` using valid, null, and malformed sample inputs

3. For each **inline Transform Message** component:
   - Read with `mule_read_transform` (use transformName or transformId to target specific components)
   - Apply the same optimization checks
   - If issues found and user approves, write with `mule_write_transform`
   - Validate with `mulesoft/dataweave_run_script_tool`

## Optimization Priorities (in order)

1. **Performance** — most impactful changes first:
   - Nested `map`+`filter` → pre-index with `groupBy`, look up in O(1)
   - Inline regex literals inside `map`/`filter` → extract to `var` before the map
   - Round-trip `write()`/`read()` serialization → remove the no-op pair

2. **Null safety** — prevents runtime `NullPointerException` equivalents:
   - Every optional field access must use `default`: `payload.field default ""`
   - Nested chains: each level must be guarded: `payload.order.items[0].price default 0`

3. **Output directive** — prevents runtime type inference issues:
   - Every script must start with `%dw 2.0` and declare `output application/json` (or the correct type)
   - Input types should be declared when upstream content-type is ambiguous

4. **Streaming** — for large or unknown-size payloads:
   - Suggest `output application/json streaming=true` when the script maps over a potentially large array
   - Warn if `sizeOf()`, `[-1]`, or `reverse()` are used in a streaming context

5. **Documentation** — for maintainability:
   - Add `//` or `/** */` comments before undocumented `fun` declarations
   - Describe: purpose, parameters, return type
   - Flag copy-pasted logic appearing in 3+ transforms as a module extraction candidate

## Output Format

For each file reviewed, report:
- File path and component name/ID
- Number of issues found
- For each issue: type, line number, description, suggested fix
- The optimized script (preview or applied)
- Validation command to run with `mulesoft/dataweave_run_script_tool`
