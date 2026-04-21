package com.zte.gateway.filter;

import com.zte.auth.obo.UserContextTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserContextPropagationFilter}.
 *
 * <p>Key assertions:
 * <ul>
 *   <li>Authenticated request — signed OBO token added, spoofed incoming headers stripped.</li>
 *   <li>No security context / non-JWT auth — chain still called, injected headers stripped.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserContextPropagationFilterTest {

    private static final String OBO_TOKEN    = "signed.obo.token.value";
    private static final String SUBJECT      = "user-uuid-abc-123";

    @Mock UserContextTokenService tokenService;
    @Mock GatewayFilterChain      chain;

    UserContextPropagationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserContextPropagationFilter(tokenService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private JwtAuthenticationToken jwtAuth(String sub, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "RS256")
                .subject(sub)
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    /** Exchange that carries spoofed user-context headers to verify they are stripped. */
    private MockServerWebExchange exchangeWithSpoofedHeaders() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/service-a/hello")
                        .header(UserContextPropagationFilter.HEADER_USER_CONTEXT, "spoofed-obo")
                        .header(UserContextPropagationFilter.HEADER_USER_ID, "attacker-id")
                        .build());
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    /**
     * Authenticated JWT request:
     * <ul>
     *   <li>tokenService.createToken() called with sub + roles from JWT.</li>
     *   <li>OBO token set as {@code X-ZTE-User-Context} on the forwarded request.</li>
     *   <li>Plain {@code X-User-Id} set to subject.</li>
     *   <li>Any spoofed incoming headers are replaced (not appended).</li>
     * </ul>
     */
    @Test
    void authenticatedRequest_addsOboTokenAndReplacesIncomingHeaders() {
        when(tokenService.createToken(eq(SUBJECT), any())).thenReturn(OBO_TOKEN);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange  ex   = exchangeWithSpoofedHeaders();
        JwtAuthenticationToken auth = jwtAuth(SUBJECT, List.of("ADMIN"));

        StepVerifier.create(
                filter.filter(ex, chain)
                      .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        HttpHeaders headers = captor.getValue().getRequest().getHeaders();
        assertThat(headers.getFirst(UserContextPropagationFilter.HEADER_USER_CONTEXT))
                .isEqualTo(OBO_TOKEN);
        assertThat(headers.getFirst(UserContextPropagationFilter.HEADER_USER_ID))
                .isEqualTo(SUBJECT);
        // Exactly one value — the spoofed "attacker-id" must be gone
        assertThat(headers.get(UserContextPropagationFilter.HEADER_USER_ID)).hasSize(1);
    }

    /**
     * Non-JWT authentication (e.g. anonymous) — chain is still called but
     * spoofed headers are stripped to prevent injection on public paths.
     */
    @Test
    void nonJwtAuthentication_stripsHeadersAndForwardsToChain() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange ex     = exchangeWithSpoofedHeaders();
        SecurityContextImpl   emptyCtx = new SecurityContextImpl(); // no auth

        StepVerifier.create(
                filter.filter(ex, chain)
                      .contextWrite(ReactiveSecurityContextHolder
                              .withSecurityContext(Mono.just(emptyCtx))))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        HttpHeaders headers = captor.getValue().getRequest().getHeaders();
        assertThat(headers.get(UserContextPropagationFilter.HEADER_USER_CONTEXT)).isNullOrEmpty();
        assertThat(headers.get(UserContextPropagationFilter.HEADER_USER_ID)).isNullOrEmpty();
        verifyNoInteractions(tokenService);
    }

    /**
     * No security context at all (public path, {@code switchIfEmpty} branch) —
     * chain called with stripped headers.
     */
    @Test
    void noSecurityContext_stripsHeadersAndForwardsToChain() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange ex = exchangeWithSpoofedHeaders();

        // No contextWrite → ReactiveSecurityContextHolder returns empty
        StepVerifier.create(filter.filter(ex, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        HttpHeaders headers = captor.getValue().getRequest().getHeaders();
        assertThat(headers.get(UserContextPropagationFilter.HEADER_USER_CONTEXT)).isNullOrEmpty();
        assertThat(headers.get(UserContextPropagationFilter.HEADER_USER_ID)).isNullOrEmpty();
        verifyNoInteractions(tokenService);
    }
}
