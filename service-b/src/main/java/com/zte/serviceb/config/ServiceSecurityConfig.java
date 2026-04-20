package com.zte.serviceb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for service-b.
 *
 * <p>Service-b is an <em>internal</em> service. Access is controlled by:
 * <ol>
 *   <li><strong>mTLS:</strong> TLS handshake requires a client cert signed by the ZTE CA.</li>
 *   <li><strong>X-ZTE-User-Context validation:</strong> The {@link UserContextController}
 *       validates the HMAC-signed OBO token on every request, providing application-level
 *       proof of user identity and delegation authority.</li>
 * </ol>
 *
 * <p>No JWT/OAuth2 is configured — all requests that survive the TLS handshake are
 * permitted at the Spring Security layer. Controller-level validation handles the rest.
 */
@Configuration
@EnableWebFluxSecurity
public class ServiceSecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // mTLS + OBO token validation in UserContextController is the trust perimeter
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }
}
