package org.github.tess1o.geopulse.timeline.events;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.locationtech.jts.geom.Geometry;

import java.util.UUID;

@Data
@Builder
public class FavoriteAddedEvent {
    private Long favoriteId;
    private UUID userId;
    private String favoriteName;
    private FavoriteLocationType favoriteType;
    private Geometry geometry;
}