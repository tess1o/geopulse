package org.github.tess1o.geopulse.importdata.model;

import java.util.List;

public record ImportJobsResponse(List<ImportJobResponse> items, int limit, int offset, boolean hasNext) {
}
