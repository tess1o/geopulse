package org.github.tess1o.geopulse.poi.service;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.poi.client.PoiRestClientFactory;
import org.github.tess1o.geopulse.poi.client.commons.CommonsImageInfoResponse;
import org.github.tess1o.geopulse.poi.model.entity.PoiImageCacheEntity;
import org.github.tess1o.geopulse.poi.repository.PoiImageCacheRepository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves and serves Wikimedia Commons images.
 *
 * <p>Two rules drive the design:
 * <ul>
 *   <li><b>Never serve an unattributed image.</b> Commons licences are per-file, so a file
 *       whose licence cannot be established is treated as having no image.</li>
 *   <li><b>The remote host is never client-controlled.</b> Only an allow-listed Wikimedia
 *       host is fetched, and only for a file name that already exists in our own POI cache,
 *       so this endpoint cannot be turned into an SSRF primitive.</li>
 * </ul>
 */
@ApplicationScoped
@Slf4j
public class PoiImageService {

    /** Verified by inspection: thumbnails are served from thumb.wikimedia.org, not upload. */
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "upload.wikimedia.org", "thumb.wikimedia.org", "commons.wikimedia.org");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int BATCH_SIZE = 50;

    private final PoiConfigurationService config;
    private final PoiImageCacheRepository imageCacheRepository;

    @Inject
    PoiRestClientFactory clientFactory;

    @Inject
    public PoiImageService(PoiConfigurationService config, PoiImageCacheRepository imageCacheRepository) {
        this.config = config;
        this.imageCacheRepository = imageCacheRepository;
    }

    /** Licence and URL information for one Commons file. */
    @Builder
    public record CommonsImage(String fileName, String thumbUrl, String filePageUrl,
                               String author, String licenseName, String licenseUrl) {
        public boolean isUsable() {
            return thumbUrl != null && licenseName != null && !licenseName.isBlank();
        }
    }

    /**
     * Resolves licence metadata for a batch of files in as few round trips as possible.
     * Files that cannot be resolved are simply absent from the result.
     */
    public Map<String, CommonsImage> resolveFiles(List<String> fileNames) {
        Map<String, CommonsImage> resolved = new HashMap<>();
        if (fileNames == null || fileNames.isEmpty()) {
            return resolved;
        }

        List<String> distinct = fileNames.stream().filter(Objects::nonNull).distinct().toList();
        for (int start = 0; start < distinct.size(); start += BATCH_SIZE) {
            List<String> batch = distinct.subList(start, Math.min(start + BATCH_SIZE, distinct.size()));
            try {
                CommonsImageInfoResponse response = fetchImageInfo(batch);
                mapImageInfo(response).forEach(image -> resolved.put(image.fileName(), image));
            } catch (Exception e) {
                log.warn("Commons imageinfo lookup failed for {} file(s)", batch.size(), e);
            }
        }
        return resolved;
    }

    private CommonsImageInfoResponse fetchImageInfo(List<String> fileNames) {
        String titles = fileNames.stream()
                .map(name -> "File:" + name)
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
        return clientFactory.commons()
                .imageInfo("query", "json", "imageinfo", titles,
                        "url|extmetadata", config.getThumbWidth(), config.getUserAgent(), "application/json")
                .await().atMost(Duration.ofSeconds(30));
    }

    private static List<CommonsImage> mapImageInfo(CommonsImageInfoResponse response) {
        if (response == null || response.getQuery() == null || response.getQuery().getPages() == null) {
            return List.of();
        }
        return response.getQuery().getPages().values().stream()
                .filter(page -> page.getImageinfo() != null && !page.getImageinfo().isEmpty())
                .map(page -> {
                    CommonsImageInfoResponse.ImageInfo info = page.getImageinfo().getFirst();
                    Map<String, CommonsImageInfoResponse.MetaValue> meta =
                            info.getExtmetadata() == null ? Map.of() : info.getExtmetadata();
                    String fileName = stripFilePrefix(page.getTitle());
                    return CommonsImage.builder()
                            .fileName(fileName)
                            .thumbUrl(info.getThumburl() != null ? info.getThumburl() : info.getUrl())
                            .filePageUrl(info.getDescriptionurl())
                            .author(plainText(metaValue(meta, "Artist")))
                            .licenseName(plainText(metaValue(meta, "LicenseShortName")))
                            .licenseUrl(metaValue(meta, "LicenseUrl"))
                            .build();
                })
                .filter(CommonsImage::isUsable)
                .toList();
    }

    private static String stripFilePrefix(String title) {
        if (title == null) {
            return null;
        }
        return title.startsWith("File:") ? title.substring("File:".length()) : title;
    }

    private static String metaValue(Map<String, CommonsImageInfoResponse.MetaValue> meta, String key) {
        CommonsImageInfoResponse.MetaValue value = meta.get(key);
        return value == null ? null : value.getValue();
    }

    /** Commons metadata fields contain HTML; reduce them to the text a human would read. */
    static String plainText(String html) {
        if (html == null) {
            return null;
        }
        String text = html.replaceAll("<[^>]*>", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Returns the bytes for a cached POI's image, fetching and caching on first use.
     * Empty when the POI has no image or its licence cannot be established.
     *
     * <p>Deliberately not {@code @Transactional}: the cache miss path performs two outbound
     * HTTP calls, and holding a database connection for that long would exhaust the pool
     * under any concurrency. The write is wrapped in its own short transaction instead.
     */
    public Optional<CachedImage> getThumbnail(String imageFile) {
        if (imageFile == null || imageFile.isBlank()) {
            return Optional.empty();
        }

        String imageKey = imageFile + "@" + config.getThumbWidth();
        Instant now = Instant.now();

        Optional<PoiImageCacheEntity> cached = imageCacheRepository.findByImageKey(imageKey)
                .filter(entity -> entity.getExpiresAt().isAfter(now));
        if (cached.isPresent()) {
            PoiImageCacheEntity entity = cached.get();
            return Optional.of(new CachedImage(entity.getContentType(), entity.getImageBytes(),
                    entity.getLicenseName(), entity.getLicenseUrl(), entity.getAuthor(),
                    entity.getFilePageUrl()));
        }

        return fetchAndStore(imageFile, imageKey, now);
    }

    private Optional<CachedImage> fetchAndStore(String imageFile, String imageKey, Instant now) {
        Map<String, CommonsImage> resolved = resolveFiles(List.of(imageFile));
        CommonsImage image = resolved.get(imageFile);

        if (image == null || !image.isUsable()) {
            // The safe failure direction: no licence, no image.
            log.info("No usable licence for Commons file '{}'; serving no image", imageFile);
            return Optional.empty();
        }

        try {
            byte[] bytes = download(image.thumbUrl());
            if (bytes == null) {
                return Optional.empty();
            }

            PoiImageCacheEntity entity = PoiImageCacheEntity.builder()
                    .imageKey(imageKey)
                    .fileName(imageFile)
                    .remoteUrl(image.thumbUrl())
                    .filePageUrl(image.filePageUrl())
                    .contentType(detectContentType(image.thumbUrl()))
                    .contentLength((long) bytes.length)
                    .imageBytes(bytes)
                    .author(image.author())
                    .licenseName(image.licenseName())
                    .licenseUrl(image.licenseUrl())
                    .fetchedAt(now)
                    .expiresAt(now.plus(config.getImageCacheTtlDays(), ChronoUnit.DAYS))
                    .build();
            // Own short transaction, opened only once the bytes are in hand.
            QuarkusTransaction.requiringNew().run(() -> imageCacheRepository.persist(entity));

            return Optional.of(new CachedImage(entity.getContentType(), bytes, image.licenseName(),
                    image.licenseUrl(), image.author(), image.filePageUrl()));
        } catch (Exception e) {
            log.warn("Failed to fetch Commons image '{}'", imageFile, e);
            return Optional.empty();
        }
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            log.warn("Rejected malformed image URL");
            return null;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || !ALLOWED_HOSTS.contains(hostOf(uri))) {
            log.warn("Rejected image URL outside the Wikimedia allow-list: {}", uri.getHost());
            return null;
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", config.getUserAgent())
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Commons image fetch returned HTTP {}", response.statusCode());
            return null;
        }

        String contentType = response.headers().firstValue("content-type")
                .map(value -> value.split(";")[0].trim().toLowerCase(Locale.ROOT))
                .orElse("");
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            log.warn("Rejected image with unexpected content type '{}'", contentType);
            return null;
        }

        byte[] body = response.body();
        if (body == null || body.length == 0 || body.length > MAX_BYTES) {
            log.warn("Rejected image of {} bytes (limit {})", body == null ? 0 : body.length, MAX_BYTES);
            return null;
        }
        return body;
    }

    private static String hostOf(URI uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase(Locale.ROOT);
    }

    private static String detectContentType(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public record CachedImage(String contentType, byte[] bytes, String licenseName,
                              String licenseUrl, String author, String filePageUrl) {
    }
}
