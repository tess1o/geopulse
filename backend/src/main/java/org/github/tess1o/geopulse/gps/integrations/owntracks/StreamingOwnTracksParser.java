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
     * This method reads the JSON array incrementally:
     * 1. Validates the root is a JSON array
     * 2. Streams through the array elements
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
            // Expect root array to start
            JsonToken firstToken = parser.nextToken();
            if (firstToken != JsonToken.START_ARRAY) {
                throw new IllegalArgumentException(
                    "OwnTracks JSON must be an array, found: " + firstToken);
            }

            // Stream through array elements
            parseMessageArray(parser, callback, stats);

            log.info("Streaming parse completed: {} messages, {} valid messages",
                    stats.totalMessages, stats.validMessages);

            return stats;
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
