package org.github.tess1o.geopulse.admin.backup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/**
 * Full-database operations deliberately never use Hibernate or the live connection pool.
 */
@Slf4j
public final class NativeDatabaseBackup {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final NativeBackupContext context;
    private final PgTools tools;

    public NativeDatabaseBackup(NativeBackupContext context) {
        this.context = context;
        tools = new PgTools(context);
    }

    public void write(Path output, char[] password, Instant deadline, String operationId) throws Exception {
        log.info("Backup operation {} creating encrypted native database backup; file={}", operationId, output.getFileName());
        KeyCipher key = KeyCipher.load(context.keyLocation());
        RestoreJournal.createPrivateFile(output);
        try (Connection snapshot = context.postgres().connect(context.postgres().database(), false); ConnectionDeadline guard = new ConnectionDeadline(snapshot, deadline)) {
            snapshot.setAutoCommit(false);
            snapshot.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            snapshot.setReadOnly(true);
            NativeBackupManifest manifest = describe(snapshot);
            int pgDumpMajor = tools.major(operationId, "pg_dump");
            if (pgDumpMajor != manifest.postgresMajor) {
                log.error("Backup operation {} pg_dump/server major mismatch; toolMajor={}; serverMajor={}",
                        operationId, pgDumpMajor, manifest.postgresMajor);
                throw new IOException("pg_dump must match the server PostgreSQL major version");
            }
            String snapshotId = scalar(snapshot, "SELECT pg_export_snapshot()");
            try (OutputStream file = Files.newOutputStream(output, StandardOpenOption.WRITE);
                 OutputStream encrypted = BackupEnvelope.encrypt(file, password);
                 ZipOutputStream zip = new ZipOutputStream(encrypted)) {
                RestoreJournal.secureFile(output);
                zip.setLevel(0); // pg_dump custom format already compresses table data.
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                zip.putNextEntry(new ZipEntry("database.dump"));
                tools.run(operationId, "pg_dump", List.of("--format=custom", "--snapshot=" + snapshotId,
                                "--exclude-table-data=public.oidc_session_states", "--exclude-table-data=public.mobile_auth_codes"),
                        context.postgres().database(), false,
                        new DigestOutputStream(zip, digest), deadline);
                zip.closeEntry();
                manifest.dumpSha256 = HexFormat.of().formatHex(digest.digest());
                manifest.sourceKeyFingerprint = key.fingerprint();
                entry(zip, "manifest.json", JSON.writeValueAsBytes(manifest));
                byte[] bytes = key.exportKey();
                try {
                    entry(zip, "source-key", bytes);
                } finally {
                    Arrays.fill(bytes, (byte) 0);
                }
            }
            snapshot.rollback();
            log.info("Backup operation {} encrypted native database backup completed; file={}", operationId, output.getFileName());
        } catch (Exception e) {
            Files.deleteIfExists(output);
            throw e;
        }
    }

