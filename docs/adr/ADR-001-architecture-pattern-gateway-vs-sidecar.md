# ADR-001: Architecture Pattern — Gateway vs Sidecar

**Status:** Accepted
**Date:** 2026-04-02
**Deciders:** ZTE-Lightweight Architects

---

## Context

The ZTE-Lightweight project requires a Zero Trust enforcement point that:
1. Authenticates every inbound request via OIDC/JWT (Keycloak).
2. Authorizes requests based on roles/scopes before forwarding to downstream services.
3. Can be built and operated by a small team on an MVP timeline.
4. Must run on Docker Compose today, with a path to Kubernetes later.

Two dominant patterns exist for implementing this enforcement point:

- **Dedicated API Gateway** (e.g., Spring Cloud Gateway as its own service)
- **Sidecar Proxy** (e.g., Envoy + Istio/Linkerd injected per-pod)

---

## Decision

**We will use a Dedicated API Gateway implemented as a Spring Cloud Gateway service.**

The gateway (`gateway-service`) is a standalone Spring Boot 3 application using Spring Cloud Gateway (WebFlux-based). It is the single ingress point for all external traffic. Authentication and authorization are enforced via Spring Security + OAuth2 Resource Server (JWT validation against Keycloak's JWKS endpoint).

---

## Alternatives Considered

### Option A: Sidecar Proxy (Envoy + Istio)
- **Pros:** Language-agnostic, zero-trust enforcement at the network layer, mTLS between services, no code changes to downstream services.
- **Cons:** Requires Kubernetes + service mesh (Istio/Linkerd). Enormous operational overhead for an MVP. Learning curve for the team. Docker Compose is not a first-class citizen.
- **Verdict:** Rejected for MVP. Revisit at scale (>5 services, Kubernetes adoption).

### Option B: NGINX / Kong Gateway
- **Pros:** Battle-tested, plugin ecosystem (Kong), high performance.
- **Cons:** Requires Lua scripting or plugin development for custom zero-trust logic. Less idiomatic for a Java/Spring team. Keycloak integration requires additional plugins.
- **Verdict:** Rejected. Higher integration cost; team expertise is in Java/Spring.

### Option C: Spring Cloud Gateway (Selected)
- **Pros:** Native Spring Boot integration, WebFlux reactive stack, first-class Spring Security support, JWT validation built-in, Keycloak JWKS auto-discovery, custom filter API in Java, same team skills.
- **Cons:** Single point of failure if not clustered; JVM overhead vs native proxies; tightly couples network policy to application code.
- **Verdict:** Accepted for MVP.

---

## Chain of Thought (CoT)

1. The team writes Java. A Java-based gateway means zero context switching for adding custom filters (rate limiting, audit logging, header enrichment).
2. Spring Boot 3's `spring-boot-starter-oauth2-resource-server` handles JWKS fetching, JWT signature verification, and claims extraction automatically. This is a solved problem — no custom crypto code.
3. Spring Cloud Gateway's `GatewayFilter` and `GlobalFilter` APIs are the correct extension points for ZTE policy enforcement (e.g., stripping internal headers, injecting `X-User-Id`).
4. Docker Compose is the current deployment target. A sidecar pattern requires a container orchestrator for injection — incompatible with the current infrastructure requirement.
5. Keycloak in dev mode on Docker Compose gives us a working OIDC provider in minutes. The gateway's `issuer-uri` points to it; everything else is auto-configured.
6. The `auth-library` module encapsulates security configuration as a reusable library. When a second service is added, it imports `auth-library` and gains identical JWT enforcement with no code duplication.

---

## Self-Critique

| Risk | Severity | Mitigation |
|---|---|---|
| Single ingress point of failure | Medium | Horizontal scaling behind a load balancer (Docker Swarm / K8s later) |
| JVM startup time (cold starts) | Low | Spring Boot 3 AOT + GraalVM native image path exists if needed |
| WebFlux reactive complexity | Medium | Team must understand Project Reactor; document Mono/Flux patterns |
| Gateway becomes a God Service | High | Enforce strict module boundaries: gateway only routes + enforces; NO business logic |
| Keycloak `latest` tag drift | Medium | Pin to a specific Keycloak version before staging/prod |
| JDBC + WebFlux impedance mismatch | Low | Flyway migrations use JDBC DataSource; gateway runtime stays reactive (no blocking calls in request path) |

---

## Consequences

- **Positive:** Simple to develop, debug, and test locally. Full Spring ecosystem compatibility.
- **Positive:** `auth-library` enforces DRY security configuration across all future services.
- **Negative:** Adding mTLS between services requires custom work (not automatic as with a service mesh).
- **Negative:** Network policy is in application code, not infrastructure — policy drift risk if services bypass the gateway.

---

## Future Migration Path

When moving to Kubernetes with >5 downstream services, evaluate adding a service mesh (Istio) for mTLS between services. The gateway role shifts to handling only **north-south** (external) traffic; the mesh handles **east-west** (internal service-to-service) zero trust.
