package com.zte.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that logs authenticated requests and injects the caller's
 * subject ({@code sub} JWT claim) as the {@code X-User-Id} header for
 * downstream services.
 *
 * <p>Zero Trust principle: downstream services should NEVER trust client-supplied
 * identity headers. Only headers set by this gateway are authoritative.
 */
@Component
public class RequestAuditFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestAuditFilter.class);

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> {
                var principal = ctx.getAuthentication();
                if (principal != null && principal.getPrincipal() instanceof Jwt jwt) {
                    String subject  = jwt.getSubject();
                    String clientId = jwt.getClaimAsString("azp");
                    log.info("ZT-AUDIT sub={} azp={} path={}",
                        subject, clientId, exchange.getRequest().getPath());

                    // Strip any client-supplied identity headers, inject trusted one
                    ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r
                            .headers(h -> {
                                h.remove("X-User-Id");   // strip untrusted
                                h.add("X-User-Id", subject);
                            }))
                        .build();
                    return chain.filter(mutated);
                }
                return chain.filter(exchange);
            })
            .switchIfEmpty(chain.filter(exchange));
    }
}
