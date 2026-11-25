package org.github.tess1o.geopulse.auth.oidc.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jakarta.enterprise.event.Event;
import org.github.tess1o.geopulse.admin.repository.SystemSettingsRepository;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.mapmatching.event.MapMatchingSettingsChangedEvent;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plain unit test (no Quarkus): starts local HTTPS servers whose self-signed certificates are NOT trusted by the
 * JVM and checks that {@link OidcHttpClient} reaches them only when the certificate is configured as a custom CA.
 * Every rejection is asserted to be a TLS handshake failure, so a refused connection or a server error cannot
 * make a test pass by accident.
 */
@Tag("unit")
class OidcHttpClientTlsTest {

    @TempDir
    Path tmp;

    /** A running HTTPS server plus its certificate in PEM form. */
    private record TlsServer(HttpsServer server, String pem, String url) {}

    private final List<HttpsServer> servers = new ArrayList<>();
    private TlsServer trusted;
    private Path caPem;
    private SystemSettingsService settings;
    private OidcHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        trusted = startServer("srv", "dns:localhost,ip:127.0.0.1");
        caPem = tmp.resolve("ca.pem");
        Files.writeString(caPem, trusted.pem());

        settings = mock(SystemSettingsService.class);
        configureCaPath("");
        OidcTlsConfig tls = new OidcTlsConfig();
        tls.settingsService = settings;
        client = new OidcHttpClient();
        client.tlsConfig = tls;
        client.objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        servers.forEach(s -> s.stop(0));
    }

    @Test
    void privateCaIsRejectedWithoutConfiguration() {
        assertTlsRejected(() -> client.getString(trusted.url()));
    }

    @Test
    void privateCaIsAcceptedWhenConfigured() {
        configureCaPath(caPem.toString());

        assertTrue(client.getString(trusted.url()).contains("\"method\":\"GET\""));

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", "https://app.example/cb?a=1&b=2");
        OidcHttpClient.Response post = client.postForm(trusted.url(), form);
        assertEquals(200, post.status());
        assertTrue(post.body().contains("\"method\":\"POST\""), post.body());
        assertTrue(post.body().contains(
                "grant_type=authorization_code&redirect_uri=https%3A%2F%2Fapp.example%2Fcb%3Fa%3D1%26b%3D2"), post.body());
    }

    @Test
    void getJsonParsesTheResponse() {
        configureCaPath(caPem.toString());
        Map<?, ?> json = client.getJson(trusted.url(), Map.class);
        assertEquals("GET", json.get("method"));
    }

    @Test
    void clientFollowsSettingChangesWithoutRestart() {
        configureCaPath(caPem.toString());
        assertDoesNotThrow(() -> client.getString(trusted.url()));

        configureCaPath("");
        assertTlsRejected(() -> client.getString(trusted.url()));

        configureCaPath(caPem.toString());
        assertDoesNotThrow(() -> client.getString(trusted.url()));
    }

    @Test
    void replacedCaFileIsReloadedWithoutRestart() throws Exception {
        TlsServer other = startServer("other", "dns:localhost,ip:127.0.0.1");
        Path rotating = tmp.resolve("rotating.pem");
        Files.writeString(rotating, other.pem());
        configureCaPath(rotating.toString());

        assertTlsRejected(() -> client.getString(trusted.url()));

        // Same path, new contents and a later modification time, like a rotated mounted file.
        FileTime before = Files.getLastModifiedTime(rotating);
        Files.writeString(rotating, trusted.pem());
        Files.setLastModifiedTime(rotating, FileTime.fromMillis(before.toMillis() + 5_000));

        assertDoesNotThrow(() -> client.getString(trusted.url()));
    }

    @Test
    void customCaDoesNotDisableHostNameVerification() throws Exception {
        // Trusted certificate, but issued for a different host than the one we connect to.
        TlsServer wrongHost = startServer("wrong", "dns:idp.example");
        Path wrongHostCa = tmp.resolve("wrong-host.pem");
        Files.writeString(wrongHostCa, wrongHost.pem());
        configureCaPath(wrongHostCa.toString());

        SSLHandshakeException e = assertTlsRejected(() -> client.getString(wrongHost.url()));
        assertTrue(e.getMessage().toLowerCase().contains("localhost"),
                "expected a host name mismatch, got: " + e.getMessage());
    }

    @Test
    void nonSuccessStatusIsReportedForGetAndReturnedForPost() {
        configureCaPath(caPem.toString());
        String missing = trusted.url().replace("/echo", "/missing");

        OidcHttpException e = assertThrows(OidcHttpException.class, () -> client.getString(missing));
        assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());

        assertEquals(404, client.postForm(missing, Map.of("a", "b")).status());
    }

    @Test
    void emptyPathMeansJvmDefaults() {
        configureCaPath("   ");
        assertNull(client.tlsConfig.getSslContext());
    }

    @Test
    void missingFileGivesHelpfulError() {
        configureCaPath(tmp.resolve("nope.pem").toString());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> client.tlsConfig.getSslContext());
        assertTrue(e.getMessage().contains("CA certificate file not found"), e.getMessage());
    }

    @Test
    void fileWithoutCertificatesIsRejected() throws Exception {
        Path bad = tmp.resolve("bad.pem");
        Files.writeString(bad, "this is not a certificate");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> OidcTlsConfig.validate(bad.toString()));
        assertTrue(e.getMessage().contains("certificate"), e.getMessage());
    }

    @Test
    void validateCountsCertificatesInBundle() throws Exception {
        TlsServer other = startServer("other", "dns:localhost");
        Path bundle = tmp.resolve("bundle.pem");
        Files.writeString(bundle, trusted.pem() + "\n" + other.pem());
        assertEquals(2, OidcTlsConfig.validate(bundle.toString()));
    }

    @Test
    void savingTheSettingChecksTheFile() {
        SystemSettingsService real = realSettingsService();

        assertDoesNotThrow(() -> real.validateValueForImport(OidcTlsConfig.CA_CERT_PATH_KEY, ""));
        assertDoesNotThrow(() -> real.validateValueForImport(OidcTlsConfig.CA_CERT_PATH_KEY, caPem.toString()));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> real.validateValueForImport(OidcTlsConfig.CA_CERT_PATH_KEY, tmp.resolve("nope.pem").toString()));
        assertTrue(e.getMessage().contains("CA certificate file not found"), e.getMessage());
    }

    private void configureCaPath(String path) {
        when(settings.getString(OidcTlsConfig.CA_CERT_PATH_KEY)).thenReturn(path);
    }

    /** Asserts the call fails because the TLS handshake was rejected, and returns that handshake failure. */
    private static SSLHandshakeException assertTlsRejected(Executable call) {
        OidcHttpException e = assertThrows(OidcHttpException.class, call);
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SSLHandshakeException handshake) {
                return handshake;
            }
        }
        fail("Expected a TLS handshake failure, got: " + e);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static SystemSettingsService realSettingsService() {
        return new SystemSettingsService(
                mock(SystemSettingsRepository.class),
                mock(AIEncryptionService.class),
                mock(Event.class),
                mock(Event.class));
    }

    private TlsServer startServer(String alias, String san) throws Exception {
        Path keystore = tmp.resolve(alias + ".p12");
        keytool("-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=" + alias, "-ext", "SAN=" + san,
                "-keystore", keystore.toString(), "-storetype", "PKCS12", "-storepass", "changeit");
        String pem = keytool("-exportcert", "-rfc", "-alias", alias,
                "-keystore", keystore.toString(), "-storepass", "changeit");

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) {
            ks.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), null, null);

        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        server.createContext("/echo", exchange -> {
            byte[] req = exchange.getRequestBody().readAllBytes();
            byte[] body = ("{\"method\":\"" + exchange.getRequestMethod() + "\",\"body\":\""
                    + new String(req, StandardCharsets.UTF_8) + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        servers.add(server);
        return new TlsServer(server, pem, "https://localhost:" + server.getAddress().getPort() + "/echo");
    }

    private static String keytool(String... args) throws Exception {
        String bin = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        String[] cmd = new String[args.length + 1];
        cmd[0] = bin;
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), out);
        return out;
    }
}
