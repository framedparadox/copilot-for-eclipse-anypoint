---
mode: agent
tools:
  - mule_project_scan
  - mule_code_review
  - mule_security_review
  - run_mule_maven_tests
---
# MuleSoft Deployment Readiness

Run `mule_project_scan` to establish the baseline. Run `mule_code_review` and `mule_security_review`. Then run `run_mule_maven_tests` to confirm MUnit status. Ask the user for the target platform (CloudHub 1.0, CloudHub 2.0 / Runtime Fabric, or standalone/on-prem) to tailor checklist items.

## Universal Prerequisites
- `mule-artifact.json` present with correct `minMuleVersion` and `classLoaderModelLoaderDescriptor`.
- `pom.xml` has the correct Mule Maven Plugin version compatible with the target runtime. Flag `mule-maven-plugin` versions that do not match the runtime major version.
- All MUnit tests pass (`run_mule_maven_tests`). Blocking failures must be resolved before deployment.
- No hardcoded secrets in any Mule XML, property file, or POM (confirmed by `mule_security_review`).
- All environment-specific properties externalized to `config-<env>.yaml` or `.properties` with `${property}` placeholders. Flag any `config-default.yaml` values that look environment-specific (URLs, ports, hostnames).
- Log level set to `INFO` or `WARN` in the production logging profile. Flag `DEBUG` or `TRACE` in the default log4j2 config.

## Health Endpoints
- Every application must expose a health check endpoint (e.g., `GET /health` or `GET /status`) returning HTTP 200 with at minimum `{"status": "UP", "version": "<app-version>"}`.
- The health endpoint must respond within 2 seconds under normal load. Flag health implementations that call downstream services synchronously without a timeout guard.
- Flag missing health endpoints — deployment platforms use them for liveness and readiness probes.

## CloudHub 1.0 Specific
- Worker type and count must be configured in the deployment descriptor or Anypoint Platform. Recommend minimum `Medium` (1 vCore) for production flows. Flag `Micro` for anything other than dev/test.
- Persistent queues should be enabled for flows using VM connector or Anypoint MQ when message loss is unacceptable.
- Static IP should be requested in advance if the app connects to IP-allowlisted upstream services.

## CloudHub 2.0 / Runtime Fabric Specific
- `resources.cpu.reserved`, `resources.cpu.limit`, `resources.memory.reserved`, `resources.memory.limit` must be set in the deployment descriptor. Flag missing resource specifications.
- Replicas should be 2+ for HA in production. Flag single-replica production deployments.
- Liveness and readiness probe paths should point to the health endpoint. Flag if not configured.
- Ingress TLS must be terminated at the ingress controller. Flag HTTP-only ingress configurations.

## Standalone / On-Premises Specific
- Mule runtime installed at the correct version matching `minMuleVersion`. Flag version mismatches.
- Cluster configuration required for HA: `<cluster>` settings in `wrapper.conf` or via Management Center.
- JVM heap sizing: `-Xms` and `-Xmx` configured appropriate to worker memory. Flag default JVM settings (256 MB) for production workloads.
- Application hot-deployment path confirmed writable by the Mule process user.

## Smoke Test Checklist
After deployment:
1. Health endpoint returns `{"status": "UP"}` — verify manually or via curl.
2. Main API endpoint returns expected response to a known-good test request.
3. Log output shows application startup completion without ERROR lines.
4. Anypoint Monitoring or CloudWatch shows response time < SLA threshold within 5 minutes of startup.

## Output
Return a deployment readiness score (ready / conditional / blocked), a checklist of passed and failed items grouped by category, and any blocking issues that must be resolved before deployment proceeds.