    public void prepare(Path archive, char[] password, RestoreState state, Instant deadline, Consumer<RestorePhase> phase) throws Exception {
        log.info("Restore operation {} preparation started", state.operationId);
        Path extracted = Files.createDirectory(context.workPath().resolve(state.operationId + ".extract"));
        RestoreJournal.secureDirectory(extracted);
        byte[] sourceKey = null;
        try {
            NativeBackupManifest manifest;
            Set<String> entries = new HashSet<>();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream raw = Files.newInputStream(archive);
                 InputStream decrypted = BackupEnvelope.decrypt(raw, password);
                 ZipInputStream zip = new ZipInputStream(decrypted)) {
                ZipEntry entry;
                byte[] metadata = null;
                while ((entry = zip.getNextEntry()) != null) {
                    checkDeadline(deadline);
                    String name = entry.getName();
                    if (entry.isDirectory() || !Set.of("database.dump", "manifest.json", "source-key").contains(name) || !entries.add(name))
                        throw new IOException("Unexpected or duplicate backup entry");
                    if (name.equals("database.dump")) {
                        Path dump = RestoreJournal.createPrivateFile(extracted.resolve(name));
                        RestoreJournal.secureFile(dump);
                        try (OutputStream out = new DigestOutputStream(Files.newOutputStream(dump), digest)) {
                            transfer(zip, out, deadline);
                        }
                    } else if (name.equals("manifest.json")) metadata = limited(zip, 4 * 1024 * 1024);
                    else sourceKey = limited(zip, 32);
                    zip.closeEntry();
                }
                // ZIP readers can stop at the central directory before the encrypted stream's final segment.
                // Consume to authenticated EOF before executing any SQL from the archive.
                transfer(decrypted, OutputStream.nullOutputStream(), deadline);
                if (entries.size() != 3 || metadata == null || sourceKey == null)
                    throw new IOException("Incomplete backup archive");
                manifest = JSON.readValue(metadata, NativeBackupManifest.class);
            }
            if (manifest.formatVersion != 1 || !Objects.equals(manifest.dumpSha256, HexFormat.of().formatHex(digest.digest())))
                throw new IOException("Unsupported or damaged backup");
            state.backupCreatedAt = manifest.createdAt;
            context.journal().write(state);
            KeyCipher source = new KeyCipher(sourceKey);
            KeyCipher destination = KeyCipher.load(context.keyLocation());
            if (!source.fingerprint().equals(manifest.sourceKeyFingerprint))
                throw new IOException("Backup key fingerprint mismatch");
            state.keyFingerprint = destination.fingerprint();
            phase.accept(RestorePhase.PREFLIGHT);
            List<Map<String, Object>> localSettings;
            try (Connection live = context.postgres().connect(context.postgres().database(), false); ConnectionDeadline guard = new ConnectionDeadline(live, deadline)) {
                NativeBackupManifest local = describe(live);
                boolean incompatible = manifest.postgresMajor != local.postgresMajor || !manifest.migrations.equals(local.migrations);
                if (incompatible || !Objects.equals(manifest.schemaFingerprint, local.schemaFingerprint)) {
                    logRestoreManifestMismatch(state.operationId, manifest, local);
                    if (incompatible)
                        throw new IOException("Backup requires the same Flyway migration history and PostgreSQL major version");
                }
                localSettings = rows(live, "SELECT * FROM system_settings WHERE key LIKE 'backup.%' ORDER BY key");
            }
            if (tools.major(state.operationId, "pg_restore") != manifest.postgresMajor)
                throw new IOException("pg_restore must match the server PostgreSQL major version");
            try (Connection admin = context.postgres().connect(context.postgres().maintenanceDatabase(), true); ConnectionDeadline guard = new ConnectionDeadline(admin, deadline)) {
                state.originalDatabase = context.postgres().database();
                state.originalOid = databaseOid(admin, state.originalDatabase);
                String suffix = state.operationId.replace("-", "");
                state.stagingDatabase = "gp_restore_" + suffix;
                state.previousDatabase = "gp_previous_" + suffix;
                context.journal().write(state);
                createStaging(admin, state, manifest);
                state.stagingOid = databaseOid(admin, state.stagingDatabase);
                context.journal().write(state);
                log.info("Restore operation {} created staging database {}", state.operationId, state.stagingDatabase);
            }
            phase.accept(RestorePhase.RESTORING);
            Set<String> installedSchemas = new HashSet<>();
            try (Connection staging = context.postgres().connect(state.stagingDatabase, true); ConnectionDeadline guard = new ConnectionDeadline(staging, deadline)) {
                installExtensions(staging, manifest);
                for (List<String> schema : strings(staging, "SELECT nspname FROM pg_namespace WHERE nspname NOT LIKE 'pg_%' AND nspname <> 'information_schema'"))
                    installedSchemas.add(schema.get(0));
            }
            Path restoreList = extracted.resolve("restore.list");
            try (OutputStream out = Files.newOutputStream(restoreList, StandardOpenOption.CREATE_NEW)) {
                tools.run(state.operationId, "pg_restore", List.of("--list", extracted.resolve("database.dump").toString()), state.stagingDatabase, true, out, deadline);
            }
            if (Files.size(restoreList) > 16 * 1024 * 1024)
                throw new IOException("Backup object list exceeds size limit");
            List<String> objects = Files.readAllLines(restoreList);
            // PostGIS creates topology during extension installation. Avoid creating that schema twice;
            // restore every application/data object, with extension-owned objects supplied by the server.
            objects.removeIf(line -> installedSchemas.stream().anyMatch(schema -> line.matches("^[0-9]+; [0-9]+ [0-9]+ SCHEMA - " + java.util.regex.Pattern.quote(schema) + " .*")));
            Files.write(restoreList, objects);
            tools.run(state.operationId, "pg_restore", List.of("--dbname=" + state.stagingDatabase, "--exit-on-error", "--no-owner", "--no-acl", "--no-comments",
                            "--role=" + context.postgres().username(), "--use-list=" + restoreList, extracted.resolve("database.dump").toString()),
                    state.stagingDatabase, true, OutputStream.nullOutputStream(), deadline);
            phase.accept(RestorePhase.SECRETS);
            try (Connection staging = context.postgres().connect(state.stagingDatabase, false); ConnectionDeadline guard = new ConnectionDeadline(staging, deadline)) {
                staging.setAutoCommit(false);
                try {
                    reencrypt(staging, source, destination, deadline);
                    preserveSettings(staging, localSettings);
                    staging.commit();
                } catch (Exception e) {
                    staging.rollback();
                    throw e;
                }
                staging.setAutoCommit(true);
                phase.accept(RestorePhase.VALIDATING);
                NativeBackupManifest restored = describe(staging);
                if (!manifest.schemaFingerprint.equals(restored.schemaFingerprint) || !manifest.migrations.equals(restored.migrations)
                        || !manifest.extensions.equals(restored.extensions))
                    throw new IOException("Restored database metadata does not match the backup");
                validate(staging);
                checkDeadline(deadline);
                execute(staging, "ANALYZE");
            }
            checkDeadline(deadline);
            log.info("Restore operation {} preparation and validation completed", state.operationId);
        } finally {
            if (sourceKey != null) Arrays.fill(sourceKey, (byte) 0);
            RestoreJournal.removeTree(extracted);
        }
    }

    public NativeBackupManifest describe(Connection connection) throws Exception {
        NativeBackupManifest result = new NativeBackupManifest();
        result.applicationVersion = context.applicationVersion();
        result.createdAt = Instant.now().toString();
        result.postgresMajor = Integer.parseInt(scalar(connection, "SHOW server_version_num")) / 10000;
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT extname, extversion FROM pg_extension ORDER BY extname")) {
            while (rs.next()) result.extensions.put(rs.getString(1), rs.getString(2));
        }
        result.migrations = strings(connection, "SELECT version, description, type, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank");
        // Excludes runtime values/sequence positions and extension-owned objects; includes all application columns and constraints.
        result.schema = schemaDescription(connection);
        result.schemaFingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(result.schema)));
        return result;
    }

    private static void logRestoreManifestMismatch(String operationId, NativeBackupManifest backup, NativeBackupManifest local) {
        if (backup.postgresMajor != local.postgresMajor) {
            log.warn("Restore operation {} PostgreSQL major mismatch; backupMajor={}; localMajor={}",
                    operationId, backup.postgresMajor, local.postgresMajor);
        }
        if (!backup.migrations.equals(local.migrations)) {
            log.warn("Restore operation {} migration history mismatch; backupCount={}; localCount={}",
                    operationId, backup.migrations.size(), local.migrations.size());
            for (String line : migrationDiff(backup.migrations, local.migrations)) {
                log.warn("Restore operation {} migration difference: {}", operationId, line);
            }
        }
        if (!Objects.equals(backup.schemaFingerprint, local.schemaFingerprint)) {
            log.warn("Restore operation {} schema fingerprint mismatch; backupFingerprint={}; localFingerprint={}",
                    operationId, backup.schemaFingerprint, local.schemaFingerprint);
            if (backup.schema != null && !backup.schema.isEmpty()) {
                log.warn("Restore operation {} schema row mismatch; backupCount={}; localCount={}",
                        operationId, backup.schema.size(), local.schema.size());
                for (String line : rowDiff(backup.schema, local.schema, "schema")) {
                    log.warn("Restore operation {} schema difference: {}", operationId, line);
                }
            } else {
                log.warn("Restore operation {} backup manifest does not include schema rows; create a new backup with this version to log exact schema differences",
                        operationId);
            }
        }
    }

    private static List<String> migrationDiff(List<List<String>> backup, List<List<String>> local) {
        return rowDiff(backup, local, "migration");
    }

    private static List<String> rowDiff(List<List<String>> backup, List<List<String>> local, String rowType) {
        List<String> diff = new ArrayList<>();
        int common = Math.min(backup.size(), local.size());
        for (int i = 0; i < common; i++) {
            if (!backup.get(i).equals(local.get(i))) {
                diff.add("index=" + i + "; backup=" + rowSummary(backup.get(i), rowType) + "; local=" + rowSummary(local.get(i), rowType));
            }
        }
        for (int i = common; i < backup.size(); i++) {
            diff.add("backup-only index=" + i + "; " + rowSummary(backup.get(i), rowType));
        }
        for (int i = common; i < local.size(); i++) {
            diff.add("local-only index=" + i + "; " + rowSummary(local.get(i), rowType));
        }
        if (diff.size() > 50) {
            List<String> capped = new ArrayList<>(diff.subList(0, 50));
            capped.add("... " + (diff.size() - 50) + " more " + rowType + " differences omitted");
            return capped;
        }
        return diff;
    }

    private static String rowSummary(List<String> row, String rowType) {
        if ("migration".equals(rowType)) return migrationRow(row);
        return row.toString();
    }

    private static String migrationRow(List<String> row) {
        return switch (row.size()) {
            case 0 -> "[]";
            case 1 -> "version=" + row.get(0);
            case 2 -> "version=" + row.get(0) + ", description=" + row.get(1);
            case 3 -> "version=" + row.get(0) + ", description=" + row.get(1) + ", type=" + row.get(2);
            case 4 -> "version=" + row.get(0) + ", description=" + row.get(1) + ", type=" + row.get(2) + ", script=" + row.get(3);
            case 5 -> "version=" + row.get(0) + ", description=" + row.get(1) + ", type=" + row.get(2) + ", script=" + row.get(3)
                    + ", checksum=" + row.get(4);
            default -> "version=" + row.get(0) + ", description=" + row.get(1) + ", type=" + row.get(2) + ", script=" + row.get(3)
                    + ", checksum=" + row.get(4) + ", success=" + row.get(5);
        };
    }

    private static List<List<String>> schemaDescription(Connection connection) throws SQLException {
        List<List<String>> schema = strings(connection, """
                SELECT c.relname, a.attname, format_type(a.atttypid,a.atttypmod), a.attnotnull::text,
                       COALESCE(pg_get_expr(d.adbin,d.adrelid),'')
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
                LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
                WHERE n.nspname='public' AND c.relkind IN ('r','p')
                  AND NOT EXISTS (SELECT 1 FROM pg_depend dep WHERE dep.classid='pg_class'::regclass AND dep.objid=c.oid AND dep.deptype='e')
                ORDER BY c.relname,a.attnum
                """);
        // PostgreSQL rewrites equivalent CHECK casts while restoring (array casts become element casts).
        // Compare structural identities here; Flyway checksums establish the application definitions and
        // pg_restore builds/validates the actual constraints from the authenticated native dump.
        schema.addAll(strings(connection, """
                SELECT conrelid::regclass::text, conname, contype::text,
                       ARRAY(SELECT a.attname FROM unnest(conkey) WITH ORDINALITY k(attnum,ord)
                             JOIN pg_attribute a ON a.attrelid=conrelid AND a.attnum=k.attnum ORDER BY ord)::text,
                       confrelid::regclass::text,
                       ARRAY(SELECT a.attname FROM unnest(confkey) WITH ORDINALITY k(attnum,ord)
                             JOIN pg_attribute a ON a.attrelid=confrelid AND a.attnum=k.attnum ORDER BY ord)::text,
                       confupdtype::text, confdeltype::text, condeferrable::text, condeferred::text, convalidated::text
                FROM pg_constraint WHERE connamespace='public'::regnamespace
                ORDER BY conrelid::regclass::text, conname
                """));
        // Catalog order can change when pg_restore recreates objects even when every structural row is identical.
        schema.sort(Comparator.comparing(Object::toString));
        return schema;
    }

    private void createStaging(Connection admin, RestoreState state, NativeBackupManifest manifest) throws Exception {
        for (var ext : manifest.extensions.entrySet()) {
            try (PreparedStatement check = admin.prepareStatement("SELECT 1 FROM pg_available_extension_versions WHERE name=? AND version=?")) {
                check.setString(1, ext.getKey());
                check.setString(2, ext.getValue());
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next())
                        throw new IOException("Required PostgreSQL extension version is not available: " + ext.getKey());
                }
            }
        }
        try (PreparedStatement query = admin.prepareStatement("SELECT pg_encoding_to_char(encoding), datcollate, datctype, datlocprovider FROM pg_database WHERE oid=?")) {
            query.setLong(1, state.originalOid);
            try (ResultSet rs = query.executeQuery()) {
                if (!rs.next() || !"c".equals(rs.getString(4)))
                    throw new IOException("Native restore currently requires a libc database locale");
                execute(admin, "CREATE DATABASE " + PostgresTarget.quote(state.stagingDatabase) + " WITH TEMPLATE template0 OWNER " + PostgresTarget.quote(context.postgres().username())
                        + " ENCODING " + literal(rs.getString(1)) + " LC_COLLATE " + literal(rs.getString(2)) + " LC_CTYPE " + literal(rs.getString(3)));
            }
        }
    }

    private void installExtensions(Connection staging, NativeBackupManifest manifest) throws SQLException {
        for (var ext : manifest.extensions.entrySet()) {
            execute(staging, "CREATE EXTENSION IF NOT EXISTS " + PostgresTarget.quote(ext.getKey()) + " VERSION " + literal(ext.getValue()) + " CASCADE");
        }
        // Extension config tables may be included in pg_dump, but are owned by the installer rather than the app role.
        for (List<String> row : strings(staging, "SELECT n.nspname,c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_depend d ON d.classid='pg_class'::regclass AND d.objid=c.oid AND d.deptype='e' WHERE c.relkind='r'")) {
            execute(staging, "GRANT USAGE ON SCHEMA " + PostgresTarget.quote(row.get(0)) + " TO " + PostgresTarget.quote(context.postgres().username()));
            execute(staging, "GRANT SELECT,INSERT,UPDATE,DELETE ON " + PostgresTarget.quote(row.get(0)) + "." + PostgresTarget.quote(row.get(1)) + " TO " + PostgresTarget.quote(context.postgres().username()));
        }
    }

    private void reencrypt(Connection connection, KeyCipher source, KeyCipher target, Instant deadline) throws Exception {
        for (String[] fields : List.of(
                new String[]{"system_settings", "key", "value", "encryption_key_id", "value_type='ENCRYPTED'"},
                new String[]{"oidc_providers", "name", "client_secret_encrypted", "client_secret_key_id", "true"},
                new String[]{"geocoding_provider_configs", "id", "headers_json", "headers_key_id", "true"},
                new String[]{"gps_source_config", "id", "payload_encryption_secret_encrypted", "payload_encryption_secret_key_id", "true"},
                new String[]{"users", "id", "ai_settings_encrypted", "ai_settings_key_id", "true"})) {
            String table = fields[0], id = fields[1], column = fields[2], keyId = fields[3];
            String select = "SELECT " + id + "," + column + "," + keyId + " FROM " + table + " WHERE " + fields[4] + " AND " + column + " IS NOT NULL AND " + column + "<>''";
            try (PreparedStatement read = connection.prepareStatement(select); PreparedStatement update = connection.prepareStatement("UPDATE " + table + " SET " + column + "=?," + keyId + "='v1' WHERE " + id + "=?")) {
                read.setFetchSize(128);
                try (ResultSet rs = read.executeQuery()) {
                    while (rs.next()) {
                        checkDeadline(deadline);
                        String clear = source.decrypt(rs.getString(2), rs.getString(3));
                        if (table.equals("users")) {
                            JsonNode json = JSON.readTree(clear);
                            JsonNode nested = json.get("openaiApiKey");
                            if (nested != null && !nested.isNull() && !nested.asText().isBlank())
                                ((ObjectNode) json).put("openaiApiKey", target.encrypt(source.decrypt(nested.asText(), rs.getString(3))));
                            clear = JSON.writeValueAsString(json);
                        }
                        String encrypted = target.encrypt(clear);
                        if (!clear.equals(target.decrypt(encrypted, "v1")))
                            throw new IOException("Secret validation failed");
                        update.setString(1, encrypted);
                        update.setObject(2, rs.getObject(1));
                        update.executeUpdate();
                    }
                }
            }
        }
    }

    private void preserveSettings(Connection staging, List<Map<String, Object>> settings) throws SQLException {
        execute(staging, "DELETE FROM system_settings WHERE key LIKE 'backup.%'");
        for (Map<String, Object> row : settings) {
            row.put("updated_by", null); // The originating admin may not exist in the restored database.
            List<String> columns = new ArrayList<>(row.keySet());
            String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
            try (PreparedStatement statement = staging.prepareStatement("INSERT INTO system_settings (" + String.join(",", columns) + ") VALUES (" + placeholders + ")")) {
                for (int i = 0; i < columns.size(); i++) statement.setObject(i + 1, row.get(columns.get(i)));
                statement.executeUpdate();
            }
        }
    }

    /**
     * Re-read installation-local backup settings immediately before cutover.
     */
    public void refreshDestinationBackupSettings(RestoreState state, Instant deadline) throws Exception {
        List<Map<String, Object>> settings;
        try (Connection live = context.postgres().connect(context.postgres().database(), false); ConnectionDeadline guard = new ConnectionDeadline(live, deadline)) {
            settings = rows(live, "SELECT * FROM system_settings WHERE key LIKE 'backup.%' ORDER BY key");
        }
        try (Connection staging = context.postgres().connect(state.stagingDatabase, false); ConnectionDeadline guard = new ConnectionDeadline(staging, deadline)) {
            staging.setAutoCommit(false);
            try {
                preserveSettings(staging, settings);
                staging.commit();
            } catch (Exception e) {
                staging.rollback();
                throw e;
            }
        }
    }

    private void validate(Connection staging) throws Exception {
        if (!"POINT(1 2)".equals(scalar(staging, "SELECT ST_AsText(ST_SetSRID(ST_MakePoint(1,2),4326))")))
            throw new IOException("PostGIS validation failed");
        if ("0".equals(scalar(staging, "SELECT count(*) FROM users u WHERE role='ADMIN' AND is_active AND (NULLIF(password_hash,'') IS NOT NULL OR EXISTS (SELECT 1 FROM user_oidc_connections o WHERE o.user_id=u.id))")))
            throw new IOException("The backup does not contain an active administrator with login credentials");
        if (!"0".equals(scalar(staging, "SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated")))
            throw new IOException("Restored database contains unvalidated constraints");
        // Read every sequence without advancing it. Identity/serial sequences must not issue an existing ID.
        for (List<String> sequence : strings(staging, "SELECT schemaname,sequencename FROM pg_sequences WHERE schemaname='public'")) {
            scalar(staging, "SELECT last_value FROM " + PostgresTarget.quote(sequence.get(0)) + "." + PostgresTarget.quote(sequence.get(1)));
        }
        for (List<String> sequence : strings(staging, """
                SELECT ns.nspname, seq.relname, nt.nspname, tbl.relname, a.attname, s.seqincrement::text
                FROM pg_class seq JOIN pg_sequence s ON s.seqrelid=seq.oid
                JOIN pg_namespace ns ON ns.oid=seq.relnamespace
                JOIN pg_depend d ON d.classid='pg_class'::regclass AND d.objid=seq.oid AND d.deptype IN ('a','i')
                JOIN pg_class tbl ON tbl.oid=d.refobjid JOIN pg_namespace nt ON nt.oid=tbl.relnamespace
                JOIN pg_attribute a ON a.attrelid=tbl.oid AND a.attnum=d.refobjsubid
                WHERE ns.nspname='public' AND s.seqincrement=1
                """)) {
            String name = PostgresTarget.quote(sequence.get(0)) + "." + PostgresTarget.quote(sequence.get(1));
            String table = PostgresTarget.quote(sequence.get(2)) + "." + PostgresTarget.quote(sequence.get(3));
            String valid = scalar(staging, "SELECT (last_value + CASE WHEN is_called THEN 1 ELSE 0 END > COALESCE((SELECT max(" +
                    PostgresTarget.quote(sequence.get(4)) + ") FROM " + table + "),0)) FROM " + name);
            if (!"t".equals(valid)) throw new IOException("Restored sequence would generate duplicate IDs");
        }
    }

    public void discard(RestoreState state) throws Exception {
        if (state.stagingDatabase == null) return;
        // Never infer a deletion target from journal text alone. A staging database is removable only
        // when its operation-derived name, configured original database, and recorded positive OIDs agree.
        state.validateDatabaseIdentity(context.postgres().database(), true);
        try (Connection admin = context.postgres().connect(context.postgres().maintenanceDatabase(), true)) {
            long oid = databaseOid(admin, state.stagingDatabase);
            if (oid == 0) return;
            if (oid != state.stagingOid)
                throw new IOException("Staging database OID changed; manual cleanup is required");
            execute(admin, "DROP DATABASE " + PostgresTarget.quote(state.stagingDatabase));
            log.info("Restore operation {} dropped staging database {}", state.operationId, state.stagingDatabase);
        }
    }

    public static long databaseOid(Connection c, String name) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("SELECT oid FROM pg_database WHERE datname=?")) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public static String scalar(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            if (!rs.next()) throw new SQLException("Expected one result");
            return rs.getString(1);
        }
    }

    public static void execute(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    public static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static List<List<String>> strings(Connection c, String sql) throws SQLException {
        List<List<String>> result = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) row.add(rs.getString(i));
                result.add(row);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> rows(Connection c, String sql) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                result.add(row);
            }
        }
        return result;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] limited(InputStream in, int limit) throws IOException {
        byte[] bytes = in.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("Backup metadata exceeds size limit");
        return bytes;
    }

    private static void transfer(InputStream in, OutputStream out, Instant deadline) throws IOException {
        byte[] buffer = new byte[65536];
        int length;
        while ((length = in.read(buffer)) != -1) {
            checkDeadline(deadline);
            out.write(buffer, 0, length);
        }
    }

    public static void checkDeadline(Instant deadline) throws IOException {
        if (Thread.currentThread().isInterrupted() || Instant.now().isAfter(deadline))
            throw new IOException("Backup operation timed out or was interrupted");
    }
}
