package com.zte.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reusable Zero Trust security configuration for all ZTE services.
 *
 * <p>Policy: deny by default. Every request must present a valid JWT signed by
 * the configured Keycloak realm. Only actuator health endpoints are public.
 *
 * <p>This is a library bean — it activates when imported via
 * {@link ZteSecurityAutoConfiguration} and is overridable per-service by
 * declaring a competing {@code SecurityWebFilterChain} bean with higher precedence.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)          // Stateless JWT API — no CSRF needed
            .authorizeExchange(exchanges -> exchanges
                // Public: health probes (liveness/readiness) — needed by Docker/K8s
                .pathMatchers("/actuator/health/**").permitAll()
                // Everything else requires a valid JWT
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {}) // JwtDecoder auto-configured from application.yml issuer-uri
            )
            .build();
    }
}
