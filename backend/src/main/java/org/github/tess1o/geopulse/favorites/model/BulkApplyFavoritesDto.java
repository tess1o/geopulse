package org.github.tess1o.geopulse.favorites.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.List;

@Data
public class BulkApplyFavoritesDto {
    @NotNull
    private List<@NotNull @Valid AddPointToFavoritesDto> points = List.of();
    @NotNull
    private List<@NotNull @Valid AddAreaToFavoritesDto> areas = List.of();
    @NotNull
    private List<@NotNull @Valid Update> updates = List.of();
    @NotNull
    private List<@NotNull @Positive Long> deleteIds = List.of();

    @Data
    public static class Update {
        @NotNull @Positive
        private Long favoriteId;
        @NotNull @Valid
        private EditFavoriteDto changes;
    }
}
