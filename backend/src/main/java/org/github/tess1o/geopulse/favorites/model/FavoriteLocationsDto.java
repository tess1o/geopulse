package org.github.tess1o.geopulse.favorites.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class FavoriteLocationsDto {
    private List<FavoritePointDto> points;
    private List<FavoriteAreaDto> areas;
}
