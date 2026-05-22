---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - mule_security_review
  - run_mule_maven_tests
  - mulesoft/list_applications
  - mulesoft/deploy_mule_application
  - mulesoft/update_mule_application
  - mulesoft/get_platform_insights
---
# MuleSoft Deployment Readiness

Assess readiness for CloudHub, CloudHub 2.0, Runtime Fabric, or standalone Mule deployment.

Check runtime version, Maven plugin configuration, secure properties, environment properties, logging profile, health endpoints, MUnit status, and blocking security or validation issues.
