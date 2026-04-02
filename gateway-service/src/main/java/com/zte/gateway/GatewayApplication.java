package com.zte.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ZTE-Lightweight API Gateway.
 *
 * <p>Single ingress point enforcing Zero Trust: every request must carry a valid
 * JWT issued by Keycloak before being forwarded to a downstream service.
 *
 * <p>Architecture: Spring Cloud Gateway (WebFlux reactive) + Spring Security
 * OAuth2 Resource Server (JWT validation via Keycloak JWKS endpoint).
 */
@SpringBootApplication(scanBasePackages = {"com.zte.gateway", "com.zte.auth"})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
