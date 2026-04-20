package com.zte.servicea.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for service-a.
 *
 * <p>Service-a is an <em>internal</em> service — it does not accept user-facing
 * Keycloak JWTs. Access control is handled by two transport-level mechanisms:
 * <ol>
 *   <li><strong>mTLS:</strong> The Netty server requires a valid client certificate
 *       signed by the ZTE CA. Any connection without a valid cert is rejected
 *       at the TLS handshake — before any HTTP request is processed.</li>
 *   <li><strong>Network topology:</strong> Only the gateway can reach service-a
 *       in the production Docker/K8s network.</li>
 * </ol>
 *
 * <p>Application-level authentication (JWT/OAuth2) is therefore not applied here.
 * All requests that survive the TLS handshake are permitted. This bean overrides
 * any Spring Boot security auto-configuration that would otherwise require JWT.
 */
@Configuration
@EnableWebFluxSecurity
public class ServiceSecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // mTLS (TLS client cert) is the trust perimeter — permit all at app layer
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }
}
