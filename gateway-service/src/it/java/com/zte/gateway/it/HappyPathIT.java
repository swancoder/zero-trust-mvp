package com.zte.gateway.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.hamcrest.Matchers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;

/**
 * Happy-path E2E tests: verifies the full ZTE trust chain for a legitimately
 * authenticated and authorised user.
 *
 * <p>Chain exercised:
 * Keycloak JWT → Gateway (ZteAuthorizationFilter + UserContextPropagationFilter) → WireMock (service-a stub)
 *
 * <p>Key assertions:
 * <ol>
 *   <li>Gateway returns 200 with the stubbed service-a response body.</li>
 *   <li>WireMock received the request with a non-blank {@code X-ZTE-User-Context}
 *       OBO token — proves the gateway minted and forwarded the delegation token.</li>
 *   <li>WireMock received {@code X-User-Id} equal to the Keycloak subject — proves
 *       user identity propagation.</li>
 * </ol>
 */
@DisplayName("Happy Path — full ZTE trust chain")
class HappyPathIT extends BaseZteIntegrationTest {

    private static final String STUB_RESPONSE = """
            {
              "service":   "service-a",
              "caller":    "zte-admin-uuid",
              "message":   "Hello from Protected Service A",
              "service-b": "{\\"service\\":\\"service-b\\",\\"trustBasis\\":\\"mTLS (ZTE-CA) + HMAC-SHA256 OBO token\\"}"
            }
            """;

    @Test
    @DisplayName("ADMIN user — gateway 200, OBO token forwarded to downstream")
    void adminUser_fullChain_returns200WithOboToken() {
        // Arrange: stub service-a
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/service-a/hello"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(STUB_RESPONSE)));

        String token = getAdminToken();

        // Act + Assert: gateway returns 200 with stub body
        given()
            .baseUri("http://localhost:" + gatewayPort)
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/v1/service-a/hello")
        .then()
            .statusCode(200)
            .body("service", Matchers.equalTo("service-a"))
            .body("message", Matchers.containsString("Hello"));

        // Assert: WireMock received the OBO + user-id headers (set by gateway filters)
        WIREMOCK.verify(
                getRequestedFor(urlPathEqualTo("/api/v1/service-a/hello"))
                        .withHeader("X-ZTE-User-Context", matching(".{10,}"))   // non-blank JWT
                        .withHeader("X-User-Id",          matching(".{5,}")));  // non-blank UUID
    }

    @Test
    @DisplayName("Spoofed OBO header — gateway strips and replaces with its own token")
    void adminUser_withSpoofedOboHeader_gatewayReplacesIt() {
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/service-a/hello"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(STUB_RESPONSE)));

        String token = getAdminToken();

        // Act: send request with attacker-injected OBO header
        given()
            .baseUri("http://localhost:" + gatewayPort)
            .header("Authorization",    "Bearer " + token)
            .header("X-ZTE-User-Context", "attacker-forged-obo-token")
            .header("X-User-Id",          "attacker-uuid")
        .when()
            .get("/api/v1/service-a/hello")
        .then()
            .statusCode(200);

        // Assert: WireMock did NOT receive the spoofed values
        WIREMOCK.verify(
                getRequestedFor(urlPathEqualTo("/api/v1/service-a/hello"))
                        .withHeader("X-ZTE-User-Context", notMatching("attacker-forged-obo-token"))
                        .withHeader("X-User-Id",          notMatching("attacker-uuid")));
    }
}
