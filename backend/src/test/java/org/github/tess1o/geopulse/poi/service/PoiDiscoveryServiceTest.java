package org.github.tess1o.geopulse.poi.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PoiDiscoveryServiceTest {

    @Test
    void boxAroundCoversTheRequestedRadius() {
        // ~8 km around central Paris.
        PoiDiscoveryService.BoundingBox box =
                PoiDiscoveryService.boxAround(48.8566, 2.3522, 8_000);

        assertThat(box.south()).isLessThan(48.8566);
        assertThat(box.north()).isGreaterThan(48.8566);
        assertThat(box.west()).isLessThan(2.3522);
        assertThat(box.east()).isGreaterThan(2.3522);

        // Roughly 8km north/south.
        double latSpanMeters = (box.north() - box.south()) * 111_320d;
        assertThat(latSpanMeters).isBetween(15_000d, 17_000d);
    }

    @Test
    void boxAroundDoesNotExplodeInLongitudeNearThePoles() {
        PoiDiscoveryService.BoundingBox box =
                PoiDiscoveryService.boxAround(90d, 0d, 8_000);

        // A naive cos(lat) divisor collapses to zero at the pole; the span must stay bounded.
        assertThat(box.east() - box.west()).isLessThanOrEqualTo(360d);
        assertThat(box.north()).isLessThanOrEqualTo(90d);
        assertThat(box.south()).isGreaterThanOrEqualTo(-90d);
    }

    @Test
    void hashAreaIsStableAndIgnoresSubHundredMetreJitter() {
        PoiDiscoveryService.BoundingBox a =
                PoiDiscoveryService.boxAround(48.85660, 2.35220, 8_000);
        PoiDiscoveryService.BoundingBox b =
                PoiDiscoveryService.boxAround(48.85661, 2.35221, 8_000);

        // Rounding exists so repeat clicks on the same spot reuse one cached provider query.
        assertThat(PoiDiscoveryService.hashArea(a)).isEqualTo(PoiDiscoveryService.hashArea(b));

        PoiDiscoveryService.BoundingBox different =
                PoiDiscoveryService.boxAround(48.90, 2.40, 8_000);
        assertThat(PoiDiscoveryService.hashArea(a)).isNotEqualTo(PoiDiscoveryService.hashArea(different));
    }

    @Test
    void hashAreaIsANonReversibleDigest() {
        PoiDiscoveryService.BoundingBox box = PoiDiscoveryService.boxAround(48.8566, 2.3522, 8_000);

        String hash = PoiDiscoveryService.hashArea(box);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        // The stored value must not reveal the coordinates it was derived from.
        assertThat(hash).doesNotContain("48.8").doesNotContain("2.35");
    }

    @Test
    void imageUrlIsRelativeToTheApiBaseLikeOtherAuthenticatedBinaryEndpoints() {
        // Deliberately not absolute: the client attaches a bearer token and renders a blob
        // URL, so it must resolve against the API base the same way Immich photos do.
        assertThat(PoiDiscoveryService.imageUrl(42L))
                .isEqualTo("/poi/images/42/thumbnail");
    }

    @Test
    void plainTextReducesCommonsHtmlAttributionToReadableText() {
        // Real shape: Commons wraps the author in a link.
        String html = "<a rel=\"nofollow\" class=\"external text\" href=\"https://www.flickr.com/people/1@N00\">\n"
                + "Jane Doe</a>";

        assertThat(PoiImageService.plainText(html)).isEqualTo("Jane Doe");
    }

    @Test
    void plainTextHandlesNullAndEntities() {
        assertThat(PoiImageService.plainText(null)).isNull();
        assertThat(PoiImageService.plainText("   ")).isNull();
        assertThat(PoiImageService.plainText("A &amp; B")).isEqualTo("A & B");
    }
}
