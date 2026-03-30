package org.github.tess1o.geopulse.home.model;

import java.util.List;

public record HomeContentResponse(
        List<Tip> tips,
        List<WhatsNewItem> whatsNew,
        Meta meta
) {
    public HomeContentResponse {
        tips = tips == null ? List.of() : List.copyOf(tips);
        whatsNew = whatsNew == null ? List.of() : List.copyOf(whatsNew);
    }

    public record Tip(
            String id,
            String title,
            String description,
            String icon,
            List<TipLink> links,
            List<String> audiences
    ) {
        public Tip {
            links = links == null ? List.of() : List.copyOf(links);
            audiences = audiences == null ? List.of() : List.copyOf(audiences);
        }
    }

    public record TipLink(
            String label,
            String url
    ) {
    }

    public record WhatsNewItem(
            String version,
            String title,
            List<String> highlights,
            String releaseUrl
    ) {
        public WhatsNewItem {
            highlights = highlights == null ? List.of() : List.copyOf(highlights);
        }
    }

    public record Meta(
            String source,
            String updatedAt
    ) {
    }
}
