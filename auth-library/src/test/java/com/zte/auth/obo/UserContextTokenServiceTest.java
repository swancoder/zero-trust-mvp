package com.zte.auth.obo;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link UserContextTokenService}.
 *
 * <p>No Spring context required — tests verify the cryptographic contract
 * of the OBO token: correct round-trip, TTL expiry rejection, and signature
 * tamper detection.
 *
 * <p>Why mock R2DBC / no DB here: {@link UserContextTokenService} is a pure
 * JJWT HMAC utility. Pulling in a real DB (or even an R2DBC mock) would test
 * Spring Data infrastructure rather than the token logic itself, while adding
 * slow connection setup and fragile state. The correct integration test boundary
 * is the full request path (gateway → service-b via Testcontainers).
 */
class UserContextTokenServiceTest {

    private static final String       SECRET      = "test-secret-that-is-at-least-32-bytes-long";
    private static final String       SUBJECT     = "user-uuid-abc-123";
    private static final List<String> ROLES       = List.of("ADMIN", "USER");
    private static final long         EXPIRY_SECS = 30L;

    private UserContextTokenService service;

    @BeforeEach
    void setUp() {
        service = new UserContextTokenService(SECRET, EXPIRY_SECS);
    }

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    void createToken_producesCompactJwt() {
        String token = service.createToken(SUBJECT, ROLES);
        assertThat(token).isNotBlank();
        // Compact JWT format: header.payload.signature
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void validateAndParse_roundTripsSubjectAndRoles() {
        UserContextClaims claims = service.validateAndParse(service.createToken(SUBJECT, ROLES));

        assertThat(claims.sub()).isEqualTo(SUBJECT);
        assertThat(claims.roles()).containsExactlyInAnyOrderElementsOf(ROLES);
    }

    @Test
    void validateAndParse_expiryIsWithinExpectedWindow() {
        Instant before = Instant.now();
        String  token  = service.createToken(SUBJECT, ROLES);
        Instant after  = Instant.now();

        UserContextClaims claims = service.validateAndParse(token);

        assertThat(claims.issuedAt()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
        assertThat(claims.expiresAt())
                .isBetween(before.plusSeconds(EXPIRY_SECS - 1),
                           after.plusSeconds(EXPIRY_SECS + 1));
    }

    @Test
    void validateAndParse_emptyRoles_roundTripsToEmptyList() {
        UserContextClaims claims = service.validateAndParse(service.createToken(SUBJECT, List.of()));
        assertThat(claims.roles()).isEmpty();
    }

    // ── security: signature validation ────────────────────────────────────────

    @Test
    void validateAndParse_wrongSecret_throwsJwtException() {
        String token = service.createToken(SUBJECT, ROLES);

        UserContextTokenService otherService =
                new UserContextTokenService("completely-different-secret-value-xyz", EXPIRY_SECS);

        assertThatThrownBy(() -> otherService.validateAndParse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateAndParse_tamperedPayload_throwsJwtException() {
        String   token   = service.createToken(SUBJECT, ROLES);
        String[] parts   = token.split("\\.");
        // Append a character to the payload segment — signature mismatch
        String   tampered = parts[0] + "." + parts[1] + "X" + "." + parts[2];

        assertThatThrownBy(() -> service.validateAndParse(tampered))
                .isInstanceOf(JwtException.class);
    }

    // ── security: TTL expiry ──────────────────────────────────────────────────

    /**
     * Creates a token with a 1-second TTL, waits for expiry, and asserts that
     * validation throws {@link JwtException} (specifically {@code ExpiredJwtException}).
     */
    @Test
    void validateAndParse_expiredToken_throwsJwtException() throws InterruptedException {
        UserContextTokenService shortLived = new UserContextTokenService(SECRET, 1L);
        String token = shortLived.createToken(SUBJECT, ROLES);

        Thread.sleep(1500); // wait past the 1-second TTL

        assertThatThrownBy(() -> shortLived.validateAndParse(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }
}
