package org.github.tess1o.geopulse.importdata;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.importdata.model.*;

@RegisterForReflection(targets = {
        ImportStatus.class,
        ImportJob.class,
        ImportJobResponse.class,
        ImportOptions.class,
        DebugImportRequest.class,
})
public class ImportDataNativeConfig {
}
