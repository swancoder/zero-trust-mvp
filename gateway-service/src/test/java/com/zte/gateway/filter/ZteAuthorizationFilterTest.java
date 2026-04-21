package com.zte.gateway.filter;

import com.zte.gateway.policy.PolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ZteAuthorizationFilter}.
 *
 * <p>Tests use {@link MockServerWebExchange} and inject a reactive security context
 * via {@link ReactiveSecurityContextHolder#withAuthentication} so no Spring
 * application context is required.
 */
@ExtendWith(MockitoExtension.class)
class ZteAuthorizationFilterTest {

    @Mock PolicyService     policyService;
    @Mock GatewayFilterChain chain;

    ZteAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ZteAuthorizationFilter(policyService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private JwtAuthenticationToken jwtAuth(List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("user-uuid-123")
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/service-a/hello").build());
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    /**
     * When no security context is present (public / unauthenticated path) the filter
     * must not call PolicyService and must forward to the next filter.
     */
    @Test
    void noSecurityContext_forwardsToChain() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange ex = exchange();

        StepVerifier.create(filter.filter(ex, chain))
                .verifyComplete();

        verify(chain).filter(ex);
        verifyNoInteractions(policyService);
    }

    /**
     * When the security context contains a non-JWT authentication (e.g. anonymous),
     * the filter passes through without policy evaluation.
     */
    @Test
    void nonJwtAuthentication_forwardsToChain() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange ex = exchange();

        // SecurityContext with no authentication — getAuthentication() == null
        SecurityContextImpl emptyCtx = new SecurityContextImpl();

        StepVerifier.create(
                filter.filter(ex, chain)
                      .contextWrite(ReactiveSecurityContextHolder
                              .withSecurityContext(Mono.just(emptyCtx))))
                .verifyComplete();

        verify(chain).filter(ex);
        verifyNoInteractions(policyService);
    }

    /**
     * JWT bearer with a role that matches a policy → 200, chain called.
     */
    @Test
    void jwtWithAllowedRole_callsChain() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(policyService.isAllowed(any(), any(), any())).thenReturn(Mono.just(true));

        MockServerWebExchange ex    = exchange();
        JwtAuthenticationToken auth = jwtAuth(List.of("ADMIN"));

        StepVerifier.create(
                filter.filter(ex, chain)
                      .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .verifyComplete();

        verify(chain).filter(ex);
        verify(policyService).isAllowed(List.of("ADMIN"), "/api/v1/service-a/hello", "GET");
    }

    /**
     * JWT bearer whose role has no matching policy → 403, chain never called.
     */
    @Test
    void jwtWithDeniedRole_returns403AndDoesNotCallChain() {
        when(policyService.isAllowed(any(), any(), any())).thenReturn(Mono.just(false));

        MockServerWebExchange  ex   = exchange();
        JwtAuthenticationToken auth = jwtAuth(List.of("USER"));

        StepVerifier.create(
                filter.filter(ex, chain)
                      .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
