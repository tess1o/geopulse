package org.github.tess1o.geopulse.importdata;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility to generate large GeoJSON test files by replicating an existing file
 * with timestamp modifications.
 *
 * Usage: Run the main method to generate a 1GB test file from a 100MB source file.
 */
@Slf4j
public class GeoJsonTestFileGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final JsonFactory jsonFactory = new JsonFactory();

    /**
     * Generate a large test file by replicating a source file multiple times
     * with timestamp offsets.
     *
     * @param sourceFilePath Path to source GeoJSON file
     * @param targetFilePath Path where the generated file should be saved
     * @param replicationCount Number of times to replicate the data (e.g., 10 for 10x size)
     * @param yearOffsetPerIteration Years to add to timestamps for each iteration
     * @throws IOException if file operations fail
     */
    public static void generateLargeGeoJsonFile(
            String sourceFilePath,
            String targetFilePath,
            int replicationCount,
            int yearOffsetPerIteration) throws IOException {

        log.info("Starting GeoJSON file generation:");
        log.info("  Source file: {}", sourceFilePath);
        log.info("  Target file: {}", targetFilePath);
        log.info("  Replication count: {}", replicationCount);
        log.info("  Year offset per iteration: {}", yearOffsetPerIteration);

        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists()) {
            throw new IOException("Source file not found: " + sourceFilePath);
        }

        long sourceFileSize = sourceFile.length();
        log.info("  Source file size: {} MB", sourceFileSize / (1024 * 1024));
        log.info("  Estimated target size: {} MB", (sourceFileSize * replicationCount) / (1024 * 1024));

        long startTime = System.currentTimeMillis();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFilePath), 1024 * 1024)) {
            // Write FeatureCollection header
            writer.write("{\n");
            writer.write("  \"type\" : \"FeatureCollection\",\n");
            writer.write("  \"features\" : [ ");

            boolean firstFeature = true;
            long totalFeaturesWritten = 0;

            // Replicate the source file multiple times
            for (int iteration = 0; iteration < replicationCount; iteration++) {
                log.info("Processing iteration {}/{}", iteration + 1, replicationCount);

                int yearOffset = iteration * yearOffsetPerIteration;
                List<JsonNode> features = parseFeatures(sourceFilePath);

                log.info("  Parsed {} features from source", features.size());

                // Write each feature with modified timestamp
                for (JsonNode feature : features) {
                    if (!firstFeature) {
                        writer.write(", ");
                    }
                    firstFeature = false;

                    // Modify timestamp
                    JsonNode modifiedFeature = modifyTimestamp(feature, yearOffset);

                    // Write feature (pretty-printed)
                    String featureJson = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(modifiedFeature);
                    writer.write(featureJson);

                    totalFeaturesWritten++;

                    // Log progress periodically
                    if (totalFeaturesWritten % 10000 == 0) {
                        log.debug("  Written {} features so far...", totalFeaturesWritten);
                    }
                }

                log.info("  Completed iteration {}: {} features written (total: {})",
                        iteration + 1, features.size(), totalFeaturesWritten);
            }

            // Write FeatureCollection footer
            writer.write(" ]\n");
            writer.write("}\n");

            log.info("File generation completed!");
            log.info("  Total features written: {}", totalFeaturesWritten);
            log.info("  Output file: {}", targetFilePath);

            File targetFile = new File(targetFilePath);
            long targetFileSize = targetFile.length();
            log.info("  Actual file size: {} MB ({} GB)",
                    targetFileSize / (1024 * 1024),
                    String.format("%.2f", targetFileSize / (1024.0 * 1024 * 1024)));

            long duration = System.currentTimeMillis() - startTime;
            log.info("  Generation time: {} seconds", duration / 1000);
        }
    }

    /**
     * Parse all features from a GeoJSON file using streaming
     */
    private static List<JsonNode> parseFeatures(String filePath) throws IOException {
        List<JsonNode> features = new ArrayList<>();

        try (JsonParser parser = jsonFactory.createParser(new File(filePath))) {
            // Navigate to features array
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                if ("features".equals(fieldName)) {
                    parser.nextToken(); // Move to START_ARRAY

                    // Read each feature
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        JsonNode feature = objectMapper.readTree(parser);
                        features.add(feature);
                    }
                    break;
                } else {
                    parser.nextToken();
                    parser.skipChildren();
                }
            }
        }

        return features;
    }

    /**
     * Modify the timestamp in a feature by adding years
     */
    private static JsonNode modifyTimestamp(JsonNode feature, int yearsToAdd) {
        // Create a mutable copy
        ObjectNode mutableFeature = feature.deepCopy();

        // Get properties
        JsonNode properties = mutableFeature.get("properties");
        if (properties != null && properties.has("timestamp")) {
            String originalTimestamp = properties.get("timestamp").asText();

            try {
                // Parse timestamp
                Instant instant = Instant.parse(originalTimestamp);

                // Add years
                Instant newInstant = instant.plus(yearsToAdd * 365L, ChronoUnit.DAYS);

                // Update timestamp in properties
                ((ObjectNode) properties).put("timestamp", newInstant.toString());
            } catch (Exception e) {
                log.warn("Failed to parse timestamp: {}", originalTimestamp);
            }
        }

        return mutableFeature;
    }

    /**
     * Main method to generate a 1GB test file
     */
    public static void main(String[] args) throws IOException {
        String sourceFile = "src/main/resources/geopulse-export-d13b7e50-1761289624.geojson";

        if (args.length > 0 && args[0].equals("--small")) {
            // Generate 455MB file (10 iterations)
            String targetFile = "src/test/resources/test-geojson-455mb.geojson";
            generateLargeGeoJsonFile(sourceFile, targetFile, 10, 1);
            System.out.println("\n✅ Small test file generated: " + targetFile);
        } else if (args.length > 0 && args[0].equals("--xlarge")) {
            // Generate ~2GB file (20 iterations)
            String targetFile = "src/test/resources/test-geojson-2gb.geojson";
            generateLargeGeoJsonFile(sourceFile, targetFile, 20, 1);
            System.out.println("\n✅ Extra large test file generated: " + targetFile);
        } else {
            // Default: Generate ~1GB file (assuming pretty-print overhead)
            String targetFile = "src/test/resources/test-geojson-1gb.geojson";

            // Calculate iterations needed for ~1GB (with pretty-print overhead)
            File source = new File(sourceFile);
            long sourceSizeBytes = source.length();
            long targetSizeBytes = 1024L * 1024L * 1024L; // 1GB

            // With pretty-printing, file is ~4.4x larger than compressed
            // 104MB source -> 455MB output with 10 iterations
            // So we need ~22 iterations for 1GB
            int iterations = (int) Math.ceil((double) targetSizeBytes / (sourceSizeBytes * 4.4));

            System.out.println("Calculated iterations needed for 1GB: " + iterations);
            generateLargeGeoJsonFile(sourceFile, targetFile, iterations, 1);
            System.out.println("\n✅ Test file generation completed!");
            System.out.println("📁 Output: " + targetFile);
        }

        System.out.println("\nYou can now use this file for large file import testing.");
        System.out.println("\nUsage:");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"...GeoJsonTestFileGenerator\" -Dexec.classpathScope=test");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"...GeoJsonTestFileGenerator\" -Dexec.classpathScope=test -Dexec.args=\"--small\"");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"...GeoJsonTestFileGenerator\" -Dexec.classpathScope=test -Dexec.args=\"--xlarge\"");
    }
}
