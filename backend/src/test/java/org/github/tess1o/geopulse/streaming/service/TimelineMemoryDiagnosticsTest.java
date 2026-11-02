package org.github.tess1o.geopulse.streaming.service;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.hibernate.Session;
import org.hibernate.stat.SessionStatistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@Tag("unit")
class TimelineMemoryDiagnosticsTest {

    @TempDir
    Path directory;

    @Test
    void disabledDiagnosticsDoNotOpenASessionOrRegisterCallbacks() {
        var diagnostics = new TimelineMemoryDiagnostics();
        diagnostics.entityManager = mock(EntityManager.class);
        diagnostics.transactions = mock(TransactionSynchronizationRegistry.class);
        diagnostics.checkpoint(UUID.randomUUID(), null, "disabled");
        diagnostics.watchTransaction(UUID.randomUUID(), null);
        verifyNoInteractions(diagnostics.entityManager, diagnostics.transactions);
    }

    @Test
    void readsLinuxProcessKilobytesAndCgroupBytesWithoutMixingUnits() throws Exception {
        Path file = directory.resolve("status");
        Files.writeString(file, "Name:\tapplication\nVmRSS:\t123 kB\nRssAnon: 100 kB\nother: 5 kB\n");
        assertThat(TimelineMemoryDiagnostics.readFields(file, "VmRSS", "RssAnon"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("VmRSS", 123 * 1024L, "RssAnon", 100 * 1024L));
        Files.writeString(file, "anon 123\nfile 456\nkernel 789\n");
        assertThat(TimelineMemoryDiagnostics.readFields(file, "anon", "file"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("anon", 123L, "file", 456L));
    }

    @Test
    void unavailableAndUnlimitedCountersAreExplicitlyUnknown() throws Exception {
        Path file = directory.resolve("memory.max");
        assertThat(TimelineMemoryDiagnostics.readLong(file)).isEqualTo(-1);
        assertThat(TimelineMemoryDiagnostics.readFields(file, "anon")).isEmpty();
        Files.writeString(file, "max\n");
        assertThat(TimelineMemoryDiagnostics.readLong(file)).isEqualTo(-1);
        Files.writeString(file, "536870912\n");
        assertThat(TimelineMemoryDiagnostics.readLong(file)).isEqualTo(536870912L);
    }

    @Test
    void sessionCountsUseOnlyStatisticsAndDoNotRunQueriesOrFlush() {
        var diagnostics = new TimelineMemoryDiagnostics();
        diagnostics.entityManager = mock(EntityManager.class);
        Session session = mock(Session.class);
        SessionStatistics statistics = mock(SessionStatistics.class);
        when(diagnostics.entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getStatistics()).thenReturn(statistics);
        when(statistics.getEntityCount()).thenReturn(2001);
        when(statistics.getCollectionCount()).thenReturn(4);
        Map<String, Long> snapshot = diagnostics.snapshot();
        assertThat(snapshot).containsEntry("managedEntities", 2001L).containsEntry("managedCollections", 4L);
        assertThat(snapshot.get("heapUsedBytes")).isPositive().isLessThanOrEqualTo(snapshot.get("heapCommittedBytes"));
        verify(diagnostics.entityManager).unwrap(Session.class);
        verify(session).getStatistics();
        verify(statistics).getEntityCount();
        verify(statistics).getCollectionCount();
        verifyNoMoreInteractions(diagnostics.entityManager, session, statistics);
    }

    @Test
    void closedSessionsDoNotHideOtherMeasurements() {
        var diagnostics = new TimelineMemoryDiagnostics();
        diagnostics.entityManager = mock(EntityManager.class);
        when(diagnostics.entityManager.unwrap(Session.class)).thenThrow(new IllegalStateException("closed"));
        assertThat(diagnostics.snapshot()).containsEntry("managedEntities", -1L);
    }

    @Test
    void transactionCheckpointsIncludeCommitAndRollbackOutcome() {
        var diagnostics = spy(new TimelineMemoryDiagnostics());
        diagnostics.enabled = true;
        diagnostics.transactions = mock(TransactionSynchronizationRegistry.class);
        doReturn(Map.of("heapUsedBytes", 123L)).when(diagnostics).snapshot();
        UUID user = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        diagnostics.watchTransaction(user, job);
        var callback = ArgumentCaptor.forClass(Synchronization.class);
        verify(diagnostics.transactions).registerInterposedSynchronization(callback.capture());
        callback.getValue().beforeCompletion();
        callback.getValue().afterCompletion(Status.STATUS_ROLLEDBACK);
        verify(diagnostics).checkpoint(user, job, "before_commit");
        verify(diagnostics).checkpoint(user, job, "after_transaction", Map.of("transactionStatus", Status.STATUS_ROLLEDBACK));
    }

    @Test
    void unavailableTransactionHooksDoNotBreakGeneration() {
        var diagnostics = new TimelineMemoryDiagnostics();
        diagnostics.enabled = true;
        diagnostics.transactions = mock(TransactionSynchronizationRegistry.class);
        doThrow(new IllegalStateException("no transaction")).when(diagnostics.transactions)
                .registerInterposedSynchronization(any());
        assertThatCode(() -> diagnostics.watchTransaction(UUID.randomUUID(), null)).doesNotThrowAnyException();
    }
}
