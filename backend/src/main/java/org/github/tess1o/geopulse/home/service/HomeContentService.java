package org.github.tess1o.geopulse.home.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.home.model.HomeContentResponse;
import org.github.tess1o.geopulse.user.model.SupportedLanguages;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * Tips, keyed by {@link SupportedLanguages} code. Always has an {@code en} entry; other locales are
     * only present when their bundled file exists, and {@link #getContent(String)} falls back to
     * {@code en} for anything else -- so a language can ship without translated tips yet without
     * breaking the endpoint.
     */
    private volatile Map<String, List<HomeContentResponse.Tip>> tipsByLocale = Map.of();
    /**
     * "What's New" release notes are not translated: they are a running changelog tied to actual
     * releases, so maintaining bilingual history indefinitely is a separate scope decision from the
     * evergreen tips above. Same content is returned regardless of the requested locale.
     */
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
        tipsByLocale = loadBundledTipsByLocale();
        whatsNew = loadBundledWhatsNew();
        updatedAt = Instant.now(clock);
    }

    /**
     * @deprecated kept for callers that do not need locale-specific tips; returns the {@code en} content.
     */
    @Deprecated
    public HomeContentResponse getContent() {
        return getContent(SupportedLanguages.DEFAULT.getCode());
    }

    public HomeContentResponse getContent(String locale) {
        String normalizedLocale = SupportedLanguages.normalizeOrDefault(locale);
        List<HomeContentResponse.Tip> localizedTips = tipsByLocale.getOrDefault(
                normalizedLocale,
                tipsByLocale.getOrDefault(SupportedLanguages.DEFAULT.getCode(), List.of())
        );

        return new HomeContentResponse(
                localizedTips,
                whatsNew,
                new HomeContentResponse.Meta("bundled", updatedAt.toString())
        );
    }

    private Map<String, List<HomeContentResponse.Tip>> loadBundledTipsByLocale() {
        Map<String, List<HomeContentResponse.Tip>> result = new HashMap<>();

        List<HomeContentResponse.Tip> englishTips = parseTips(readResourceRoot(tipsResourcePath, true).path("tips"));
        result.put(SupportedLanguages.DEFAULT.getCode(), englishTips);

        for (SupportedLanguages language : SupportedLanguages.values()) {
            if (language == SupportedLanguages.DEFAULT) {
                continue;
            }

            String localizedPath = localizedResourcePath(tipsResourcePath, language.getCode());
            JsonNode root = readResourceRoot(localizedPath, false);
            List<HomeContentResponse.Tip> localizedTips = parseTips(root.path("tips"));
            // An empty/missing translation file is expected for a language that has not been translated
            // yet -- fall back to English rather than serving an empty tips list for that locale.
            result.put(language.getCode(), localizedTips.isEmpty() ? englishTips : localizedTips);
        }

        return Map.copyOf(result);
    }

    /**
     * {@code /home-content.json} -> {@code /home-content_uk.json}, matching the frontend locale catalog
     * naming (a `_<code>` suffix before the extension).
     */
    private String localizedResourcePath(String basePath, String languageCode) {
        int dotIndex = basePath.lastIndexOf('.');
        return dotIndex < 0
                ? basePath + "_" + languageCode
                : basePath.substring(0, dotIndex) + "_" + languageCode + basePath.substring(dotIndex);
    }

    private List<HomeContentResponse.WhatsNewItem> loadBundledWhatsNew() {
        JsonNode root = readResourceRoot(whatsNewResourcePath, true);
        if (root.isArray()) {
            return parseWhatsNew(root);
        }
        return parseWhatsNew(root.path("whatsNew"));
    }

    /**
     * @param required whether a missing file is logged as an error (a core bundled file) or quietly
     *                  treated as "not translated yet" (an optional per-locale file).
     */
    private JsonNode readResourceRoot(String resourcePath, boolean required) {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                if (required) {
                    log.error("Bundled home content file '{}' is missing", resourcePath);
                } else {
                    log.debug("Optional bundled home content file '{}' is not present", resourcePath);
                }
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
