---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - munit_validate_flow_tests
  - munit_full_review
  - munit_improvement_suggestions
  - get_mule_project_errors
  - run_mule_maven_tests
---
# Generate MUnit Tests

Inspect the target flow and existing MUnit suites. Recommend or generate focused MUnit coverage for positive, negative, and edge cases.

Mock downstream connectors, validate payloads and variables, avoid duplicate tests, and include the Maven command needed to run the suite.

Before finalizing, validate the suite with `munit_validate_flow_tests` and address missing MUnit namespaces, config,
execution, validation, assertions, processor coverage, branch coverage, and error-path coverage. Use `munit_full_review`
for an end-to-end review and `munit_improvement_suggestions` to tune the testing cadence.
