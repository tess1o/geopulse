package org.github.tess1o.geopulse.gps.integrations.owntracks.service;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class OwnTracksPayloadEncryptionNativeTestResource implements QuarkusTestResourceLifecycleManager {

    private final PostgisTestResource postgis = new PostgisTestResource();

    @Override
    public Map<String, String> start() {
        Map<String, String> config = new HashMap<>(postgis.start());
        Path testResources = backendDir().resolve("src/test/resources");
        Path jwtPrivateKey = testResources.resolve("keys/jwt-private-key.pem");
        Path jwtPublicKey = testResources.resolve("keys/jwt-public-key.pem");

        config.put("smallrye.jwt.sign.key", readString(jwtPrivateKey));
        config.put("smallrye.jwt.sign.key.location", "file:" + jwtPrivateKey);
        config.put("mp.jwt.verify.publickey", readString(jwtPublicKey));
        config.put("mp.jwt.verify.publickey.location", "file:" + jwtPublicKey);
        config.put("geopulse.ai.encryption.key.location", "file:" + testResources.resolve("keys/ai-encryption-key.txt"));
        config.put("geopulse.geonames.import.enabled", "false");
        config.put("geopulse.geonames.country-import.enabled", "false");
        config.put("geopulse.warmup.enabled", "false");
        config.put("geopulse.admin.first-user-admin.enabled", "false");
        config.put("geopulse.import.scheduler.enabled", "false");
        config.put("quarkus.scheduler.enabled", "false");
        return config;
    }

    @Override
    public void stop() {
        postgis.stop();
    }

    private Path backendDir() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("src/test/resources"))) {
            return cwd;
        }
        return cwd.resolve("backend");
    }

    private String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read test resource " + path, e);
        }
    }
}
