package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO wrapping paginated place visits with metadata.
 * Used for server-side pagination of visit history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedPlaceVisitsDTO {
    private List<PlaceVisitDTO> visits;
    private int currentPage;
    private int pageSize;
    private long totalCount;
    private int totalPages;
}
