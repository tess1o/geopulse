package org.github.tess1o.geopulse.importdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A debug import job and, once it has completed, what it found.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DebugImportJobResponse(ImportJobResponse job, DebugImportReport report) {

    public static DebugImportJobResponse from(ImportJob job) {
        return new DebugImportJobResponse(ImportJobResponse.from(job), job.getDebugImportReport());
    }
}
