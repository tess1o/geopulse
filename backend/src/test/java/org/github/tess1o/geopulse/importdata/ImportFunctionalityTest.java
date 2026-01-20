package org.github.tess1o.geopulse.importdata;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ImportFunctionalityTest {

    @Inject
    ImportJobService importJobService;

    @Inject
    ImportDataService importDataService;

    @Test
    public void testImportJobServiceExists() {
        assertNotNull(importJobService);
        assertNotNull(importDataService);
    }

    @Test
    public void testCreateImportJobBasicValidation() {
        UUID userId = UUID.randomUUID();
        ImportOptions options = new ImportOptions();
        options.setDataTypes(List.of("rawGps", "timeline"));
        
        String fileName = "test-import.zip";
        byte[] zipData = new byte[100]; // Mock zip data
        
        // This should not throw an exception
        assertDoesNotThrow(() -> {
            var job = importJobService.createImportJob(userId, options, fileName, zipData);
            assertNotNull(job);
            assertEquals(userId, job.getUserId());
            assertEquals(fileName, job.getUploadedFileName());
        });
    }
}