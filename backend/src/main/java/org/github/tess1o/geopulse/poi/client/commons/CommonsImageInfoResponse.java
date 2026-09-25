package org.github.tess1o.geopulse.poi.client.commons;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Wikimedia Commons {@code action=query&prop=imageinfo} response, narrowed to what we need:
 * the file URL, its page, and the licence metadata.
 *
 * <p>Extmetadata values arrive as {@code {"value": ..., "source": ..., "hidden": ...}} maps.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommonsImageInfoResponse {

    private Query query;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Query {
        private Map<String, Page> pages;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Page {
        private String title;
        private List<ImageInfo> imageinfo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageInfo {
        private String url;
        private String thumburl;
        private String descriptionurl;
        private Map<String, MetaValue> extmetadata;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetaValue {
        private String value;
    }
}
