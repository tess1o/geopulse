package org.github.tess1o.geopulse.auth.oidc.http;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds the {@link SSLContext} used for outbound OIDC HTTPS requests (discovery, token, JWKS).
 * <p>
 * When {@value #CA_CERT_PATH_KEY} is set, the certificates from that PEM file (one or several) are trusted
 * <b>in addition to</b> the JVM's default trust store, so providers with public certificates keep working.
 * When it is empty, {@link #getSslContext()} returns {@code null} and the JVM defaults are used.
 * The setting is read on every call, so changing it in the admin UI takes effect without a restart;
 * the context is rebuilt only when the path or the file's modification time changes.
 */
@ApplicationScoped
@Slf4j
public class OidcTlsConfig {

    public static final String CA_CERT_PATH_KEY = "auth.oidc.tls.ca-cert-path";

    private record Cached(String path, long lastModified, SSLContext context) {}

    @Inject
    SystemSettingsService settingsService;

    private volatile Cached cached;

    /**
     * @return a context trusting the configured CA(s), or {@code null} when no custom CA is configured
     * @throws IllegalStateException if a path is configured but the file cannot be used
     */
    public SSLContext getSslContext() {
        String path = settingsService == null ? "" : settingsService.getString(CA_CERT_PATH_KEY);
        path = path == null ? "" : path.trim();
        if (path.isEmpty()) {
            cached = null;
            return null;
        }

        long lastModified = lastModified(path);
        Cached current = cached;
        if (current != null && current.path.equals(path) && current.lastModified == lastModified) {
            return current.context;
        }
        synchronized (this) {
            current = cached;
            if (current != null && current.path.equals(path) && current.lastModified == lastModified) {
                return current.context;
            }
            SSLContext context = build(path);
            cached = new Cached(path, lastModified, context);
            return context;
        }
    }

    /**
     * Validates a CA file without caching it. Used when saving the setting.
     *
     * @return number of certificates found
     * @throws IllegalArgumentException with a human-readable reason
     */
    public static int validate(String path) {
        try {
            return loadCertificates(path).size();
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static long lastModified(String path) {
        try {
            return Files.getLastModifiedTime(Path.of(path)).toMillis();
        } catch (IOException | RuntimeException e) {
            return -1L; // build() reports the real reason
        }
    }

    private SSLContext build(String path) {
        List<X509Certificate> custom = loadCertificates(path);
        try {
            KeyStore customStore = KeyStore.getInstance(KeyStore.getDefaultType());
            customStore.load(null, null);
            for (int i = 0; i < custom.size(); i++) {
                customStore.setCertificateEntry("oidc-custom-ca-" + i, custom.get(i));
            }

            X509TrustManager defaultTm = trustManagerFor(null);
            X509TrustManager customTm = trustManagerFor(customStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new CompositeTrustManager(defaultTm, customTm)}, null);
            log.info("OIDC TLS: trusting {} custom CA certificate(s) from {} in addition to the JVM defaults",
                    custom.size(), path);
            return context;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to initialise TLS with CA file " + path + ": " + e.getMessage(), e);
        }
    }

    private static List<X509Certificate> loadCertificates(String path) {
        Path file = Path.of(path);
        try (InputStream in = Files.newInputStream(file)) {
            Collection<? extends Certificate> certs = CertificateFactory.getInstance("X.509").generateCertificates(in);
            List<X509Certificate> result = new ArrayList<>();
            for (Certificate c : certs) {
                if (c instanceof X509Certificate x509) {
                    result.add(x509);
                }
            }
            if (result.isEmpty()) {
                throw new IllegalStateException("No valid PEM certificates found in " + path);
            }
            return result;
        } catch (NoSuchFileException e) {
            throw new IllegalStateException("CA certificate file not found: " + path
                    + " (is the volume mounted into the container?)", e);
        } catch (java.nio.file.AccessDeniedException e) {
            throw new IllegalStateException("CA certificate file is not readable: " + path
                    + " (check file permissions)", e);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not read CA certificates from " + path + ": " + e.getMessage(), e);
        }
    }

    private static X509TrustManager trustManagerFor(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store); // null = JVM default trust store
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new GeneralSecurityException("No X509TrustManager available");
    }

    /** Accepts a chain if either the JVM defaults or the custom CAs accept it. */
    private static final class CompositeTrustManager extends javax.net.ssl.X509ExtendedTrustManager {
        private final X509TrustManager first;
        private final X509TrustManager second;

        CompositeTrustManager(X509TrustManager first, X509TrustManager second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            try {
                first.checkServerTrusted(chain, authType);
            } catch (java.security.cert.CertificateException e) {
                second.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws java.security.cert.CertificateException {
            try {
                checkWith(first, chain, authType, socket, null);
            } catch (java.security.cert.CertificateException e) {
                checkWith(second, chain, authType, socket, null);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException {
            try {
                checkWith(first, chain, authType, null, engine);
            } catch (java.security.cert.CertificateException e) {
                checkWith(second, chain, authType, null, engine);
            }
        }

        // The socket/engine overloads let the delegate also verify the server's host name.
        private static void checkWith(X509TrustManager tm, X509Certificate[] chain, String authType,
                                      Socket socket, javax.net.ssl.SSLEngine engine)
                throws java.security.cert.CertificateException {
            if (tm instanceof javax.net.ssl.X509ExtendedTrustManager ext) {
                if (socket != null) {
                    ext.checkServerTrusted(chain, authType, socket);
                } else {
                    ext.checkServerTrusted(chain, authType, engine);
                }
            } else {
                tm.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            first.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws java.security.cert.CertificateException {
            first.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException {
            first.checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] a = first.getAcceptedIssuers();
            X509Certificate[] b = second.getAcceptedIssuers();
            X509Certificate[] all = java.util.Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, all, a.length, b.length);
            return all;
        }
    }
}
