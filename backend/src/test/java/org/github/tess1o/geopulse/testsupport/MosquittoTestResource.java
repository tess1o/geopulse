package org.github.tess1o.geopulse.testsupport;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * A Mosquitto broker for the OwnTracks MQTT tests. Anonymous access is allowed and no password file is
 * configured, so the broker accepts the backend's configured MQTT credentials without checking them.
 * Use with {@code restrictToAnnotatedClass = true}: turning MQTT on for every test would make each Quarkus
 * boot connect to a broker.
 */
public class MosquittoTestResource implements QuarkusTestResourceLifecycleManager {

    private static final int MQTT_PORT = 1883;
    private static final String CONFIG = """
            listener 1883
            allow_anonymous true
            """;

    private GenericContainer<?> broker;

    @Override
    public Map<String, String> start() {
        broker = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.22"))
                .withCopyToContainer(Transferable.of(CONFIG), "/mosquitto/config/mosquitto.conf")
                .withExposedPorts(MQTT_PORT)
                .waitingFor(Wait.forLogMessage(".*mosquitto version .* running.*", 1));
        broker.start();
        return Map.of(
                "geopulse.mqtt.enabled", "true",
                "geopulse.mqtt.broker.host", broker.getHost(),
                "geopulse.mqtt.broker.port", String.valueOf(broker.getMappedPort(MQTT_PORT))
        );
    }

    @Override
    public void stop() {
        if (broker != null) {
            broker.stop();
        }
    }
}
