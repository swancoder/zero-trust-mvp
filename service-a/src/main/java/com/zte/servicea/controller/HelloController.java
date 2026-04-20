package com.zte.servicea.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo controller for service-a.
 *
 * <p>On receiving a request, service-a:
 * <ol>
 *   <li>Reads the {@code X-ZTE-User-Context} header set by the gateway
 *       (the signed OBO token).</li>
 *   <li>Forwards it unchanged to service-b over mTLS — delegation chain, not
 *       re-issuance. service-b validates the gateway's signature independently.</li>
 *   <li>Returns a combined response containing service-a's own message and
 *       service-b's user context response.</li>
 * </ol>
 *
 * <p>Access is controlled at the gateway by the DB-backed policy engine
 * ({@code ZteAuthorizationFilter}). service-a trusts inbound calls only if
 * the caller presents a client cert signed by the ZTE CA (enforced by
 * {@code server.ssl.client-auth=need} in {@code application.yml}).
 */
@RestController
public class HelloController {

    private static final Logger log = LoggerFactory.getLogger(HelloController.class);

    private final WebClient serviceBWebClient;

    public HelloController(WebClient serviceBWebClient) {
        this.serviceBWebClient = serviceBWebClient;
    }

    @GetMapping("/api/v1/service-a/hello")
    public Mono<ResponseEntity<Map<String, Object>>> hello(
            @RequestHeader(value = "X-User-Id",          required = false) String userId,
            @RequestHeader(value = "X-ZTE-User-Context", required = false) String userContext,
            @RequestHeader(value = "X-Gateway-Source",   required = false) String gatewaySource) {

        log.debug("Received request from gateway={} user={}", gatewaySource, userId);

        // Forward the OBO token to service-b unchanged (delegation — not re-issuance).
        // service-b validates the gateway's HMAC signature independently.
        Mono<String> serviceBResponse = serviceBWebClient.get()
                .uri("/api/v1/service-b/context")
                .header("X-ZTE-User-Context", userContext != null ? userContext : "")
                .header("X-User-Id",          userId    != null ? userId    : "")
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.warn("service-b call failed: {}", e.getMessage()))
                .onErrorReturn("{\"error\":\"service-b unavailable\"}");

        return serviceBResponse.map(serviceBBody -> {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("service",      "service-a");
            response.put("caller",       userId != null ? userId : "unknown");
            response.put("message",      "Hello from Protected Service A");
            response.put("service-b",    serviceBBody);
            return ResponseEntity.ok(response);
        });
    }
}
