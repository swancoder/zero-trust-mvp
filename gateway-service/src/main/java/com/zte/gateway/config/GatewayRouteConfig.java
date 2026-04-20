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
 * (JWT required) and additionally by {@link com.zte.gateway.filter.ZteAuthorizationFilter}
 * (DB-backed role/path policy check).
 */
@Configuration
public class GatewayRouteConfig {

    private final String serviceAUri;

    public GatewayRouteConfig(
            @Value("${service-a.uri:http://localhost:8081}") String serviceAUri) {
        this.serviceAUri = serviceAUri;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("service-a", r -> r
                        .path("/api/v1/service-a/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Source", "zte-gateway"))
                        .uri(serviceAUri))
                .build();
    }
}
