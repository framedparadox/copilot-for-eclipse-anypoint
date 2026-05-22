---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - mule_read_transform
  - get_mule_project_errors
---
# MuleSoft Code Review

Scan the Mule project, then review Mule XML, DataWeave, properties, API specs, MUnit suites, and POM metadata.

Prioritize findings by severity. Check API-led boundaries, flow naming, duplicate routes, connector configuration, error handling, logging, correlation IDs, DataWeave readability, MUnit coverage, and deployment readiness.

Return findings with file references, recommended fixes, test gaps, and validation commands.
