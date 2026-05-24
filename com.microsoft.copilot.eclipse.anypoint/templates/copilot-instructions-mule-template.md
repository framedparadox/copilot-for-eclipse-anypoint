# Copilot Instructions — MuleSoft Mule 4 Project

<!-- Copy this file to .github/copilot-instructions.md in your Mule project and customize each section. -->

## Project Context

- **Mule runtime version**: 4.x (update to match mule-artifact.json `minMuleVersion`)
- **API-led layer**: Experience API / Process API / System API *(choose one)*
- **Deployment target**: CloudHub 1.0 / CloudHub 2.0 / Runtime Fabric / On-premises *(choose one)*
- **Primary connector(s)**: HTTP, Database, Salesforce, Anypoint MQ *(list the connectors in use)*

## Coding Conventions

- Flow names use camelCase verb-noun format: `getCustomerByIdFlow`, `processOrderFlow`.
- Sub-flow names use the same convention with a descriptive qualifier: `validateOrderSubFlow`.
- All environment-specific values use `${property.name}` placeholders resolved from `config-<env>.yaml`.
- All sensitive values (passwords, tokens, keys) use `${secure::property.name}` backed by the Mule Secure Configuration Properties module.
- DataWeave scripts declare `output` type on every transform. Optional fields use `default` operator: `payload.name default ""`.

## Error Handling

- Every HTTP-facing flow has an `<on-error-propagate>` with typed matchers (e.g., `type="HTTP:CONNECTIVITY"`).
- All error handlers log `correlationId`, `flow.name`, `error.errorType`, and `error.description` in structured JSON format.
- Error responses follow this shape: `{ "code": "...", "message": "...", "correlationId": "..." }` with the correct HTTP status code (400/401/403/404/500/503).
- Correlation IDs are set at the HTTP Listener from the `X-Correlation-ID` header (fallback `uuid()`) and propagated in all outbound HTTP Request headers.

## Logging

- INFO level: flow entry/exit with `correlationId`, `flowName`, and key input identifiers. No full payload logging at INFO.
- DEBUG level: connector call details and DataWeave diagnostics. Disabled in production (log4j2.xml root level = INFO).
- Never log passwords, tokens, API keys, or PII fields without masking.
- Log format: structured JSON via DataWeave `output application/json` in Logger `message` expressions.

## Testing

- Every public flow (HTTP Listener, Scheduler, MQ listener) has MUnit tests covering: happy path, invalid input (400), connector failure simulation, and error-response contract.
- All external connectors are mocked with `munit-tools:mock-when` by `doc:name`. Sub-flows are not mocked.
- Each `<choice>` router branch has its own test, including the otherwise branch.
- Maven command to run all tests: `mvn test`
- Maven command to run a single suite: `mvn test -Dmunit.test=<suite-name>.xml`

## Connector Preferences

- HTTP connector version: <!-- e.g., 1.9.x -->
- Database connector: <!-- e.g., 1.14.x — all queries use parameterized syntax -->
- Anypoint MQ: <!-- version and acknowledgement mode -->
- *(Add other connectors and their versions here)*

## API Specification

- API spec format: RAML 1.0 / OpenAPI 3.0 *(choose one)*
- API spec location: `src/main/resources/api/api.raml` *(or update path)*
- Security scheme: OAuth 2.0 Client Credentials / API Key / None *(choose one)*
