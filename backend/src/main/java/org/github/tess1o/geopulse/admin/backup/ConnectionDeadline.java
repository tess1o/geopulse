package org.github.tess1o.geopulse.admin.backup;

import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

/** Also bounds blocked JDBC reads/SQL, not just the native subprocess. */
final class ConnectionDeadline implements AutoCloseable {
    private final ScheduledExecutorService timer;
    private final ScheduledFuture<?> abort;

    ConnectionDeadline(Connection connection, Instant deadline) throws Exception {
        NativeDatabaseBackup.checkDeadline(deadline);
        int millis = Math.toIntExact(Math.max(1, Duration.between(Instant.now(), deadline).toMillis()));
        connection.setNetworkTimeout(Runnable::run, millis);
        NativeDatabaseBackup.execute(connection, "SET statement_timeout=" + millis);
        timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());
        abort = timer.schedule(() -> {
            try { connection.abort(Runnable::run); } catch (Exception ignored) { }
        }, millis, TimeUnit.MILLISECONDS);
    }

    @Override public void close() {
        abort.cancel(false);
        timer.shutdownNow();
    }
}
