package com.zte.servicea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo downstream service protected by the ZTE gateway.
 *
 * <p>This service has NO authentication of its own — it relies entirely on the
 * gateway to enforce Zero Trust policy before forwarding requests. Requests
 * reaching this service have already been validated (JWT) and authorized
 * (DB policy match) by the gateway.
 *
 * <p>The gateway injects the trusted {@code X-User-Id} header (JWT sub claim)
 * so downstream services can identify the caller without re-validating the JWT.
 */
@SpringBootApplication
public class ServiceAApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceAApplication.class, args);
    }
}
