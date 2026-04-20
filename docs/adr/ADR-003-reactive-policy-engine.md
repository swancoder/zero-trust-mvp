# ADR-003: Reactive Policy Engine — R2DBC + In-Process Cache

**Status:** Accepted
**Date:** 2026-04-20
**Deciders:** ZTE-Lightweight Architects

---

## Context

The ZTE gateway must enforce DB-backed access policies on every authenticated request.
Each policy row maps a Keycloak realm role to an Ant-style path pattern and a set of
allowed HTTP methods. The policy check runs synchronously in the request path.

Two constraints drive the design:
1. **WebFlux reactive stack** — blocking I/O on the Netty event loop is forbidden.
2. **Low latency** — a synchronous DB query per request is unacceptable at scale.

---

## Decision

**Use Spring Data R2DBC with a reactive in-process Mono cache (5-minute TTL).**

- `AccessPolicyRepository` is a `ReactiveCrudRepository<AccessPolicy, Long>` using R2DBC (non-blocking PostgreSQL driver).
- `PolicyService` wraps the repository query in a `Mono<List<AccessPolicy>>` cached with Reactor's `Mono.cache(Duration.ofMinutes(5))`.
- On DB error: `onErrorResume` returns an empty policy list (fail-closed) without caching the error — next request retries.
- `ZteAuthorizationFilter` (GlobalFilter, order `1`) extracts `realm_access.roles` from the JWT and calls `PolicyService.isAllowed()`.

---

## Alternatives Considered

### Option A: Blocking JDBC + `@Scheduled` refresh (rejected)
Use `JdbcTemplate` or Spring Data JDBC, call it on a background thread with `subscribeOn(Schedulers.boundedElastic())`, and cache results in a `ConcurrentHashMap` refreshed by a `@Scheduled` task.

- **Pros:** Simpler code; familiar JDBC API; no second datasource config.
- **Cons:** `subscribeOn(boundedElastic())` uses a thread-pool thread for each policy refresh — this can exhaust the pool under load. The scheduled refresh runs independently of demand, wasting resources when traffic is low and potentially serving stale data when traffic is high. Two concurrency models (reactive + scheduled) in one service increases cognitive load.
- **Verdict:** Rejected. Mixing blocking I/O and reactive pipelines is the primary anti-pattern in WebFlux services.

### Option B: Spring Cache (`@Cacheable` + Caffeine) on a reactive service (rejected)
Apply `@Cacheable` to a reactive method, backed by Caffeine's `AsyncCache`.

- **Pros:** Declarative caching; integrates with Spring's cache abstraction.
- **Cons:** Spring's reactive `@Cacheable` support (via `CaffeineReactiveCacheManager`) is available but requires explicit configuration and a `CacheManager` bean. The added infrastructure (Caffeine dependency, CacheManager config) outweighs the benefit over plain `Mono.cache()` for a single cached value.
- **Verdict:** Rejected for MVP. Revisit if multiple services/methods need caching (at that point, a unified `CacheManager` makes sense).

### Option C: R2DBC + `Mono.cache(Duration)` (Selected)
Use the reactive R2DBC driver and Reactor's built-in caching operator.

- **Pros:** Fully non-blocking; zero additional dependencies (R2DBC is already needed for WebFlux compliance); `Mono.cache(Duration)` is a single-line, well-understood pattern; TTL-based refresh on next subscriber after expiry; fail-closed on error.
- **Cons:** A single `Mono` instance holds all policies in memory — not suitable if the policy table grows to tens of thousands of rows. For MVP scale (< 100 rules), this is negligible.
- **Verdict:** Accepted for MVP.

---

## Chain of Thought (CoT)

1. **WebFlux constraint is non-negotiable.** Calling a blocking datasource from a Netty thread causes thread starvation. R2DBC is the only correct choice for a reactive gateway.

2. **One DB query per request is too expensive.** At 100 req/s, that's 100 policy table scans per second. A 5-minute TTL cache reduces this to at most 1 query per 5 minutes — effectively zero DB overhead at runtime.

3. **Reactor's `Mono.cache(Duration)` is idiomatic.** It is designed exactly for this: deferred, lazy, TTL-bounded caching of a reactive value. No external cache library, no thread scheduling, no callbacks. The same `Mono` instance is shared across all concurrent requests.

4. **Fail-closed is the correct default.** If the DB is unreachable at policy-refresh time, denying all requests is safer than allowing all. The `onErrorResume` converts the error to `Mono.just(List.of())` BEFORE the `cache()` operator, so the empty list (not the error) is what gets cached — next subscriber retries the DB.

5. **JDBC + R2DBC coexistence is supported.** Flyway must use JDBC (its migrations run before the reactive context starts). Spring Boot 3.4 configures both datasources independently — JDBC (HikariCP) for Flyway at startup, R2DBC (r2dbc-pool) for runtime queries. There is no conflict.

6. **Filter order `1` is correct.** Spring Security's WebFilter processes JWT validation before any Gateway GlobalFilter runs. By the time `ZteAuthorizationFilter` executes, the `ReactiveSecurityContextHolder` is populated. Order `1` ensures policy enforcement runs before `RequestAuditFilter` (LOWEST_PRECEDENCE - 10) and before the routing filter.

---

## Self-Critique

| Risk | Severity | Mitigation |
|---|---|---|
| All policies held in memory | Low (MVP) | Policy table is small. At scale, replace with a paginated or role-indexed query. |
| 5-min stale policy window | Medium | Acceptable for MVP. Add a `/admin/policies/refresh` actuator endpoint for immediate invalidation if needed. |
| Error result cached as empty list for 5 min | Low | `onErrorResume` before `cache()` means the error itself is NOT cached — only the empty fallback list is. Next subscriber after TTL retries the DB. |
| R2DBC connection pool adds memory overhead | Low | `initial-size: 2, max-size: 5` — minimal footprint for a gateway with low downstream DB traffic. |
| service-a has no JWT validation | Acceptable | By design (Zero Trust perimeter at gateway). If service-a becomes externally accessible, `auth-library` provides a one-line opt-in. |

---

## Consequences

- **Positive:** DB policy changes are picked up within 5 minutes with zero gateway restart.
- **Positive:** Policy logic is fully unit-testable without a running DB (mock `PolicyService`).
- **Positive:** `ZteAuthorizationFilter` is independent of Keycloak's role hierarchy — roles are just strings, making the policy model provider-agnostic.
- **Negative:** `service-a` requires a Docker build for the compose workflow. Alternative: `./gradlew :service-a:bootRun` for local dev without Docker.
- **Negative:** The 5-minute cache means a rogue session with a revoked JWT (but still valid signature) could continue to access resources until the next policy refresh — this is a JWT lifetime concern, not specific to this design.

---

## Future Migration Path

- **Policy invalidation endpoint:** Add `POST /actuator/policies/refresh` to force cache expiry.
- **ABAC (Attribute-Based Access Control):** Extend `access_policies` with a `condition` column (SpEL expression evaluated against JWT claims) for fine-grained attribute-based rules.
- **Distributed cache:** Replace `Mono.cache()` with Redis-backed caching when multiple gateway instances are deployed (policy sync across instances).
