---
mode: agent
tools:
  - api_schema_analyze
  - mule_project_scan
  - mulesoft/generate_api_spec
  - mulesoft/create_and_manage_assets
---
# API Specification Review

Run `api_schema_analyze` on the target API spec file. If a Mule project is available, run `mule_project_scan` to cross-reference the spec against APIkit router flows.

## Required Spec Metadata
- Title, version, and base URI (RAML: `title`, `version`, `baseUri`; OpenAPI: `info.title`, `info.version`, `servers`).
- Contact, license, and description fields should be present for published Exchange assets.
- Flag missing or placeholder values (e.g., `version: "1.0"` with no semantic versioning, `baseUri: http://example.com`).

## Schema Quality
- Request and response bodies must reference named schema types, not inline anonymous objects. Inline schemas prevent reuse and make clients harder to generate.
- All reusable types should be defined in a `types` section (RAML) or `components/schemas` (OpenAPI) — not duplicated across endpoints.
- Required fields must be explicitly declared. Flag schemas with no `required` array (OpenAPI) or all-optional fields (RAML) on POST/PUT request bodies.
- Enums should be used for fields with a fixed value set. Avoid free-string fields where a constrained list is appropriate.

## Examples
- Every request body and response body must have at least one example. Examples validate the spec is usable and enable mocking.
- Examples must be valid against their schema. Flag examples that do not match the declared types or required fields.

## Error Responses
- All endpoints must declare at minimum: 400 (bad request), 401 (unauthorized), 404 (not found), 500 (internal error).
- Error response bodies should reference a shared error schema (e.g., `ErrorResponse` type) with fields `code`, `message`, and optionally `details`.
- Flag endpoints with only a 200 response defined — partial spec coverage misleads consumers.

## Security Definitions
- A security scheme must be defined at the spec level: OAuth 2.0, API Key, or HTTP Basic. Flag specs with no security scheme.
- Security scheme must be applied to all non-public endpoints. Flag endpoints with no `securedBy` (RAML) or no `security` (OpenAPI).
- OAuth 2.0 scopes should be listed with descriptions. Generic `read`/`write` scopes are acceptable minimums, but resource-specific scopes are preferred.

## APIkit Compatibility
- If a Mule project is available: compare spec endpoint list against APIkit router flow names. Flag spec endpoints missing a router flow and router flows missing a spec endpoint.
- RAML: verify that `baseUri` and `version` are compatible with the APIkit router configuration (`api.raml` path in router config).
- Flag RAML `uses:` references to Exchange libraries that are not pinned to a specific version — unversioned dependencies can break on Exchange republish.

## Versioning
- API version must be in the URL path (e.g., `/v1/customers`) for REST APIs. Header-based versioning is acceptable but must be documented and consistently applied.
- Breaking changes (removing fields, changing types, renaming endpoints) require a new major version. Flag any spec that removes or narrows a previously defined field without a version bump.

## Output
Return: contract summary (endpoints, schemas, security), governance findings by severity, APIkit compatibility issues if project was scanned, and specific recommendations for each finding.
