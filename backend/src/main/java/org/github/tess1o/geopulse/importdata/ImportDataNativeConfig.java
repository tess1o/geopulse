package org.github.tess1o.geopulse.importdata;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportJobResponse;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.model.ImportStatus;

@RegisterForReflection(targets = {
        ImportStatus.class,
        ImportJob.class,
        ImportJobResponse.class,
        ImportOptions.class,
})
public class ImportDataNativeConfig {
}
