package org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Configuration class for conditional MQTT support.
 * Only activates MQTT functionality when geopulse.mqtt.enabled is set to true.
 */
@ApplicationScoped
@Slf4j
@Getter
public class MqttConfiguration {

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.enabled")
    boolean mqttEnabledConfig;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.broker.host")
    String brokerHost;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.broker.port")
    int brokerPort;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.username")
    String username;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.password")
    String password;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.tls.enabled")
    boolean tlsEnabled;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.tls.protocol")
    String tlsProtocol;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.tls.truststore.path")
    String tlsTruststorePath;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.tls.truststore.password")
    String tlsTruststorePassword;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.tls.truststore.type")
    String tlsTruststoreType;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.tls.keystore.path")
    String tlsKeystorePath;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.tls.keystore.password")
    String tlsKeystorePassword;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.tls.keystore.type")
    String tlsKeystoreType;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.tls.insecure-skip-hostname-verification")
    boolean tlsInsecureSkipHostnameVerification;

    private boolean mqttEnabled;

    @PostConstruct
    void init() {
        // Validate configuration when MQTT is enabled
        if (!mqttEnabledConfig) {
            this.mqttEnabled = false;
            log.debug("MQTT support is disabled");
            return;
        }
        if (!hasText(brokerHost)) {
            log.error("MQTT is enabled but broker host is not configured");
            this.mqttEnabled = false;
            return;
        }
        if (tlsEnabled && !validateTlsFiles()) {
            this.mqttEnabled = false;
            return;
        }
        this.mqttEnabled = true;
        log.info("MQTT support is enabled - broker: {}:{}", brokerHost, brokerPort);
        if (tlsEnabled) {
            log.info("MQTT TLS is enabled for external broker connection");
        }
    }

    /**
     * Get MQTT broker URL for connection
     */
    public String getBrokerUrl() {
        String scheme = tlsEnabled ? "ssl" : "tcp";
        return scheme + "://" + brokerHost + ":" + brokerPort;
    }

    /**
     * Check if authentication is required
     */
    public boolean hasCredentials() {
        return hasText(username) && hasText(password);
    }

    /**
     * Build MQTT TLS socket factory from configured truststore/keystore.
     * Truststore and keystore are optional; JVM defaults are used when omitted.
     */
    public SSLSocketFactory buildTlsSocketFactory() throws GeneralSecurityException, IOException {
        SSLContext sslContext = SSLContext.getInstance(tlsProtocol);

        KeyManager[] keyManagers = null;
        if (hasText(tlsKeystorePath)) {
            KeyStore keyStore = loadKeyStore(tlsKeystorePath, tlsKeystoreType, tlsKeystorePassword);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, tlsKeystorePassword.toCharArray());
            keyManagers = keyManagerFactory.getKeyManagers();
        }

        TrustManager[] trustManagers = null;
        if (hasText(tlsTruststorePath)) {
            KeyStore trustStore = loadKeyStore(tlsTruststorePath, tlsTruststoreType, tlsTruststorePassword);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        }

        sslContext.init(keyManagers, trustManagers, new SecureRandom());
        return sslContext.getSocketFactory();
    }

    private KeyStore loadKeyStore(String path, String type, String password) throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(type);
        try (InputStream inputStream = Files.newInputStream(Path.of(path))) {
            keyStore.load(inputStream, password.toCharArray());
        }
        return keyStore;
    }

    private boolean validateTlsFiles() {
        if (hasText(tlsTruststorePath) && !isReadableFile(tlsTruststorePath)) {
            log.error("MQTT TLS truststore file does not exist or is not readable: {}", tlsTruststorePath);
            return false;
        }
        if (hasText(tlsKeystorePath) && !isReadableFile(tlsKeystorePath)) {
            log.error("MQTT TLS keystore file does not exist or is not readable: {}", tlsKeystorePath);
            return false;
        }
        return true;
    }

    private boolean isReadableFile(String path) {
        try {
            Path filePath = Path.of(path);
            return Files.isRegularFile(filePath) && Files.isReadable(filePath);
        } catch (RuntimeException e) {
            log.error("Invalid MQTT TLS file path: {}", path, e);
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
