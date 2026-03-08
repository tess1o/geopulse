package org.github.tess1o.geopulse.friends.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FriendshipRepositoryTimestampTest {

    @Mock
    EntityManager entityManager;

    @Mock
    Query query;

    @InjectMocks
    FriendshipRepository friendshipRepository;

    @Test
    void findFriendsShouldHandleSqlTimestampFromNativeQuery() {
        UUID userId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        Instant ts = Instant.parse("2026-03-06T17:05:34Z");

        Object[] row = new Object[]{
                userId.toString(),
                friendId.toString(),
                "friend@example.com",
                "Friend Name",
                null,
                Timestamp.from(ts),
                null,
                "TRIP",
                120,
                true,
                true
        };

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("userId"), eq(userId))).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(row));

        List<FriendInfoDTO> result = friendshipRepository.findFriends(userId);

        assertEquals(1, result.size());
        assertEquals("2026-03-06T17:05:34Z", result.getFirst().getLastSeen());
    }

    @Test
    void findFriendsShouldHandleOffsetDateTimeFromNativeQuery() {
        UUID userId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        OffsetDateTime ts = OffsetDateTime.parse("2026-03-06T17:05:34Z");

        Object[] row = new Object[]{
                userId.toString(),
                friendId.toString(),
                "friend@example.com",
                "Friend Name",
                null,
                ts,
                null,
                "STAY",
                300,
                true,
                false
        };

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("userId"), eq(userId))).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(row));

        List<FriendInfoDTO> result = friendshipRepository.findFriends(userId);

        assertEquals(1, result.size());
        assertEquals("2026-03-06T17:05:34Z", result.getFirst().getLastSeen());
    }
}
