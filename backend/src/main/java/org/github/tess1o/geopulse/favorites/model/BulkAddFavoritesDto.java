package org.github.tess1o.geopulse.favorites.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkAddFavoritesDto {

    @NotNull(message = "Points list cannot be null")
    @Valid
    private List<AddPointToFavoritesDto> points;

    @NotNull(message = "Areas list cannot be null")
    @Valid
    private List<AddAreaToFavoritesDto> areas;
}
