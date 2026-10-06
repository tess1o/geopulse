package org.github.tess1o.geopulse.mapmatching.client;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingConfiguration;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/** Builds a Valhalla client from the admin-configured base URL and timeouts. */
public final class ValhallaRestClients {

    private ValhallaRestClients() {
    }

    public static ValhallaRestClient create(MapMatchingConfiguration configuration) {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(configuration.valhallaBaseUrl()))
                .connectTimeout(Math.max(1, configuration.getConnectTimeoutSeconds()), TimeUnit.SECONDS)
                .readTimeout(Math.max(1, configuration.getReadTimeoutSeconds()), TimeUnit.SECONDS)
                .build(ValhallaRestClient.class);
    }
}
