package org.github.tess1o.geopulse.poi.dto;

/**
 * A place worth visiting, with everything needed to render a card.
 *
 * <p>The image fields describe a Commons file served through our own proxy. They are
 * deliberately per-POI because Commons licences are per-file: {@code imageLicense} being
 * null means the licence could not be established, and the UI must then show no image
 * rather than an unattributed one.
 */
public record PoiDto(
        Long id,
        String externalId,
        String name,
        String description,
        Double latitude,
        Double longitude,
        /** Same-origin URL of the proxied thumbnail, or null when there is no usable image. */
        String imageUrl,
        String imageAuthor,
        String imageLicense,
        String imageLicenseUrl,
        String imageFilePageUrl
) {
    /** Attribution required wherever POI data is displayed. */
    public static final String DATA_ATTRIBUTION = "Place data from Wikidata (CC0)";
}
