package org.github.tess1o.geopulse.admin.backup;

import java.io.IOException;
import java.sql.*;

/** The only component allowed to rename databases. Both renames commit or roll back together. */
public final class DatabaseCutover {
    public enum CurrentIdentity { ORIGINAL, STAGED, UNEXPECTED }
    private final PostgresTarget postgres;
    public DatabaseCutover(PostgresTarget postgres) { this.postgres = postgres; }

    public void validateReady(RestoreState state) throws Exception {
        try (Connection control = postgres.connect(postgres.maintenanceDatabase(), true)) {
            if (NativeDatabaseBackup.databaseOid(control, state.originalDatabase) != state.originalOid
                    || NativeDatabaseBackup.databaseOid(control, state.stagingDatabase) != state.stagingOid
                    || NativeDatabaseBackup.databaseOid(control, state.previousDatabase) != 0) {
                throw new IOException("Recorded database identities changed before activation");
            }
        }
    }

    public CurrentIdentity currentIdentity(RestoreState state) throws SQLException {
        try (Connection current = postgres.connect(postgres.maintenanceDatabase(), true)) {
            long oid = NativeDatabaseBackup.databaseOid(current, postgres.database());
            if (oid == state.stagingOid) return CurrentIdentity.STAGED;
            if (oid == state.originalOid) return CurrentIdentity.ORIGINAL;
            return CurrentIdentity.UNEXPECTED;
        }
    }

    public void activate(RestoreState state) throws Exception {
        SQLException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (!terminateConnections(state.originalOid, state.stagingOid)) continue;
            try (Connection control = postgres.connect(postgres.maintenanceDatabase(), true)) {
                control.setAutoCommit(false);
                try {
                    NativeDatabaseBackup.execute(control, "SET LOCAL lock_timeout='5s'");
                    NativeDatabaseBackup.execute(control, "SET LOCAL statement_timeout='20s'");
                    NativeDatabaseBackup.execute(control, "ALTER DATABASE " + PostgresTarget.quote(state.originalDatabase)
                            + " RENAME TO " + PostgresTarget.quote(state.previousDatabase));
                    NativeDatabaseBackup.execute(control, "ALTER DATABASE " + PostgresTarget.quote(state.stagingDatabase)
                            + " RENAME TO " + PostgresTarget.quote(state.originalDatabase));
                    NativeDatabaseBackup.execute(control, "ALTER DATABASE " + PostgresTarget.quote(state.previousDatabase)
                            + " ALLOW_CONNECTIONS false");
                    control.commit();
                    return;
                } catch (SQLException failure) {
                    last = failure;
                    try { control.rollback(); } catch (SQLException ignored) { }
                }
            }
            // A broken connection can hide a successful COMMIT. Reconcile before attempting any rename again.
            if (isCommitted(state)) return;
        }
        if (isCommitted(state)) return;
        throw new IOException("Database activation could not obtain an exclusive cutover after terminating active clients", last);
    }

    public boolean isCommitted(RestoreState state) throws SQLException {
        try (Connection control = postgres.connect(postgres.maintenanceDatabase(), true)) {
            return NativeDatabaseBackup.databaseOid(control, state.originalDatabase) == state.stagingOid
                    && NativeDatabaseBackup.databaseOid(control, state.previousDatabase) == state.originalOid;
        }
    }

    private boolean terminateConnections(long originalOid, long stagingOid) throws SQLException {
        try (Connection control = postgres.connect(postgres.maintenanceDatabase(), true);
             Statement timeout = control.createStatement();
             PreparedStatement terminate = control.prepareStatement("""
                     SELECT pid, pg_terminate_backend(pid, 5000)
                     FROM pg_stat_activity
                     WHERE datid IN (?, ?) AND pid <> pg_backend_pid()
                     """)) {
            timeout.execute("SET statement_timeout='20s'");
            terminate.setLong(1, originalOid);
            terminate.setLong(2, stagingOid);
            boolean terminated = true;
            try (ResultSet results = terminate.executeQuery()) {
                while (results.next()) {
                    terminated &= results.getBoolean(2);
                }
            }
            return terminated;
        }
    }
}
