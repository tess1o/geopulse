package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;

import java.io.IOException;
import java.util.List;

/**
 * Runs debug imports on the import job queue, so a large export is processed in the background with progress.
 */
@ApplicationScoped
public class DebugImportStrategy implements ImportStrategy {

    public static final String FORMAT = "debug";

    @Inject
    DebugImportService debugImportService;

    @Override
    public String getFormat() {
        return FORMAT;
    }

    @Override
    public List<String> validateAndDetectDataTypes(ImportJob job) throws IOException {
        debugImportService.validate(job);
        return List.of(ExportImportConstants.DataTypes.RAW_GPS);
    }

    @Override
    public void processImportData(ImportJob job) throws IOException {
        DebugImportRequest request = job.getDebugImportRequest() != null
                ? job.getDebugImportRequest()
                : new DebugImportRequest();
        job.setDebugImportReport(debugImportService.importDebugData(job, request));
    }
}
