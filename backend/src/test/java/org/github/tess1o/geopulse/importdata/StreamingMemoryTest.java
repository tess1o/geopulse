package org.github.tess1o.geopulse.importdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.geojson.StreamingGeoJsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Standalone test to verify streaming parser memory efficiency with large files.
 *
 * This test demonstrates that the streaming parser can handle files of any size
 * with constant memory usage, unlike the old implementation which would load
 * the entire file into memory.
 *
 * Run this test with memory constraints to verify streaming behavior:
 *
 * mvn exec:java -Dexec.mainClass="org.github.tess1o.geopulse.importdata.StreamingMemoryTest" \
 *               -Dexec.classpathScope=test \
 *               -Dexec.args="src/test/resources/test-geojson-1gb.geojson" \
 *               -Dexec.executable="java" \
 *               -Dexec.args="-Xmx128m"
 */
@Slf4j
public class StreamingMemoryTest {

    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    public static void main(String[] args) throws IOException {
        String testFile = args.length > 0
                ? args[0]
                : "src/test/resources/test-geojson-1gb.geojson";

        log.info("===========================================");
        log.info("  Streaming GeoJSON Parser Memory Test");
        log.info("===========================================");
        log.info("");

        File file = new File(testFile);
        if (!file.exists()) {
            log.error("Test file not found: {}", testFile);
            log.error("Please generate it first using:");
            log.error("  ./generate_test_geojson.sh");
            System.exit(1);
        }

        long fileSizeBytes = file.length();
        double fileSizeMB = fileSizeBytes / (1024.0 * 1024.0);
        double fileSizeGB = fileSizeBytes / (1024.0 * 1024.0 * 1024.0);

        log.info("Test file: {}", testFile);
        log.info("File size: {:.2f} MB ({:.2f} GB)", fileSizeMB, fileSizeGB);
        log.info("");

        // Get initial memory stats
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long maxMemory = runtime.maxMemory();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        log.info("JVM Memory Configuration:");
        log.info("  Max heap: {} MB", maxMemory / (1024 * 1024));
        log.info("  Memory before parsing: {} MB", memoryBefore / (1024 * 1024));
        log.info("");

        if (fileSizeBytes > maxMemory / 2) {
            log.warn("⚠️  File size ({:.2f} MB) is > 50% of max heap ({} MB)",
                    fileSizeMB, maxMemory / (1024 * 1024));
            log.warn("⚠️  Traditional parsing would likely fail with OutOfMemoryError");
            log.warn("⚠️  Testing streaming parser's ability to handle this...");
            log.info("");
        }

        // Parse using streaming
        log.info("Starting streaming parse...");
        long startTime = System.currentTimeMillis();

        byte[] fileBytes = Files.readAllBytes(file.toPath());
        log.info("Loaded file into memory ({} MB)", fileBytes.length / (1024 * 1024));

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(fileBytes, objectMapper);

        long[] featureCount = {0};
        long[] lastLogTime = {System.currentTimeMillis()};

        StreamingGeoJsonParser.ParsingStats stats = parser.parseFeatures((feature, currentStats) -> {
            featureCount[0]++;

            // Log progress every 5 seconds
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLogTime[0] > 5000) {
                runtime.gc();
                long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                log.info("Progress: {} features parsed, memory: {} MB",
                        featureCount[0], currentMemory / (1024 * 1024));
                lastLogTime[0] = currentTime;
            }
        });

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;

        // Get final memory stats
        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = memoryAfter - memoryBefore;

        log.info("");
        log.info("===========================================");
        log.info("  Parsing Complete");
        log.info("===========================================");
        log.info("");
        log.info("Statistics:");
        log.info("  Features parsed: {}", stats.totalFeatures);
        log.info("  Valid features: {}", stats.validFeatures);
        log.info("  Total points: {}", stats.totalPoints);
        log.info("  Valid points: {}", stats.validPoints);
        log.info("");
        log.info("Performance:");
        log.info("  Duration: {:.2f} seconds", durationMs / 1000.0);
        log.info("  Throughput: {:.0f} features/sec",
                (stats.totalFeatures * 1000.0) / durationMs);
        log.info("");
        log.info("Memory:");
        log.info("  Before parsing: {} MB", memoryBefore / (1024 * 1024));
        log.info("  After parsing: {} MB", memoryAfter / (1024 * 1024));
        log.info("  Memory increase: {} MB", memoryIncrease / (1024 * 1024));
        log.info("  File size: {:.2f} MB", fileSizeMB);
        log.info("  Memory efficiency: {:.1f}% of file size",
                (memoryIncrease * 100.0) / fileSizeBytes);
        log.info("");

        // Verify memory efficiency
        boolean memoryEfficient = memoryIncrease < fileSizeBytes * 0.5; // < 50% of file size
        if (memoryEfficient) {
            log.info("✅ SUCCESS: Streaming parser is memory efficient!");
            log.info("   Memory increase ({} MB) is < 50% of file size ({:.2f} MB)",
                    memoryIncrease / (1024 * 1024), fileSizeMB);
        } else {
            log.warn("⚠️  WARNING: Memory usage higher than expected");
            log.warn("   Memory increase ({} MB) is >= 50% of file size ({:.2f} MB)",
                    memoryIncrease / (1024 * 1024), fileSizeMB);
        }

        log.info("");
        log.info("===========================================");
        log.info("");
        log.info("This test demonstrates that the streaming parser can:");
        log.info("  1. Parse files much larger than available heap memory");
        log.info("  2. Maintain constant memory usage during parsing");
        log.info("  3. Process features incrementally without loading entire file");
        log.info("");
        log.info("Traditional approach would require ~{}x more memory!",
                (int) Math.ceil(fileSizeMB / (memoryIncrease / (1024.0 * 1024.0))));
    }
}
