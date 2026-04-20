package com.zte.serviceb;

import com.zte.auth.obo.UserContextClaims;
import com.zte.auth.obo.UserContextTokenService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the OBO token round-trip: create → validate → parse.
 *
 * <p>These tests run without any Spring context — they verify the core
 * cryptographic guarantee of the on-behalf-of mechanism:
 * <ul>
 *   <li>A token created with secret A can be validated with the same secret A.</li>
 *   <li>A token created with secret A cannot be validated with secret B
 *       (tamper detection).</li>
 *   <li>Claims round-trip correctly (sub, roles, expiry).</li>
 *   <li>An expired token is rejected.</li>
 * </ul>
 *
 * <p>Full end-to-end scenario test (User → Gateway → Service A → Service B)
 * requires all services running with generated certs. Run:
 * <pre>
 *   chmod +x certs/generate-certs.sh && ./certs/generate-certs.sh
 *   ./gradlew :service-b:bootRun &
 *   ./gradlew :service-a:bootRun &
 *   ./gradlew :gateway-service:bootRun &
 *   # obtain ADMIN token, then:
 *   curl -sk -H "Authorization: Bearer $TOKEN" https://localhost:8080/api/v1/service-a/hello
 * </pre>
 */
class UserContextTokenServiceTest {

    private static final String SECRET        = "test-secret-that-is-at-least-32-bytes-long";
    private static final String SUB           = "user-uuid-abc-123";
    private static final List<String> ROLES   = List.of("ADMIN", "USER");
    private static final long   EXPIRY_SECS   = 30L;

    private UserContextTokenService service;

    @BeforeEach
    void setUp() {
        service = new UserContextTokenService(SECRET, EXPIRY_SECS);
    }

    @Test
    void createToken_isNotBlank() {
        String token = service.createToken(SUB, ROLES);
        assertThat(token).isNotBlank();
        // Compact JWT has exactly 3 Base64url segments separated by dots
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void validateAndParse_roundTripsSubAndRoles() {
        String           token  = service.createToken(SUB, ROLES);
        UserContextClaims claims = service.validateAndParse(token);

        assertThat(claims.sub()).isEqualTo(SUB);
        assertThat(claims.roles()).containsExactlyInAnyOrderElementsOf(ROLES);
    }

    @Test
    void validateAndParse_expiryIsWithinExpectedWindow() {
        Instant before = Instant.now();
        String  token  = service.createToken(SUB, ROLES);
        Instant after  = Instant.now();

        UserContextClaims claims = service.validateAndParse(token);

        assertThat(claims.issuedAt()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
        assertThat(claims.expiresAt())
                .isBetween(before.plusSeconds(EXPIRY_SECS - 1),
                           after.plusSeconds(EXPIRY_SECS + 1));
    }

    @Test
    void validateAndParse_withWrongSecret_throwsJwtException() {
        String token = service.createToken(SUB, ROLES);

        UserContextTokenService otherService = new UserContextTokenService(
                "completely-different-secret-value", EXPIRY_SECS);

        assertThatThrownBy(() -> otherService.validateAndParse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateAndParse_withTamperedPayload_throwsJwtException() {
        String token = service.createToken(SUB, ROLES);
        // Flip one character in the payload segment to simulate tampering
        String[] parts = token.split("\\.");
        String   tampered = parts[0] + "." + parts[1] + "X" + "." + parts[2];

        assertThatThrownBy(() -> service.validateAndParse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateAndParse_expiredToken_throwsJwtException() throws InterruptedException {
        UserContextTokenService shortLived = new UserContextTokenService(SECRET, 1L); // 1s TTL
        String token = shortLived.createToken(SUB, ROLES);

        Thread.sleep(1500); // wait for expiry

        assertThatThrownBy(() -> shortLived.validateAndParse(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("JWT expired");
    }

    @Test
    void createToken_withEmptyRoles_roundTripsEmptyList() {
        String           token  = service.createToken(SUB, List.of());
        UserContextClaims claims = service.validateAndParse(token);

        assertThat(claims.roles()).isEmpty();
    }
}
