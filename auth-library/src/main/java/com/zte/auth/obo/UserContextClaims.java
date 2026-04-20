package com.zte.auth.obo;

import java.time.Instant;
import java.util.List;

/**
 * Parsed claims from an {@code X-ZTE-User-Context} on-behalf-of token.
 *
 * <p>Carries the original user's identity and realm roles as asserted by
 * the ZTE gateway. Services use this to know <em>who</em> the request is
 * being performed on behalf of — the mTLS client cert proves <em>which</em>
 * internal service is making the call.
 *
 * @param sub       Keycloak user UUID (the {@code sub} claim of the original JWT)
 * @param roles     realm-level roles from {@code realm_access.roles}
 * @param issuedAt  token creation time
 * @param expiresAt token expiry (typically 30 seconds after issuedAt)
 */
public record UserContextClaims(
        String       sub,
        List<String> roles,
        Instant      issuedAt,
        Instant      expiresAt
) {}
