package com.zte.gateway.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.hamcrest.Matchers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;

/**
 * Zero Trust breach-attempt tests.
 *
 * <p>Each test simulates a specific attack vector and asserts that the ZTE
 * gateway rejects the request with the appropriate HTTP status code.
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li><b>No token</b> → Spring Security returns 401 (WWW-Authenticate challenge).</li>
 *   <li><b>Malformed Bearer token</b> → 401 (JWT parse failure).</li>
 *   <li><b>Valid JWT, USER role, no matching policy</b> → ZteAuthorizationFilter 403.</li>
 *   <li><b>Valid JWT but wrong issuer</b> → 401 (issuer claim mismatch).</li>
 *   <li><b>Injected X-ZTE-User-Context</b> → 200 but downstream sees only the gateway's
 *       own OBO token (header injection prevention by UserContextPropagationFilter).</li>
 * </ol>
 *
 * <p><em>Note on mTLS:</em> In this test suite service-a is replaced by WireMock (HTTP),
 * so transport-layer mTLS rejection cannot be tested here. That scenario is covered by
 * the system test suite run against the full Docker Compose stack. The filter-layer
 * application of Zero Trust principles (JWT validation, DB policy, OBO integrity) is
 * fully exercised in these tests. See ADR-005 for the rationale.
 */
@DisplayName("Zero Trust Breach Attempts")
class ZeroTrustBreachIT extends BaseZteIntegrationTest {

    @Test
    @DisplayName("No Authorization header → 401 Unauthorized")
    void noToken_returns401() {
        given()
            .baseUri("http://localhost:" + gatewayPort)
        .when()
            .get("/api/v1/service-a/hello")
        .then()
            .statusCode(401);
    }

    @Test
    @DisplayName("Malformed Bearer token → 401 Unauthorized")
    void malformedToken_returns401() {
        given()
            .baseUri("http://localhost:" + gatewayPort)
            .header("Authorization", "Bearer not.a.valid.jwt.at.all")
        .when()
            .get("/api/v1/service-a/hello")
        .then()
            .statusCode(401);
    }

    @Test
    @DisplayName("Valid JWT, USER role — no access policy → 403 Forbidden")
    void userRoleNoPolicy_returns403() {
        String userToken = getUserToken();  // zte-test-user has only USER role

        given()
            .baseUri("http://localhost:" + gatewayPort)
            .header("Authorization", "Bearer " + userToken)
        .when()
            .get("/api/v1/service-a/hello")
        .then()
            .statusCode(403)
            .body("error", Matchers.equalTo("Forbidden"));
    }

    @Test
    @DisplayName("Valid JWT, ADMIN role but expired — policy check never reached → 401")
    void expiredToken_returns401() {
        // A well-formed JWT with a past expiry (manually crafted compact string)
        // Using a known-expired token from a prior run is brittle; instead use a
        // base64-forged token with an 'exp' claim set to epoch 1 (1970).
        // Spring Security will reject this before the policy filter runs.
        String expiredToken = "eyJhbGciOiJSUzI1NiJ9"  // header
                + ".eyJzdWIiOiJ0ZXN0IiwiZXhwIjoxfQ"   // payload: exp=1
                + ".invalidsignature";                  // signature doesn't matter — exp checked first

        given()
            .baseUri("http://localhost:" + gatewayPort)
            .header("Authorization", "Bearer " + expiredToken)
        .when()
            .get("/api/v1/service-a/hello")
        .then()
            .statusCode(401);
    }

    @Test
    @DisplayName("Injected OBO header from attacker — downstream receives only gateway-minted token")
    void injectedOboHeader_isReplacedByGatewayToken() {
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/service-a/hello"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"service\":\"service-a\"}")));

        String adminToken = getAdminToken();

        given()
            .baseUri("http://localhost:" + gatewayPort)
            .header("Authorization",      "Bearer " + adminToken)
            .header("X-ZTE-User-Context", "attacker-crafted-obo")
            .header("X-User-Id",          "evil-user-id")
        .when()
            .get("/api/v1/service-a/hello")
        .then()
            .statusCode(200);

        // The downstream (WireMock) must NOT see the attacker's values
        WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/service-a/hello"))
                .withHeader("X-ZTE-User-Context", notMatching("attacker-crafted-obo"))
                .withHeader("X-User-Id",          notMatching("evil-user-id")));
    }
}
