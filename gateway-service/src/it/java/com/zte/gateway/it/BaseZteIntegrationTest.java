package com.zte.gateway.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Shared base for all ZTE integration tests.
 *
 * <p>Containers are started <em>once</em> (singleton pattern) via the static
 * initialiser block and shared across all subclasses. Spring's test context
 * cache sees the same {@code @DynamicPropertySource} output for all subclasses
 * that extend this class, so a single {@code ApplicationContext} is created and
 * reused for the entire integration-test suite — no restart overhead.
 *
 * <p>Infrastructure summary:
 * <ul>
 *   <li><b>PostgreSQL 16</b> — Flyway migrations run at context startup, seeding
 *       the {@code access_policies} table (ADMIN → /api/v1/service-a/**).</li>
 *   <li><b>Keycloak 24.0.4</b> — {@code zte-realm} imported from
 *       {@code realm-export.json}; {@code zte-admin} password set via Admin Client.</li>
 *   <li><b>WireMock</b> — replaces service-a and service-b; stubs reset before
 *       each test via {@link #resetStubs()}.</li>
 * </ul>
 */
@SpringBootTest(
    classes  = com.zte.gateway.GatewayApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("it")
abstract class BaseZteIntegrationTest {

    // ── Shared password used for all test users ──────────────────────────────
    protected static final String TEST_PASSWORD  = "Admin@123!";
    protected static final String ADMIN_USERNAME = "zte-admin";
    protected static final String USER_USERNAME  = "zte-test-user";

    // ── Singleton containers (started exactly once per JVM) ──────────────────

    static final PostgreSQLContainer<?> POSTGRES;
    static final KeycloakContainer      KEYCLOAK;
    static final WireMockServer         WIREMOCK;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("zte_db")
                .withUsername("zte_user")
                .withPassword("zte_pass");

        KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:24.0.4")
                .withRealmImportFile("realm-export.json"); // on classpath from keycloak/ dir

        WIREMOCK = new WireMockServer(wireMockConfig().dynamicPort());

        POSTGRES.start();
        KEYCLOAK.start();
        WIREMOCK.start();

        provisionTestUsers();
    }

    // ── DynamicPropertySource wires containers into the Spring context ────────

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // JDBC (Flyway)
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Flyway
        registry.add("spring.flyway.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user",     POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);

        // R2DBC
        registry.add("spring.r2dbc.url",      () -> toR2dbcUrl(POSTGRES.getJdbcUrl()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);

        // Keycloak JWT validation — both issuer-uri (for iss claim) and jwk-set-uri
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> KEYCLOAK.getAuthServerUrl() + "realms/zte-realm");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> KEYCLOAK.getAuthServerUrl() + "realms/zte-realm/protocol/openid-connect/certs");

        // Downstream services → WireMock (HTTP, no mTLS needed in test)
        registry.add("service-a.uri", () -> "http://localhost:" + WIREMOCK.port());
        registry.add("service-b.uri", () -> "http://localhost:" + WIREMOCK.port());
    }

    // ── Dynamic port injection ────────────────────────────────────────────────

    @Value("${local.server.port}")
    protected int gatewayPort;

    // ── Before each test: reset WireMock stubs ───────────────────────────────

    @BeforeEach
    void resetStubs() {
        WIREMOCK.resetAll();
    }

    // ── Token helpers ─────────────────────────────────────────────────────────

    /**
     * Returns a JWT access token for the ADMIN role user ({@code zte-admin}).
     * Uses the Resource Owner Password Credentials grant.
     */
    protected String getAdminToken() {
        return fetchToken(ADMIN_USERNAME);
    }

    /**
     * Returns a JWT access token for the USER role test user ({@code zte-test-user}).
     */
    protected String getUserToken() {
        return fetchToken(USER_USERNAME);
    }

    private String fetchToken(String username) {
        return RestAssured.given()
                .contentType(ContentType.URLENC)
                .formParam("grant_type",    "password")
                .formParam("client_id",     "zte-gateway")
                .formParam("client_secret", "zte-gateway-secret")
                .formParam("username",      username)
                .formParam("password",      TEST_PASSWORD)
                .post(KEYCLOAK.getAuthServerUrl() + "realms/zte-realm/protocol/openid-connect/token")
                .then().statusCode(200)
                .extract().path("access_token");
    }

    // ── Keycloak provisioning (runs once, in static block) ───────────────────

    private static void provisionTestUsers() {
        try (Keycloak admin = KEYCLOAK.getKeycloakAdminClient()) {
            var realm = admin.realm("zte-realm");

            // 1. Set password for zte-admin (imported without credentials)
            String adminId = realm.users().search(ADMIN_USERNAME).get(0).getId();
            realm.users().get(adminId).resetPassword(credential(TEST_PASSWORD));

            // 2. Create zte-test-user with USER role only
            UserRepresentation user = new UserRepresentation();
            user.setUsername(USER_USERNAME);
            user.setEmail("zte-test-user@zte.local");
            user.setEnabled(true);
            user.setEmailVerified(true);

            var response = realm.users().create(user);
            String userId = extractId(response.getHeaderString("Location"));
            realm.users().get(userId).resetPassword(credential(TEST_PASSWORD));

            RoleRepresentation userRole = realm.roles().get("USER").toRepresentation();
            realm.users().get(userId).roles().realmLevel().add(List.of(userRole));
        }
    }

    private static CredentialRepresentation credential(String password) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(false);
        return cred;
    }

    private static String extractId(String locationHeader) {
        return locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
    }

    private static String toR2dbcUrl(String jdbcUrl) {
        // jdbc:postgresql://host:port/db[?params] → r2dbc:postgresql://host:port/db
        return jdbcUrl.replace("jdbc:", "r2dbc:").replaceAll("\\?.*$", "");
    }
}
