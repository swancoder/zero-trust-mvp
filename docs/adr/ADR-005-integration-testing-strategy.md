# ADR-005: Integration Testing Strategy — Testcontainers + WireMock

**Date:** 2026-04-21
**Status:** Accepted
**Author:** Gemini (Architect) / Claude (Engineer)

---

## Context

The ZTE gateway enforces four distinct security guarantees that must be tested end-to-end:
1. JWT signature and expiry validation (via Keycloak RS256)
2. DB-backed role/path policy enforcement (R2DBC → PostgreSQL)
3. OBO token creation and header injection prevention (UserContextPropagationFilter)
4. mTLS transport security (Reactor Netty, client.p12 / ZTE-CA)

Unit tests (Stage 5) cover items 1–3 in isolation with mocks. Stage 6 adds tests that exercise the *running gateway process* against real infrastructure components to catch integration misconfigurations.

---

## Decision

### Test Framework: Testcontainers (JUnit 5)

**Why Testcontainers over Docker Compose:**
- Containers are started/stopped by JUnit lifecycle — no out-of-band `docker compose up` step in CI.
- Each test run gets a clean, isolated state; no shared mutable infra between branches.
- `DynamicPropertySource` injects container ports into Spring's environment without property file edits.

**Why Testcontainers over H2/embedded alternatives:**
- H2 does not speak R2DBC PostgreSQL dialect; the policy engine uses PostgreSQL-specific behaviour (Ant-path matching, `BIGSERIAL`).
- Keycloak has no embeddable equivalent; RS256 JWT validation requires a real JWKS endpoint.

### Container Topology

| Container | Image | Role |
|---|---|---|
| PostgreSQL 16-alpine | `postgres:16-alpine` | `access_policies` table, Flyway migrations |
| Keycloak 24.0.4 | `quay.io/keycloak/keycloak:24.0.4` | JWT issuance + JWKS endpoint |
| WireMock 3.0.4 | in-process (Java) | Simulates service-a / service-b responses |

### Singleton Container Pattern

All three containers are started **once** in a `static {}` block of `BaseZteIntegrationTest` rather than per-test-class. This means:
- The first test class that loads starts the containers (~30 s for Keycloak).
- All subsequent test classes share the same containers and the same cached Spring `ApplicationContext`.
- Total overhead for 2+ test classes is the same as for 1.

Spring's test context cache key includes the `@DynamicPropertySource` output. Because all subclasses of `BaseZteIntegrationTest` produce the same dynamic properties (same container ports), Spring creates one `ApplicationContext` for the entire integration test run.

### WireMock as Service-A/B Replacement

**Rationale:** Running service-a and service-b as real containers would require:
1. Pre-built Docker images (slow in CI — depends on `./gradlew build`).
2. Certificate generation for inter-service mTLS.
3. Network routing between containers on a shared Docker network.

WireMock (in-process, HTTP) avoids all three by replacing the downstream services at the Spring Cloud Gateway routing layer. The gateway's security filters still run at full fidelity — only the final routing target changes from `https://service-a:8081` to `http://localhost:<wiremock-port>`.

### mTLS Testing Gap and Mitigation

**Gap:** Transport-layer mTLS enforcement (TLS handshake failure when client cert is absent or signed by a different CA) cannot be tested when WireMock is the downstream. This requires service-a and service-b to be running as real HTTPS/mTLS servers.

**Mitigation:**
- The `@ConditionalOnProperty(name = "zte.mtls.enabled")` guard on `MtlsHttpClientConfig` ensures the production mTLS client is exercised in integration tests against the real Docker Compose stack.
- The `ZeroTrustBreachIT` tests verify the **application-layer** Zero Trust guarantees (JWT, policy, OBO) which are the primary ZT enforcement points.
- A future Stage 7 system test suite (Testcontainers `GenericContainer` or Docker Compose + `wait-for-it`) will cover the full mTLS chain.

### `@ConditionalOnProperty` on `MtlsHttpClientConfig`

`MtlsHttpClientConfig` loads `client.p12` and `truststore.p12` in its `@Bean` factory methods. Adding `@ConditionalOnProperty(name = "zte.mtls.enabled", havingValue = "true", matchIfMissing = true)` to the class:

- In **production** (`zte.mtls.enabled` not set → `matchIfMissing = true`): behaves identically to before.
- In **integration tests** (`zte.mtls.enabled: false` in `application-it.yml`): skips cert loading; Spring Cloud Gateway uses its default HTTP client; routes point to WireMock HTTP.

### Gradle `it` Source Set

A dedicated source set `src/it` (not `src/test`) is used to:
1. Separate integration tests from unit tests — `./gradlew test` runs only unit tests.
2. Allow different dependency sets (Testcontainers, WireMock are it-only, not test-only).
3. Enable CI to run `./gradlew test integrationTest` in separate pipeline stages.

---

## Consequences

| Concern | Impact |
|---|---|
| Keycloak startup time | ~15-30 s (one-time per JVM; amortised across all IT classes) |
| Container isolation | Each `./gradlew integrationTest` run gets a fresh DB, fresh Keycloak realm state |
| mTLS gap | Documented; full mTLS system test deferred to Stage 7 |
| Production code change | One `@ConditionalOnProperty` added to `MtlsHttpClientConfig` — backward-compatible |
| CI requirements | Docker daemon must be available on the CI runner |
