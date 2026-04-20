package com.zte.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declarative route definitions for the ZTE gateway.
 *
 * <p>All routes are protected by default via {@code com.zte.auth.SecurityConfig}
 * (JWT required), {@link com.zte.gateway.filter.ZteAuthorizationFilter}
 * (DB-backed role/path policy check), and
 * {@link com.zte.gateway.filter.UserContextPropagationFilter}
 * (attaches signed X-ZTE-User-Context OBO header).
 *
 * <p>Downstream URIs use {@code https://} — service-a and service-b require mTLS.
 * The gateway presents {@code client.p12} as its client certificate (configured
 * via {@link MtlsHttpClientConfig}).
 */
@Configuration
public class GatewayRouteConfig {

    private final String serviceAUri;
    private final String serviceBUri;

    public GatewayRouteConfig(
            @Value("${service-a.uri:https://localhost:8081}") String serviceAUri,
            @Value("${service-b.uri:https://localhost:8082}") String serviceBUri) {
        this.serviceAUri = serviceAUri;
        this.serviceBUri = serviceBUri;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("service-a", r -> r
                        .path("/api/v1/service-a/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Source", "zte-gateway"))
                        .uri(serviceAUri))
                .route("service-b", r -> r
                        .path("/api/v1/service-b/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Source", "zte-gateway"))
                        .uri(serviceBUri))
                .build();
    }
}
