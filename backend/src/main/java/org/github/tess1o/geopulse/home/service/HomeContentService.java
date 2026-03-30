package org.github.tess1o.geopulse.home.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.home.model.HomeContentResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
@Slf4j
public class HomeContentService {

    private static final String TIPS_RESOURCE_PATH = "/home-content.json";
    private static final String WHATS_NEW_RESOURCE_PATH = "/whats_new.json";
    private static final String DEFAULT_RELEASES_URL = "https://github.com/tess1o/geopulse/releases";
    private static final Set<String> ALLOWED_AUDIENCES = Set.of("admin", "non_admin", "all");

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String tipsResourcePath;
    private final String whatsNewResourcePath;

    private volatile List<HomeContentResponse.Tip> tips = List.of();
    private volatile List<HomeContentResponse.WhatsNewItem> whatsNew = List.of();
    private volatile Instant updatedAt = Instant.EPOCH;

    @jakarta.inject.Inject
    public HomeContentService(
            ObjectMapper objectMapper
    ) {
        this(objectMapper, Clock.systemUTC(), TIPS_RESOURCE_PATH, WHATS_NEW_RESOURCE_PATH);
    }

    HomeContentService(
            ObjectMapper objectMapper,
            Clock clock,
            String tipsResourcePath,
            String whatsNewResourcePath
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tipsResourcePath = tipsResourcePath;
        this.whatsNewResourcePath = whatsNewResourcePath;
    }

    @PostConstruct
    void init() {
        tips = loadBundledTips();
        whatsNew = loadBundledWhatsNew();
        updatedAt = Instant.now(clock);
    }

    public HomeContentResponse getContent() {
        return new HomeContentResponse(
                tips,
                whatsNew,
                new HomeContentResponse.Meta("bundled", updatedAt.toString())
        );
    }

    private List<HomeContentResponse.Tip> loadBundledTips() {
        JsonNode root = readResourceRoot(tipsResourcePath);
        return parseTips(root.path("tips"));
    }

    private List<HomeContentResponse.WhatsNewItem> loadBundledWhatsNew() {
        JsonNode root = readResourceRoot(whatsNewResourcePath);
        if (root.isArray()) {
            return parseWhatsNew(root);
        }
        return parseWhatsNew(root.path("whatsNew"));
    }

    private JsonNode readResourceRoot(String resourcePath) {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                log.error("Bundled home content file '{}' is missing", resourcePath);
                return objectMapper.createObjectNode();
            }

            String rawContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readTree(rawContent);
        } catch (IOException exception) {
            log.error("Failed to load bundled home content from '{}'", resourcePath, exception);
            return objectMapper.createObjectNode();
        }
    }

    private List<HomeContentResponse.Tip> parseTips(JsonNode tipsNode) {
        if (!tipsNode.isArray()) {
            return List.of();
        }

        List<HomeContentResponse.Tip> parsedTips = new ArrayList<>();
        for (JsonNode tipNode : tipsNode) {
            if (!tipNode.isObject()) {
                continue;
            }

            String id = textValue(tipNode, "id");
            String title = textValue(tipNode, "title");
            String description = textValue(tipNode, "description");
            if (id == null || title == null || description == null) {
                continue;
            }

            String icon = textValue(tipNode, "icon");
            if (icon == null) {
                icon = "pi pi-lightbulb";
            }

            List<HomeContentResponse.TipLink> links = parseTipLinks(tipNode.path("links"));
            List<String> audiences = parseAudiences(tipNode.path("audiences"));
            if (audiences.isEmpty()) {
                audiences = List.of("all");
            }

            parsedTips.add(new HomeContentResponse.Tip(id, title, description, icon, links, audiences));
        }

        return List.copyOf(parsedTips);
    }

    private List<HomeContentResponse.TipLink> parseTipLinks(JsonNode linksNode) {
        if (!linksNode.isArray()) {
            return List.of();
        }

        List<HomeContentResponse.TipLink> links = new ArrayList<>();
        for (JsonNode linkNode : linksNode) {
            if (!linkNode.isObject()) {
                continue;
            }

            String label = textValue(linkNode, "label");
            String url = textValue(linkNode, "url");
            if (label == null || url == null) {
                continue;
            }

            links.add(new HomeContentResponse.TipLink(label, url));
        }

        return List.copyOf(links);
    }

    private List<String> parseAudiences(JsonNode audiencesNode) {
        if (!audiencesNode.isArray()) {
            return List.of();
        }

        List<String> audiences = new ArrayList<>();
        for (JsonNode audienceNode : audiencesNode) {
            if (!audienceNode.isTextual()) {
                continue;
            }

            String normalized = audienceNode.asText().trim().toLowerCase(Locale.ROOT);
            if (ALLOWED_AUDIENCES.contains(normalized) && !audiences.contains(normalized)) {
                audiences.add(normalized);
            }
        }

        return List.copyOf(audiences);
    }

    private List<HomeContentResponse.WhatsNewItem> parseWhatsNew(JsonNode whatsNewNode) {
        if (!whatsNewNode.isArray()) {
            return List.of();
        }

        List<HomeContentResponse.WhatsNewItem> parsedItems = new ArrayList<>();
        for (JsonNode itemNode : whatsNewNode) {
            if (!itemNode.isObject()) {
                continue;
            }

            String version = textValue(itemNode, "version");
            if (version == null) {
                continue;
            }

            String title = textValue(itemNode, "title");
            if (title == null) {
                title = "GeoPulse " + version;
            }

            List<String> highlights = parseHighlights(itemNode.path("highlights"));

            String releaseUrl = textValue(itemNode, "releaseUrl");
            if (releaseUrl == null) {
                releaseUrl = DEFAULT_RELEASES_URL;
            }

            parsedItems.add(new HomeContentResponse.WhatsNewItem(version, title, highlights, releaseUrl));
        }

        return List.copyOf(parsedItems);
    }

    private List<String> parseHighlights(JsonNode highlightsNode) {
        if (!highlightsNode.isArray()) {
            return List.of();
        }

        List<String> highlights = new ArrayList<>();
        for (JsonNode highlightNode : highlightsNode) {
            if (!highlightNode.isTextual()) {
                continue;
            }

            String value = highlightNode.asText().trim();
            if (!value.isEmpty()) {
                highlights.add(value);
            }
        }

        return List.copyOf(highlights);
    }

    private String textValue(JsonNode node, String field) {
        JsonNode valueNode = node.path(field);
        if (!valueNode.isTextual()) {
            return null;
        }

        String value = valueNode.asText().trim();
        return value.isEmpty() ? null : value;
    }
}
