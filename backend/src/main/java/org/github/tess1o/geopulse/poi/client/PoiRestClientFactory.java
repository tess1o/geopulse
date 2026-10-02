package org.github.tess1o.geopulse.poi.client;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.poi.client.commons.CommonsApiClient;
import org.github.tess1o.geopulse.poi.client.wikidata.WikidataSparqlClient;
import org.github.tess1o.geopulse.poi.service.PoiConfigurationService;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Builds the Wikidata and Commons clients from the current settings.
 *
 * <p>Deliberately not {@code @RegisterRestClient}: that resolves the base URL from
 * MicroProfile Config once at startup, so an admin changing the endpoint in the panel
 * would have had no effect. Building per call means the configured URL actually applies.
 *
 * <p>Clients are cached per URL and closed on shutdown, the same discipline the weather
 * client uses.
 */
@ApplicationScoped
@Slf4j
public class PoiRestClientFactory {

    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int WIKIDATA_READ_TIMEOUT_SECONDS = 60;   // SPARQL is slow on a cold cache
    private static final int COMMONS_READ_TIMEOUT_SECONDS = 30;

    private final PoiConfigurationService config;
    private final Map<String, WikidataSparqlClient> wikidataClients = new ConcurrentHashMap<>();
    private final Map<String, CommonsApiClient> commonsClients = new ConcurrentHashMap<>();

    @Inject
    public PoiRestClientFactory(PoiConfigurationService config) {
        this.config = config;
    }

    public WikidataSparqlClient wikidata() {
        String endpoint = config.getWikidataEndpoint();
        return wikidataClients.computeIfAbsent(endpoint, url -> RestClientBuilder.newBuilder()
                .baseUri(URI.create(url))
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(WIKIDATA_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .property("microprofile.rest.client.disable.default.mapper", true)
                .build(WikidataSparqlClient.class));
    }

    public CommonsApiClient commons() {
        String endpoint = config.getCommonsEndpoint();
        return commonsClients.computeIfAbsent(endpoint, url -> RestClientBuilder.newBuilder()
                .baseUri(URI.create(url))
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(COMMONS_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .property("microprofile.rest.client.disable.default.mapper", true)
                .build(CommonsApiClient.class));
    }

    @PreDestroy
    void closeClients() {
        wikidataClients.values().forEach(this::closeQuietly);
        commonsClients.values().forEach(this::closeQuietly);
        wikidataClients.clear();
        commonsClients.clear();
    }

    private void closeQuietly(Object client) {
        if (client instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Failed to close POI REST client: {}", e.getMessage());
            }
        }
    }
}
