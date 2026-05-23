---
mode: agent
tools:
  - mule_project_scan
  - mule_security_review
  - api_schema_analyze
  - mulesoft/list_api_instances
  - mulesoft/manage_api_instance_policy
---
# MuleSoft Security Review

Run `mule_project_scan` first, then `mule_security_review`. Analyze Mule XML, property files, POM metadata, and API contracts.

## Credentials and Secrets
- All sensitive values (passwords, tokens, client secrets, API keys, certificates) must use `${secure::property.name}` with the Mule Secure Configuration Properties module. Never `${plain.property}` for secrets, never inline values.
- Check `mule-artifact.json` and POM for the `mule-secure-configuration-property-module` dependency. Flag if missing.
- Flag any property file (`.yaml`, `.properties`) that contains values matching secret patterns (password, secret, token, apikey, clientsecret, privatekey). These should be encrypted or externalized.

## Injection Attacks (Mule-Specific)
- **XPath injection**: Any flow using XPATH expressions (e.g., `xpath3()` function, XSLT, XQuery) with user-controlled input must sanitize or parameterize. Hardcoded XPath is safe; concatenated XPath with `attributes.queryParams` or `payload` fields is not.
- **XML External Entity (XXE)**: Flows that parse XML using DataWeave or Java invoke must use secure parser settings. Flag any `java:invoke` or `java:new` calls on XML parsers without explicit `FEATURE_SECURE_PROCESSING` or equivalent.
- **SQL injection**: All Database connector operations must use parameterized queries (`:variable` syntax). Flag any `<db:select>` or `<db:insert>` where the query attribute concatenates flow variables or payload fields as strings.
- **DataWeave deserialization**: Flag `readUrl()` or `read()` calls on untrusted input without schema validation or content-type enforcement.

## Transport and Network Security
- All HTTP Listener configs for non-internal flows must use HTTPS (`<http:listener-config tlsContext="...">`). Flag plain HTTP on public-facing endpoints.
- All HTTP Request configs calling external services must use HTTPS. Flag any `http://` URLs in request configs.
- Outbound HTTP Request configs must have `tlsContext` set and not disable certificate validation (no `insecure="true"`).
- Check for hardcoded IP addresses or internal hostnames — these should use property placeholders.

## Authentication and Authorization
- Public flows receiving external requests must include authentication validation: API key policy, OAuth 2.0, JWT validation, or Basic Auth connector with credential store. Flag flows with no authentication mechanism.
- Authorization: presence of authentication does not imply authorization. If role-based access is required, verify it exists in the flow or via Anypoint policy.
- Flag flows that return 200 on auth failure instead of 401/403.

## API Policies
- Use `mulesoft/list_api_instances` and `mulesoft/manage_api_instance_policy` to verify that deployed API instances have at minimum: Rate Limiting or SLA-based throttling, Client ID Enforcement or OAuth, and IP allowlist where applicable.

## Logging Safety
- Flag any Logger components that log `payload`, `attributes`, or variables containing passwords, tokens, or PII fields without masking.
- Structured logging with field masking is preferred over full payload logging.

## Output
Classify each finding as critical, high, medium, or low. Include: file/element reference, attack vector, remediation step, and secure property migration note where applicable.
