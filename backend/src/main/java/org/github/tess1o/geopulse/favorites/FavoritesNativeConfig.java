package org.github.tess1o.geopulse.favorites;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.favorites.model.*;

@RegisterForReflection(targets = {
        FavoriteLocationType.class,
        FavoriteLocationsDto.class,
        FavoritePointDto.class,
        FavoriteAreaDto.class,
        AddAreaToFavoritesDto.class,
        AddPointToFavoritesDto.class,
        EditFavoriteDto.class,
        FavoriteLocationsDto.class,
        FavoritesEntity.class,
        FavoriteReconcileRequest.class,
})
public class FavoritesNativeConfig {
}
