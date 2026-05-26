---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - mulesoft/search_asset
---
# Connector Governance Review

Run `mule_project_scan` to collect the Mule runtime version and the full list of connector dependencies from the POM. Then review global connector configurations in Mule XML.

## Version Compatibility
- Every connector version must be compatible with the project's Mule runtime version. Mulesoft publishes a compatibility matrix on the documentation site.
- Flag connectors pinned to EOL versions: HTTP Connector v1 (use v2+), File Connector v1 (use File Connector v2), FTP Connector v1 (use FTP v2), Database Connector v1 (use DB Connector v8+).
- Minor version mismatches between connectors (e.g., HTTP 1.5.x used with Mule 4.4.x when 1.7.x is available) should be flagged as upgrade opportunities.
- Use `mulesoft/search_asset` to look up the latest patch version of a connector in Exchange when a version upgrade is recommended.

## Redundant or Duplicate Connectors
- Flag POM dependencies that import two versions of the same connector (e.g., `mule-http-connector` appears twice at different versions). Only one version can be active at runtime.
- Flag Mule XML that defines multiple global HTTP Request configurations pointing to the same base URL/host — consolidate into one reusable config.
- Flag duplicate Database global configurations with identical JDBC URL. Each logical database should have exactly one global config.

## Connection Pooling
- Database connector global configs must set `minPoolSize`, `maxPoolSize`, and `maxWait`. Missing pool config defaults to unlimited connections, which exhausts the DB under concurrent load.
  - Recommended baseline: `minPoolSize=2`, `maxPoolSize=10`, `maxWait=5000` (adjust per load profile).
- JMS/ActiveMQ connector should set consumer thread count and prefetch appropriate to the processing throughput.
- HTTP Request config should set `maxConnections` and `connectionIdleTimeout` to prevent connection starvation.

## Timeout and Retry Strategy
- Every HTTP Request connector config must have `responseTimeout` set. Flag configs with no timeout (defaults to no timeout, blocking threads indefinitely on upstream hang).
- Database operations that may run long (bulk inserts, complex queries) should use `queryTimeout` on the operation, not just rely on connection pool wait.
- Retry: `reconnection-strategy` with `reconnect` (finite retries) is appropriate for transient connectivity loss. Flag `reconnect-forever` in production — it can consume a thread indefinitely.
- `until-successful` scope for retry logic: `maxRetries` and `millisBetweenRetries` must always be set. Flag `until-successful` without both attributes.

## Authentication Method Consistency
- HTTP connectors to the same upstream service should use the same authentication type. Flag a project where one flow uses OAuth Bearer to call Service X and another uses Basic Auth to the same service.
- Prefer OAuth 2.0 Client Credentials over Basic Auth for machine-to-machine integrations. Flag Basic Auth usages to external APIs where OAuth is available.
- API key authentication should use headers, not query parameters — query parameters appear in server access logs. Flag `apiKey` passed as a query parameter.
- Salesforce connector: prefer OAuth JWT Bearer (server-to-server) over username/password in production. Flag username/password OAuth flows if the project is production-bound.

## Deprecated and Risky Connectors
- **Scripting Module (groovy/js/python scripts)**: Scripting components execute arbitrary code and are a security risk. Flag usage and recommend DataWeave or Java Module with a typed interface instead.
- **Java Module with `java:invoke-static`**: Calling static methods on third-party libraries bypasses Mulesoft's connector contract. Flag usage and note that library upgrades can silently break the integration.
- **VM Connector for cross-application communication**: VM queues are in-memory and not clustered by default in CloudHub. Use Anypoint MQ or JMS for reliable cross-application messaging.

## Output
Return findings grouped by connector: connector name, version in use, recommended version, configuration issues, authentication issues, and specific XML attribute changes needed.
