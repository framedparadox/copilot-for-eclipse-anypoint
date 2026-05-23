---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - api_schema_analyze
---
# API-Led Architecture Review

Run `mule_project_scan` on each project involved. Ask the user which API-led layer this project is intended to implement (Experience, Process, or System) if it is not obvious from the project name or flow names.

## Layer Definitions
- **Experience API (xAPI)**: Consumer-facing. Exposes tailored endpoints for a specific channel (mobile, web, partner). Orchestrates by calling Process APIs. Must not call System APIs directly or other Experience APIs.
- **Process API (pAPI)**: Orchestration layer. Combines data from multiple System APIs to implement a business process. Must not be called by other Process APIs in a chain — flatten the orchestration instead.
- **System API (sAPI)**: One-to-one backend adapter. Exposes a single backend system (Salesforce, SAP, database) via a standard REST/SOAP interface. Contains no business logic. Must not call Process or Experience APIs.

## Layer Identification
- **Experience API indicators**: APIkit router present, endpoint URLs contain consumer-oriented resources (e.g., `/orders`, `/profile`), response shapes tailored for a channel, HTTP listener on a public port.
- **Process API indicators**: Multiple outbound HTTP Request connectors calling different System APIs, aggregation/transformation logic, no direct backend connector (DB, Salesforce, JMS) calls.
- **System API indicators**: Exactly one backend connector (DB, Salesforce, MQ, SFTP, SAP), thin transformation layer, endpoint URLs closely mirror the backend resource names.

## Call Direction Violations (Flag as High)
- Experience API calling a System API directly (skipping the Process layer) — couples consumer contracts to backend implementation details.
- System API calling another System API — creates hidden dependencies between backends.
- System API calling a Process API — inverts the dependency graph.
- Circular references: any flow-ref or HTTP call that eventually calls back into the same application.

## How to Detect Call Direction
- From `mule_project_scan` output: check `connectors` list. If a project claims to be a System API but has multiple outbound HTTP connectors calling different hosts, it may be doing Process API work.
- Outbound HTTP Request configs pointing to internal API base URIs (e.g., `/api/v1/`) rather than backend systems suggest cross-API calls. Flag these for review.
- `mule_code_review` findings on duplicate global configs or duplicate flow logic often signal that System API responsibilities have leaked into Process/Experience layers.

## Naming Conventions
- Flow names should reflect the layer: Experience APIs use consumer-action naming (`getProductsByCategory`), Process APIs use business-process naming (`processOrderFulfillment`), System APIs use backend-operation naming (`queryCustomerFromSalesforce`).
- API spec `title` and `version` should include the layer indicator: `Customer Experience API v2` vs `Customer System API v1`.
- Project name should follow the pattern: `<domain>-<layer>-api` (e.g., `order-process-api`, `customer-system-api`).

## Shared Resources
- Global connector configs (DB, Salesforce, MQ) belong in System APIs only. If a Process or Experience API contains connector configs for backend systems, the System API layer is missing.
- Shared DataWeave modules (`.dwl` files) used across layers should live in a separately versioned Exchange asset, not copied between projects.

## APIkit Validation
- Run `api_schema_analyze` on the API spec. The spec should reflect the layer's consumer contract, not the backend data model.
- System API specs should closely mirror the backend resource vocabulary. Process/Experience API specs should use business vocabulary regardless of how backends name their data.

## Output
Return: identified layer (or ambiguous if unclear), layer-specific findings (call direction violations, naming issues, connector placement), and recommended refactoring steps to restore layer boundaries.
