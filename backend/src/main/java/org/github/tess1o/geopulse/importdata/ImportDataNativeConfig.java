package org.github.tess1o.geopulse.importdata;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.importdata.model.*;

@RegisterForReflection(targets = {
        // Import job models
        ImportStatus.class,
        ImportJob.class,
        ImportJobResponse.class,
        ImportOptions.class,
        DebugImportRequest.class,
        // Chunked upload models
        ChunkedUploadSession.class,
        ChunkedUploadInitRequest.class,
        UploadStatus.class,
        // Format enum
        ImportFormat.class,
})
public class ImportDataNativeConfig {
}
