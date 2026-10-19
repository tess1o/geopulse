package org.github.tess1o.geopulse.importdata;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.importdata.model.*;

@RegisterForReflection(targets = {
        // Import job models
        ImportStatus.class,
        ImportJob.class,
        ImportJobResponse.class,
        ImportJobsResponse.class,
        ImportPhase.class,
        ImportOptions.class,
        DebugImportRequest.class,
        DebugImportJobResponse.class,
        DebugImportReport.class,
        DebugImportReport.Issue.class,
        DebugImportReport.Range.class,
        DebugImportReport.Imported.class,
        DebugImportReport.Water.class,
        DebugImportReport.Warning.class,
        DebugTimelineComparison.class,
        DebugTimelineComparison.Event.class,
        DebugTimelineComparison.Row.class,
        // Chunked upload models
        ChunkedUploadSession.class,
        ChunkedUploadInitRequest.class,
        ChunkedUploadInitResponse.class,
        ChunkUploadResponse.class,
        ChunkedUploadStatusResponse.class,
        UploadStatus.class,
        // Format enum
        ImportFormat.class,
})
public class ImportDataNativeConfig {
}
