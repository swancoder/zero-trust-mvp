package com.zte.auth.obo;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Creates and validates {@code X-ZTE-User-Context} on-behalf-of tokens.
 *
 * <p>The OBO token is a compact, HMAC-SHA256-signed JWT that carries the
 * original user's identity ({@code sub} and {@code roles}) from the gateway
 * through the internal service chain. It is distinct from the Keycloak JWT:
 *
 * <ul>
 *   <li>Short-lived (default 30 s) — limits blast radius of a stolen token.</li>
 *   <li>Signed with a shared internal secret ({@code ZTE_OBO_SECRET}) — not
 *       the Keycloak private key, so a compromised downstream service cannot
 *       impersonate the user to Keycloak.</li>
 *   <li>Gateway always strips any incoming {@code X-ZTE-User-Context} header
 *       before setting its own — prevents header injection by external callers.</li>
 * </ul>
 *
 * <p><strong>MVP limitation:</strong> A shared HMAC secret means any service
 * that knows {@code ZTE_OBO_SECRET} can forge tokens. Production upgrade:
 * switch to RS256 — gateway signs with its private key, services verify against
 * the gateway's public key exposed at a JWKS endpoint.
 *
 * <p>Instantiate as a Spring {@code @Bean} in each service's configuration:
 * <pre>{@code
 * @Bean
 * UserContextTokenService userContextTokenService(
 *         @Value("${zte.obo.secret}") String secret,
 *         @Value("${zte.obo.expiry-seconds:30}") long expirySeconds) {
 *     return new UserContextTokenService(secret, expirySeconds);
 * }
 * }</pre>
 */
public class UserContextTokenService {

    private static final String ISSUER       = "zte-gateway";
    private static final String CLAIM_ROLES  = "roles";
    private static final int    MIN_KEY_BYTES = 32; // HMAC-SHA256 requires ≥ 256 bits

    private final SecretKey key;
    private final long      expirySeconds;

    /**
     * @param secret        shared HMAC secret (padded to 32 bytes if shorter)
     * @param expirySeconds OBO token lifetime in seconds (e.g. 30)
     */
    public UserContextTokenService(String secret, long expirySeconds) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_KEY_BYTES) {
            keyBytes = Arrays.copyOf(keyBytes, MIN_KEY_BYTES);
        }
        this.key           = Keys.hmacShaKeyFor(keyBytes);
        this.expirySeconds = expirySeconds;
    }

    /**
     * Creates a signed {@code X-ZTE-User-Context} token for the given user.
     *
     * @param sub   Keycloak user UUID
     * @param roles realm-level roles from {@code realm_access.roles}
     * @return compact JWT string
     */
    public String createToken(String sub, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(sub)
                .claim(CLAIM_ROLES, roles)
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Validates the token signature and expiry, then parses the claims.
     *
     * @param token the compact JWT from the {@code X-ZTE-User-Context} header
     * @return parsed {@link UserContextClaims}
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public UserContextClaims validateAndParse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(CLAIM_ROLES, List.class);

        return new UserContextClaims(
                claims.getSubject(),
                roles != null ? roles : List.of(),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()
        );
    }
}
