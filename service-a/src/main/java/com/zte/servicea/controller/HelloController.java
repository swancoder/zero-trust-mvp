package com.zte.servicea.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo controller for service-a.
 *
 * <p>Exposed at {@code GET /api/v1/service-a/hello}. Access is controlled
 * entirely by the ZTE gateway's DB-backed policy engine — only callers whose
 * JWT contains the {@code ADMIN} realm role are forwarded here.
 */
@RestController
public class HelloController {

    @GetMapping("/api/v1/service-a/hello")
    public ResponseEntity<String> hello(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String caller = userId != null ? userId : "unknown";
        return ResponseEntity.ok("Hello from Protected Service A — caller: " + caller);
    }
}
