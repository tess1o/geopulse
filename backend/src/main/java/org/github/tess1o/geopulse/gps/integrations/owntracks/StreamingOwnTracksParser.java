package org.github.tess1o.geopulse.gps.integrations.owntracks;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Streaming parser for OwnTracks JSON files that processes location messages incrementally
 * without loading the entire file into memory.
 *
 * This parser uses Jackson's streaming API (JsonParser) to parse OwnTracks messages
 * one by one, dramatically reducing memory consumption for large files.
 *
 * Memory usage: ~5-10MB regardless of file size
 */
@Slf4j
public class StreamingOwnTracksParser {

    private final InputStream inputStream;
    private final ObjectMapper objectMapper;
    private final JsonFactory jsonFactory;

    /**
     * Create a streaming parser from a byte array (typical use case from ImportJob.fileData)
     */
    public StreamingOwnTracksParser(byte[] data, ObjectMapper objectMapper) {
        this(new ByteArrayInputStream(data), objectMapper);
    }

    /**
     * Create a streaming parser from an InputStream
     */
    public StreamingOwnTracksParser(InputStream inputStream, ObjectMapper objectMapper) {
        this.inputStream = inputStream;
        this.objectMapper = objectMapper;
        this.jsonFactory = new JsonFactory();
    }

    /**
     * Parse OwnTracks messages one-by-one and invoke callback for each message.
     *
     * This method reads OwnTracks exports incrementally. It supports both the
     * legacy GeoPulse array format and the official ocat wrapper format:
     * 1. Root array: streams array elements directly
     * 2. Root object: finds and streams the "locations" array, skipping metadata
     * 3. Deserializes each message individually
     * 4. Invokes callback with message and current statistics
     *
     * @param callback Function to process each message as it's parsed
     * @return Final parsing statistics
     * @throws IOException if JSON parsing fails or structure is invalid
     */
    public ParsingStats parseMessages(MessageCallback callback) throws IOException {
        ParsingStats stats = new ParsingStats();

        try (JsonParser parser = jsonFactory.createParser(inputStream)) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken == JsonToken.START_ARRAY) {
                parseMessageArray(parser, callback, stats);
            } else if (firstToken == JsonToken.START_OBJECT) {
                parseOwnTracksObject(parser, callback, stats);
            } else {
                throw new IllegalArgumentException(
                    "OwnTracks JSON must be an array or an object with a locations array, found: " + firstToken);
            }

            log.info("Streaming parse completed: {} messages, {} valid messages",
                    stats.totalMessages, stats.validMessages);

            return stats;
        }
    }

    /**
     * Parse official ocat format: {"count": n, "locations": [...]}
     */
    private void parseOwnTracksObject(JsonParser parser, MessageCallback callback, ParsingStats stats)
            throws IOException {
        boolean foundLocations = false;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                parser.skipChildren();
                continue;
            }

            String fieldName = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if ("locations".equals(fieldName)) {
                if (valueToken != JsonToken.START_ARRAY) {
                    throw new IllegalArgumentException(
                            "OwnTracks JSON object field 'locations' must be an array, found: " + valueToken);
                }
                foundLocations = true;
                parseMessageArray(parser, callback, stats);
            } else {
                parser.skipChildren();
            }
        }

        if (!foundLocations) {
            throw new IllegalArgumentException(
                    "OwnTracks JSON object must contain a locations array");
        }
    }

    /**
     * Parse the messages array, deserializing one message at a time
     */
    private void parseMessageArray(JsonParser parser, MessageCallback callback, ParsingStats stats)
            throws IOException {

        // Iterate through array elements
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            // Deserialize this single message object
            OwnTracksLocationMessage message = objectMapper.readValue(parser, OwnTracksLocationMessage.class);

            stats.totalMessages++;

            // Update statistics based on message validity
            if (isValidMessage(message)) {
                stats.validMessages++;
            }

            // Invoke callback with message and current stats
            callback.onMessage(message, stats);

            // Log progress periodically
            if (stats.totalMessages % 10000 == 0) {
                log.debug("Parsed {} messages, {} valid messages so far",
                        stats.totalMessages, stats.validMessages);
            }
        }
    }

    /**
     * Check if message has minimum required fields for GPS tracking
     */
    private boolean isValidMessage(OwnTracksLocationMessage message) {
        return message.getLat() != null &&
               message.getLon() != null &&
               message.getTst() != null;
    }

    /**
     * Callback interface for processing messages as they are parsed
     */
    @FunctionalInterface
    public interface MessageCallback {
        /**
         * Process a single parsed message
         *
         * @param message The parsed OwnTracks location message
         * @param stats Current parsing statistics (cumulative)
         */
        void onMessage(OwnTracksLocationMessage message, ParsingStats stats);
    }

    /**
     * Statistics tracked during parsing
     */
    public static class ParsingStats {
        public int totalMessages = 0;
        public int validMessages = 0;

        @Override
        public String toString() {
            return String.format("ParsingStats{totalMessages=%d, validMessages=%d}",
                    totalMessages, validMessages);
        }
    }
}
