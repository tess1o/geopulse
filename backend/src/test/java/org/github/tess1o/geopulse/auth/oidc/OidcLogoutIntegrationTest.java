package org.github.tess1o.geopulse.auth.oidc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.admin.service.OidcProviderConfigurationService;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.auth.session.model.SessionAuthMethod;
import org.github.tess1o.geopulse.auth.session.model.SessionContext;
import org.github.tess1o.geopulse.auth.session.service.UserSessionService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class OidcLogoutIntegrationTest {

    private static final String SESSION_COOKIE = "gp_session";
    private static final String ISSUER = "https://idp.geopulse.test";
    private static final String CLIENT_ID = "geopulse-client";
    private static final String END_SESSION_ENDPOINT = ISSUER + "/end-session";
    private static final String ID_TOKEN = "header.id-token-payload.signature";

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    UserSessionService userSessionService;

    @Inject
    OidcProviderConfigurationService providerConfigurationService;

    @Inject
    SystemSettingsService settingsService;

    private RSAKey signingKey;
    private String provider;
    private TestActor actor;
    private String subject;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        Path jwks = Files.createTempFile("geopulse-test-jwks", ".json");
        jwks.toFile().deleteOnExit();
        Files.writeString(jwks, new JWKSet(signingKey.toPublicJWK()).toString());

        provider = "idp" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        subject = "subject-" + UUID.randomUUID();
        saveProvider(jwks, END_SESSION_ENDPOINT);
        actor = QuarkusTransaction.requiringNew().call(() -> testUserFactory.createUser("oidc-logout"));
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> providerConfigurationService.deleteProvider(provider));
        settingsService.resetToDefault("auth.oidc.rp-logout.enabled");
    }

    @Test
    void backchannelLogoutBySidEndsOnlyThatProviderSession() throws Exception {
        String signedOut = oidcSession("sid-1");
        String otherIdpSession = oidcSession("sid-2");

        backchannel(logoutToken(claims -> claims.claim("sid", "sid-1"))).then().statusCode(200);

        assertSessionStatus(signedOut, 401);
        assertSessionStatus(otherIdpSession, 200);
    }

    @Test
    void backchannelLogoutBySubjectEndsAllProviderSessionsButNotPasswordSessions() throws Exception {
        String first = oidcSession("sid-1");
        String second = oidcSession("sid-2");
        String password = userSessionService.createBrowserSession(actor.user(),
                SessionContext.of(SessionAuthMethod.PASSWORD, null, null)).secret();

        backchannel(logoutToken(claims -> claims.subject(subject))).then().statusCode(200);

        assertSessionStatus(first, 401);
        assertSessionStatus(second, 401);
        assertSessionStatus(password, 200);
    }

    @Test
    void backchannelLogoutWithoutMatchingSessionStillSucceeds() throws Exception {
        backchannel(logoutToken(claims -> claims.claim("sid", "unknown-sid"))).then().statusCode(200);
    }

    @Test
    void invalidLogoutTokensAreRejectedAndSignOutNobody() throws Exception {
        String session = oidcSession("sid-1");
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID(signingKey.getKeyID()).generate();

        assertRejected(sign(otherKey, baseClaims().claim("sid", "sid-1").build()));
        assertRejected(logoutToken(claims -> claims.claim("sid", "sid-1").audience("another-client")));
        assertRejected(logoutToken(claims -> claims.claim("sid", "sid-1").issuer("https://evil.test")));
        assertRejected(logoutToken(claims -> claims.claim("sid", "sid-1").claim("nonce", "n")));
        assertRejected(logoutToken(claims -> claims.claim("sid", "sid-1").claim("events", Map.of())));
        assertRejected(logoutToken(claims -> claims.claim("sid", "sid-1")
                .issueTime(Date.from(Instant.now().minusSeconds(3600)))));
        assertRejected(logoutToken(claims -> claims.subject(null)));
        assertRejected("not-a-jwt");

        assertSessionStatus(session, 200);
    }

    @Test
    void logoutTokenDoesNotEndSessionsStartedAfterIt() throws Exception {
        String token = logoutToken(claims -> claims.subject(subject)
                .issueTime(Date.from(Instant.now().minusSeconds(200))));
        String startedLater = oidcSession("sid-new");

        backchannel(token).then().statusCode(200);

        assertSessionStatus(startedLater, 200);
    }

    @Test
    void logoutOfOidcSessionReturnsProviderLogoutUrl() {
        String session = oidcSession("sid-1");

        Response logout = given().cookie(SESSION_COOKIE, session).when().delete("/api/v1/auth/sessions/current");

        assertThat(logout.statusCode()).isEqualTo(200);
        String url = logout.jsonPath().getString("idpLogoutUrl");
        assertThat(url).startsWith(END_SESSION_ENDPOINT + "?");
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        assertThat(decoded).contains("client_id=" + CLIENT_ID)
                .contains("id_token_hint=" + ID_TOKEN)
                .contains("post_logout_redirect_uri=")
                .contains("/login");
        assertSessionStatus(session, 401);
    }

    @Test
    void logoutSkipsProviderWhenItHasNoEndSessionEndpoint() throws Exception {
        Path jwks = Files.createTempFile("geopulse-test-jwks", ".json");
        Files.writeString(jwks, new JWKSet(signingKey.toPublicJWK()).toString());
        saveProvider(jwks, null);
        String session = oidcSession("sid-1");

        Response logout = given().cookie(SESSION_COOKIE, session).when().delete("/api/v1/auth/sessions/current");

        assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(logout.jsonPath().getString("idpLogoutUrl")).isNull();
        assertSessionStatus(session, 401);
    }

    @Test
    void logoutSkipsProviderWhenRpLogoutIsDisabled() {
        settingsService.setValue("auth.oidc.rp-logout.enabled", "false", null);
        String session = oidcSession("sid-1");

        Response logout = given().cookie(SESSION_COOKIE, session).when().delete("/api/v1/auth/sessions/current");

        assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(logout.jsonPath().getString("idpLogoutUrl")).isNull();
    }

    private void saveProvider(Path jwks, String endSessionEndpoint) {
        OidcProviderConfiguration configuration = OidcProviderConfiguration.builder()
                .name(provider)
                .displayName("Test IdP")
                .enabled(true)
                .clientId(CLIENT_ID)
                .clientSecret("secret")
                .discoveryUrl(ISSUER + "/.well-known/openid-configuration")
                .authorizationEndpoint(ISSUER + "/authorize")
                .tokenEndpoint(ISSUER + "/token")
                .jwksUri(jwks.toUri().toString())
                .issuer(ISSUER)
                .endSessionEndpoint(endSessionEndpoint)
                .metadataCachedAt(Instant.now())
                .metadataValid(true)
                .build();
        QuarkusTransaction.requiringNew().run(() -> providerConfigurationService.saveProvider(configuration, null));
    }

    private String oidcSession(String sid) {
        return userSessionService.createBrowserSession(actor.user(),
                SessionContext.oidc(provider, subject, sid, ID_TOKEN, null, null)).secret();
    }

    private void assertSessionStatus(String session, int status) {
        given().cookie(SESSION_COOKIE, session).when().get("/api/v1/users/me").then().statusCode(status);
    }

    private void assertRejected(String logoutToken) {
        Response response = backchannel(logoutToken);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("code")).isEqualTo("OIDC_LOGOUT_TOKEN_INVALID");
    }

    private Response backchannel(String logoutToken) {
        return given()
                .contentType(ContentType.URLENC)
                .formParam("logout_token", logoutToken)
                .when().post("/api/v1/auth/oidc/backchannel-logouts/" + provider);
    }

    private JWTClaimsSet.Builder baseClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .subject(subject)
                .issueTime(new Date())
                .jwtID(UUID.randomUUID().toString())
                .claim("events", Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
    }

    private String logoutToken(Consumer<JWTClaimsSet.Builder> customizer) throws JOSEException {
        JWTClaimsSet.Builder claims = baseClaims();
        customizer.accept(claims);
        return sign(signingKey, claims.build());
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
