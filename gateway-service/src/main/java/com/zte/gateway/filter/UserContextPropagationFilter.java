package com.zte.gateway.filter;

import com.zte.auth.obo.UserContextTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Propagates user identity to downstream services via a signed
 * {@code X-ZTE-User-Context} on-behalf-of (OBO) token.
 *
 * <p>Runs after {@link ZteAuthorizationFilter} (order {@code HIGHEST_PRECEDENCE + 100})
 * so that by the time this filter executes, any unauthorised request has already been
 * rejected with 403. Only authenticated, policy-approved requests reach here.
 *
 * <p>For every authenticated request this filter:
 * <ol>
 *   <li>Strips any incoming {@code X-ZTE-User-Context} and {@code X-User-Id} headers
 *       — prevents header injection by external callers attempting to spoof user identity.</li>
 *   <li>Extracts {@code sub} and {@code realm_access.roles} from the validated
 *       Keycloak JWT.</li>
 *   <li>Creates a short-lived (default 30 s) HMAC-SHA256 OBO token via
 *       {@link UserContextTokenService}.</li>
 *   <li>Adds the token as {@code X-ZTE-User-Context} and adds {@code X-User-Id} (plain
 *       sub) for backward-compatible consumers.</li>
 * </ol>
 *
 * <p>Downstream services receiving {@code X-ZTE-User-Context} can call
 * {@link UserContextTokenService#validateAndParse(String)} to verify the signature and
 * extract the user context. Service A forwards the header unchanged to Service B —
 * the token is not re-issued at each hop (delegation chain, not re-issuance).
 */
@Component
public class UserContextPropagationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(UserContextPropagationFilter.class);

    static final String HEADER_USER_CONTEXT = "X-ZTE-User-Context";
    static final String HEADER_USER_ID      = "X-User-Id";

    private final UserContextTokenService tokenService;

    public UserContextPropagationFilter(UserContextTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public int getOrder() {
        // Runs after ZteAuthorizationFilter (HIGHEST_PRECEDENCE + 100)
        // but before the routing filter (Integer.MAX_VALUE)
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> {
                    Authentication auth = ctx.getAuthentication();
                    if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
                        return chain.filter(stripUserContextHeaders(exchange));
                    }

                    String       sub   = jwtAuth.getToken().getSubject();
                    List<String> roles = extractRealmRoles(jwtAuth);
                    String       token = tokenService.createToken(sub, roles);

                    log.debug("OBO token created for sub={} roles={}", sub, roles);

                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> r.headers(h -> {
                                h.remove(HEADER_USER_CONTEXT);
                                h.remove(HEADER_USER_ID);
                                h.add(HEADER_USER_CONTEXT, token);
                                h.add(HEADER_USER_ID, sub);
                            }))
                            .build();

                    return chain.filter(mutated);
                })
                // No security context → public path; still strip to prevent injection
                .switchIfEmpty(Mono.defer(() -> chain.filter(stripUserContextHeaders(exchange))));
    }

    private ServerWebExchange stripUserContextHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove(HEADER_USER_CONTEXT);
                    h.remove(HEADER_USER_ID);
                }))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(JwtAuthenticationToken jwtAuth) {
        Map<String, Object> realmAccess = jwtAuth.getToken().getClaimAsMap("realm_access");
        if (realmAccess == null) return List.of();
        Object roles = realmAccess.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }
}
