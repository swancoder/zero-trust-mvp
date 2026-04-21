# CLAUDE.md � ZTE Lightweight Project Guide

## Project Overview
**Product:** Lightweight Zero Trust Environment (ZTE) MVP.
**Goal:** Demonstrate AI-driven development (Gemini as Architect, Claude as Engineer).
**Tech Stack:** Java 21, Gradle (Kotlin DSL), Spring Boot 3.4+, PostgreSQL, Keycloak, Docker.

## Execution Protocols (Mandatory)
1. **Chain of Thought (CoT):** Always output a `### THOUGHTS` block before any implementation.
2. **Self-Criticism:** Always output a `### CRITIQUE` block after a proposal to identify risks.
3. **ADR Requirement:** Every structural or architectural decision must be documented in `./docs/adr/ADR-XXX-name.md`.
4. **Prompt History:** Save every major task prompt into `./prompts-hist/XXX_name.txt`.
5. **SUMMARY**  Update README.md  after each completed task.
6. **Git Workflow:** Each completed task must end with a successful test run and a commit to `main`.


## Build & Development Commands
- **Build Project:** `./gradlew build`
- **Run Tests:** `./gradlew test`
- **Infrastructure:** `docker-compose up -d` / `docker-compose down`
- **Clean DB:** `./gradlew flywayClean` (use with caution)
- **Check Ports:** `netstat -an | grep -E "8080|5432|8180"` (Gateway, DB, Keycloak)

## Code Style & Standards
- **Language:** Java 21 (Modern features only: Records, Pattern Matching).
- **Architecture:** API Gateway Pattern.
- **Naming:** CamelCase for classes/methods, kebab-case for URLs and configs.
- **Security:** Zero Trust principles � no implicit trust, mTLS for all inter-service traffic.
- **Auth:** OIDC/OAuth2 via Keycloak.

## Custom Skills & Tools
- `project-health-check`: Custom skill to verify Docker health and Gradle build status.
- `generate-adr`: (Planned) Helper to scaffold a new ADR file with required CoT/Critique sections.

## Key Directories
- `./gateway-service`: The ZTE entry point.
- `./auth-library`: Shared security logic/mTLS helpers.
- `./service-a`: Demo protected downstream service (port 8081).
- `./prompts-hist`: Log of all Gemini-generated instructions.
- `./docs/adr`: Architectural Decision Records.

---

## Stage Progress

### Stage 1 — Infrastructure Bootstrap `COMPLETE` (commit `c3a9aa7`)
- [x] Gradle 8.12 multi-project build (Kotlin DSL, version catalog)
- [x] Docker Compose: PostgreSQL 16, Keycloak 24.0.4
- [x] `gateway-service` Spring Boot 3.4 skeleton with Spring Cloud Gateway
- [x] `auth-library` placeholder module
- ADR: ADR-001-api-gateway-pattern.md

### Stage 2 — Identity Provider `COMPLETE` (commits `f8d044d`, `29a108c`)
- [x] `keycloak/realm-export.json` — `zte-realm` with client `zte-gateway`, roles `ADMIN`/`USER`, user `zte-admin`
- [x] Docker Compose `--import-realm` flag + directory-level bind mount (WSL2 inode fix)
- [x] `scripts/set-keycloak-password.sh` — post-start password via `kcadm.sh`
- [x] `gateway-service/application.yml` — Spring Security OAuth2 resource server pointing to Keycloak JWKS
- ADR: ADR-002-identity-provider-configuration-strategy.md

### Stage 3 — DB-Based Policy Enforcement `COMPLETE` (commit `5ce757e`)
- [x] V2 Flyway migration: `access_policies` table (role_name, path_pattern, methods, enabled)
- [x] Seed row: ADMIN → `/api/v1/service-a/**` (GET, POST)
- [x] `AccessPolicy` record, `AccessPolicyRepository` (R2DBC reactive), `PolicyService` (Mono.cache 5 min, fail-closed)
- [x] `ZteAuthorizationFilter` GlobalFilter — extracts `realm_access.roles`, enforces DB policy, 403 JSON on deny
      - Order: `HIGHEST_PRECEDENCE + 100`; uses `GATEWAY_ALREADY_ROUTED_ATTR` to block NettyRoutingFilter
- [x] `service-a` sub-module: Spring Boot, `GET /api/v1/service-a/hello`, port 8081
- [x] Gateway route: `/api/v1/service-a/**` → service-a with `X-Gateway-Source` header
- [x] Verification: ADMIN → 200 ✅ | no token → 401 ✅ | USER → 403 ✅
- ADR: ADR-003-reactive-policy-engine.md

### Stage 4 — Observability & Hardening `PENDING`
- [ ] **004** — Request audit logging: persist gateway requests/responses to DB (`request_logs` table, V3 migration)
- [ ] **005** — Distributed tracing: Spring Cloud Sleuth / Micrometer Tracing + Zipkin in Docker Compose
- [ ] **006** — `auth-library` implementation: shared JWT validation helpers, mTLS certificate management
- [ ] **007** — Rate limiting: Spring Cloud Gateway `RequestRateLimiter` filter (Redis-backed)
- [ ] **008** — `/admin/policies/refresh` actuator endpoint: force cache invalidation without gateway restart
- [ ] **009** — Integration test suite: `@SpringBootTest` + Testcontainers (PostgreSQL + Keycloak)
- [ ] **010** — `service-b` second downstream to prove multi-service policy fan-out
- [ ] **011** — Docker Compose production profile: resource limits, health-check restart policies
- [ ] **012** — ABAC extension: `condition` column on `access_policies` (SpEL evaluated against JWT claims)