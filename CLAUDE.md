# CLAUDE.md

This file gives Claude Code the context it needs to work effectively in this repository.
Keep it short, factual, and current. Prefer links to authoritative docs over long prose.

---

## Project Overview

**Name:** Agentic Automation (Leveraging Activepieces)
**Purpose:** Handles Intelligent Automation Low code.
**Owners:** Suvadip 
**Repo type:** Spring Boot microservice + Heteroginios connectors supported by Activepieces

---

## Tech Stack

- Java 21, Spring Boot 3.3, Spring Cloud 2023.x
- Kafka (Confluent), Oracle 19c, Redis
- Build: Maven 3.9, Docker, OpenShift 4.x
- Testing: JUnit 5, Mockito, Testcontainers, RestAssured
- Observability: OpenTelemetry → Grafana/Loki/Tempo

---

## Repository Layout

- `src/main/java/com/acme/payments/` — application code
    - `api/` — REST controllers (DTOs live in `api/dto`)
    - `domain/` — aggregates, domain services, invariants
    - `infra/` — Kafka, JPA, Redis, external HTTP clients
    - `config/` — Spring config, beans, properties binding
- `src/test/` — mirrors main; integration tests end with `IT.java`
- `deploy/` — Helm charts, OpenShift manifests
- `docs/adr/` — Architecture Decision Records (read before large changes)

---

## Build, Test, Run

```bash
# Build (skip integration tests)
mvn clean install -DskipITs

# Full test suite (requires Docker for Testcontainers)
mvn verify

# Run locally against dockerized deps
docker compose -f deploy/local/docker-compose.yml up -d
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Lint / format
mvn spotless:apply
```

**Always run `mvn spotless:apply && mvn verify` before proposing a commit.**

---

## Coding Standards

- Follow Google Java Style (enforced by Spotless).
- Prefer constructor injection; no field injection.
- No `@Autowired` on fields, no static singletons for stateful services.
- All public APIs must have Javadoc; private methods only when non-obvious.
- Null-safety: use `Optional` at boundaries, `@Nullable` annotations elsewhere.
- Logging: SLF4J parameterized (`log.info("id={}", id)`), never string concat.
- Never log PII, card numbers, tokens, or full request bodies.

---

## Testing Expectations

- New code needs unit tests. Aim for meaningful assertions, not coverage-chasing.
- Integration tests use Testcontainers; do not mock Kafka or the DB in `*IT.java`.
- Golden-file tests live in `src/test/resources/golden/` — regenerate only with reviewer approval.

---

## Git & PR Workflow

- Branch naming: `feature/<jira>-slug`, `fix/<jira>-slug`, `chore/...`
- Commit style: Conventional Commits (`feat:`, `fix:`, `refactor:`...).
- One logical change per PR. Keep diffs under ~400 lines when possible.
- PR description must reference the Jira ticket and note any ADR changes.
- **Do not commit or push without explicit user confirmation.**

---

## What Claude Should Do

- Read `docs/adr/` before proposing architectural changes.
- When touching Kafka contracts, update the schema in `deploy/schemas/` in the same PR.
- Prefer editing existing files over creating new ones.
- Ask before adding new dependencies; justify with alternatives considered.
- When a task is ambiguous, ask one focused clarifying question before coding.

## What Claude Should NOT Do

- Do not modify files under `deploy/prod/` or `src/main/resources/secrets/`.
- Do not upgrade major versions of Spring, Kafka client, or Oracle driver without an ADR.
- Do not disable or skip tests to make a build pass.
- Do not commit generated files (`target/`, `.idea/`, `*.log`).
- Do not run destructive commands (`git push --force`, `git reset --hard`, `rm -rf`) without confirmation.

---

## Security & Compliance

- This service processes regulated financial data. Treat all data as sensitive by default.
- Secrets come from Vault via `spring-cloud-vault`; never hardcode.
- All outbound HTTP goes through the internal egress proxy configured in `application.yml`.
- Any change touching auth, crypto, or audit logging requires Security team review — flag it in the PR.

---

## Environments

| Env    | Purpose            | Deploy trigger        |
|--------|--------------------|-----------------------|
| dev    | Feature testing    | Auto on merge to main |
| uat    | Business testing   | Manual, tagged build  |
| prod   | Live               | Change ticket + CAB   |

---

## Useful References

- Runbook: `docs/runbook.md`
- On-call: PagerDuty service "acme-payments"
- API spec: `docs/openapi.yaml` (source of truth — regenerate clients from this)
- Related repos: `acme-payments-schemas`, `acme-payments-ui`

---

## Notes for Claude

- If you find this file out of date with the code, say so in your response — do not silently work around it.
- When unsure whether an instruction here still applies, ask.