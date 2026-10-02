package org.github.tess1o.geopulse.shared.api;

import java.util.List;

public record SliceResponse<T>(
        List<T> items,
        int page,
        int size,
        boolean hasNext
) {
}
