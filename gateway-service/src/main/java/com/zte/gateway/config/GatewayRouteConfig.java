package com.zte.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declarative route definitions for the ZTE gateway.
 *
 * <p>All routes are protected by default via {@code com.zte.auth.SecurityConfig}.
 * Add downstream service routes here as the system grows.
 */
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
            // Example: route /api/v1/hello → a hypothetical downstream service
            // Uncomment and adjust lb:// URI when services are registered.
            //
            // .route("example-service", r -> r
            //     .path("/api/v1/example/**")
            //     .filters(f -> f
            //         .stripPrefix(2)
            //         .addRequestHeader("X-Gateway-Source", "zte-gateway"))
            //     .uri("lb://example-service"))
            .build();
    }
}
