package org.github.tess1o.geopulse.ai.memory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.client.dto.ChatMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Slf4j
public class ChatMemoryStore {

    @Inject
    SystemSettingsService systemSettingsService;

    private final Map<UUID, CircularBuffer<ChatMessage>> memories = new ConcurrentHashMap<>();

    public void addMessage(UUID userId, ChatMessage message) {
        int maxMessages = systemSettingsService.getInteger("ai.chat-memory.max-messages");
        memories.computeIfAbsent(userId, k -> new CircularBuffer<>(maxMessages))
                .add(message);
        log.debug("Added message to chat memory for user " + userId + " (role: " + message.getRole() + ")");
    }

    public List<ChatMessage> getMessages(UUID userId) {
        CircularBuffer<ChatMessage> buffer = memories.get(userId);
        if (buffer != null) {
            List<ChatMessage> messages = buffer.toList();
            log.debug("Retrieved " + messages.size() + " messages from chat memory for user " + userId);
            return messages;
        }
        return List.of();
    }

    public void clear(UUID userId) {
        memories.remove(userId);
        log.info("Cleared chat memory for user " + userId);
    }

    public void clearAll() {
        memories.clear();
        log.info("Cleared all chat memories");
    }

    /**
     * Thread-safe circular buffer implementation
     */
    static class CircularBuffer<T> {
        private final T[] buffer;
        private final int capacity;
        private int head = 0;
        private int tail = 0;
        private int size = 0;

        @SuppressWarnings("unchecked")
        public CircularBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = (T[]) new Object[capacity];
        }

        public synchronized void add(T item) {
            buffer[tail] = item;
            tail = (tail + 1) % capacity;

            if (size < capacity) {
                size++;
            } else {
                // Buffer is full, move head forward
                head = (head + 1) % capacity;
            }
        }

        public synchronized List<T> toList() {
            List<T> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int index = (head + i) % capacity;
                result.add(buffer[index]);
            }
            return result;
        }

        public synchronized int size() {
            return size;
        }
    }
}
