package org.github.tess1o.geopulse.streaming.events;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.locationtech.jts.geom.Geometry;

import java.util.UUID;

@Data
@Builder
public class FavoriteRenamedEvent {
    private Long favoriteId;
    private UUID userId;
    private String oldName;
    private String newName;
    private FavoriteLocationType favoriteType;
    private Geometry geometry;
}