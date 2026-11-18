package org.github.tess1o.geopulse.streaming.edits;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

/** New SQL writes must belong to generation, place reprojection, explicit restore or destructive cleanup. */
@Tag("unit")
class TimelineMaterializationBoundaryTest {
    private static final Pattern WRITE = Pattern.compile("(?i)(?:insert\\s+into|update|delete\\s+from)\\s+(?:timeline_(?:stays|trips|data_gaps)|Timeline(?:Stay|Trip|DataGap)Entity)\\b");
    @Test void correctionsAndResourcesNeverWriteTheMaterializedTimelineDirectly() throws Exception {
        Path root=Path.of("src/main/java/org/github/tess1o/geopulse");
        Set<String> allowed=Set.of(
                "streaming/service/StreamingPersistenceManager.java",
                "shared/exportimport/NativeSqlImportTemplates.java",
                "importdata/service/GeoPulseTimelineSnapshotImporter.java",
                "importdata/service/GeoPulsePlacesImporter.java",
                "importdata/service/ImportDataClearingService.java",
                "gps/service/GpsPointService.java",
                "admin/service/AdminUserService.java");
        var unexpected=new ArrayList<String>();
        try(var paths=Files.walk(root)) {
            for(var path:paths.filter(p->p.toString().endsWith(".java")).toList()) {
                var relative=root.relativize(path).toString().replace('\\','/');
                if(WRITE.matcher(Files.readString(path)).find()&&!allowed.contains(relative)) unexpected.add(relative);
            }
        }
        assertThat(unexpected).as("Timeline writers outside materialization/restore/cleanup").isEmpty();
        try(var paths=Files.walk(root.resolve("streaming/edits"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(path)).doesNotContain("TimelineStayEntity.builder(","TimelineTripEntity.builder(","TimelineDataGapEntity.builder(","GpsPointEntity.builder(");
            }
        }
    }
}
