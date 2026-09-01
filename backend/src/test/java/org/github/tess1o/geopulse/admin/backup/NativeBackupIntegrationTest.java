package org.github.tess1o.geopulse.admin.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.github.tess1o.geopulse.admin.backup.NativeDatabaseBackup.*;

/** Real migrations, PostGIS and native tools. Never uses an existing application database. */
class NativeBackupIntegrationTest {
    static Path root;
    static PostgreSQLContainer<?> postgres;
    NativeBackupContext source, destination;
    KeyCipher sourceKey, destinationKey;
    Path archive;
    final UUID user = UUID.randomUUID();
    final char[] password = "backup password kept outside GeoPulse".toCharArray();

    @BeforeAll static void startDatabase() throws Exception {
        root = Files.createTempDirectory(Path.of(System.getProperty("user.home")), ".geopulse-native-backup-test-");
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"))
                .withUsername("postgres").withPassword("test-password").withDatabaseName("test")
                .withFileSystemBind(root.toString(), root.toString(), BindMode.READ_WRITE);
        postgres.start();
        Path bin = Files.createDirectories(root.resolve("bin"));
        for (String tool : List.of("pg_dump", "pg_restore")) {
            // Execute the container's matching client version; the extracted dump is in the shared mount.
            Path script = bin.resolve(tool);
            Files.writeString(script, "#!/bin/sh\nexec docker exec -i -e PGDATABASE -e PGUSER -e PGPASSWORD -e PGAPPNAME "
                    + postgres.getContainerId() + " " + tool + " \"$@\" 2>" + root.resolve(tool + "-stderr") + "\n");
            assertThat(script.toFile().setExecutable(true)).isTrue();
        }
    }

    @AfterAll static void stopDatabase() throws Exception {
        if (postgres != null) postgres.stop();
        RestoreJournal.removeTree(root);
    }

    @BeforeEach void createInstallations() throws Exception {
        source = installation("src"); destination = installation("dst");
        sourceKey = KeyCipher.load(source.keyLocation()); destinationKey = KeyCipher.load(destination.keyLocation());
        archive = root.resolve(UUID.randomUUID() + ".gpb");
        try (Connection c = source.postgres().connect(source.postgres().database(), false)) {
            execute(c, "INSERT INTO users(id,email,emailverified,password_hash,role,is_active,full_name) VALUES ('" + user + "','admin@source.test',true,'password-hash','ADMIN',true,'0')");
            setting(c, "test.snapshot", "0", false, sourceKey);
            setting(c, "test.secret", "system secret", true, sourceKey);
            setting(c, "backup.password", "source saved backup password", true, sourceKey);
            try (PreparedStatement s = c.prepareStatement("INSERT INTO oidc_providers(name,display_name,client_id,discovery_url,client_secret_encrypted,client_secret_key_id) VALUES ('test','Test','client','https://invalid.test',?,'v1')")) {
                s.setString(1,sourceKey.encrypt("OIDC secret")); s.executeUpdate();
            }
            try (PreparedStatement s = c.prepareStatement("INSERT INTO geocoding_provider_configs(id,name,display_name,type,url,headers_json,headers_key_id) VALUES (nextval('geocoding_provider_configs_seq'),'test','Test','nominatim','https://invalid.test',?,'v1')")) {
                s.setString(1,sourceKey.encrypt("{\"Authorization\":\"geocoding secret\"}")); s.executeUpdate();
            }
            try (PreparedStatement s = c.prepareStatement("INSERT INTO gps_source_config(id,user_id,source_type,active,token,payload_encryption_secret_encrypted,payload_encryption_secret_key_id) VALUES (?,?,'OWNTRACKS',true,'unchanged-api-token',?,'v1')")) {
                s.setObject(1,UUID.randomUUID()); s.setObject(2,user); s.setString(3,sourceKey.encrypt("OwnTracks secret")); s.executeUpdate();
            }
            try (PreparedStatement s = c.prepareStatement("UPDATE users SET ai_settings_encrypted=?,ai_settings_key_id='v1' WHERE id=?")) {
                s.setString(1, sourceKey.encrypt(new ObjectMapper().writeValueAsString(Map.of("openaiApiKey",sourceKey.encrypt("AI secret"), "model","retained-model"))));
                s.setObject(2,user); s.executeUpdate();
            }
            execute(c, "INSERT INTO gps_points(user_id,coordinates,timestamp,source_type) VALUES ('"+user+"',ST_SetSRID(ST_MakePoint(30.5,50.4),4326),now(),'OWNTRACKS')");
        }
        try (Connection c = destination.postgres().connect(destination.postgres().database(), false)) {
            execute(c, "INSERT INTO users(id,email,emailverified,password_hash,role,is_active) VALUES (gen_random_uuid(),'original@destination.test',true,'original-hash','ADMIN',true)");
            setting(c, "backup.password", "destination saved password", true, destinationKey);
            setting(c, "backup.local.path", "/destination/backups", false, destinationKey);
        }
    }

    private NativeBackupContext installation(String prefix) throws Exception {
        String name = "gp_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        Path dir = Files.createDirectories(root.resolve(name));
        byte[] key = new byte[32]; new java.security.SecureRandom().nextBytes(key);
        Path keyPath = Files.writeString(dir.resolve("key"), Base64.getEncoder().encodeToString(key));
        keyPath.toFile().setReadOnly();
        PostgresTarget target = new PostgresTarget(postgres.getJdbcUrl().replace("/test", "/"+name), "postgres", "test-password", "", "", "postgres", "test-"+UUID.randomUUID());
        NativeBackupContext context = new NativeBackupContext(target, dir, root.resolve("bin").toString(), keyPath.toString(), "test-version");
        try (Connection c = target.connect("postgres",true)) { execute(c,"CREATE DATABASE "+PostgresTarget.quote(name)+" TEMPLATE template0"); }
        Flyway.configure().dataSource(target.jdbcUrl(),target.username(),target.password()).locations("classpath:db/migration").load().migrate();
        return context;
    }

    @Test void consistentSnapshotAndAllSecretsSurviveStagingAndAtomicActivation() throws Exception {
        AtomicBoolean writing = new AtomicBoolean(true);
        Thread writer = Thread.ofVirtual().start(() -> {
            try (Connection c = source.postgres().connect(source.postgres().database(), false)) {
                c.setAutoCommit(false);
                for (int i=1; writing.get(); i++) {
                    execute(c,"UPDATE users SET full_name='"+i+"'");
                    execute(c,"UPDATE system_settings SET value='"+i+"' WHERE key='test.snapshot'");
                    c.commit();
                    Thread.sleep(5);
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        try { new NativeDatabaseBackup(source).write(archive,password,deadline()); }
        finally { writing.set(false); writer.join(); }
        RestoreState state = prepare();
        assertOriginalUntouched();
        byte[] destinationKeyFile = Files.readAllBytes(Path.of(destination.keyLocation()));
        try (Connection c = destination.postgres().connect(state.stagingDatabase,false)) {
            assertThat(scalar(c,"SELECT full_name FROM users")).isEqualTo(scalar(c,"SELECT value FROM system_settings WHERE key='test.snapshot'"));
            assertThat(decrypt(c,"SELECT value FROM system_settings WHERE key='test.secret'")).isEqualTo("system secret");
            assertThat(decrypt(c,"SELECT value FROM system_settings WHERE key='backup.password'")).isEqualTo("destination saved password");
            assertThat(scalar(c,"SELECT value FROM system_settings WHERE key='backup.local.path'")).isEqualTo("/destination/backups");
            assertThat(decrypt(c,"SELECT client_secret_encrypted FROM oidc_providers")).isEqualTo("OIDC secret");
            assertThat(decrypt(c,"SELECT headers_json FROM geocoding_provider_configs")).contains("geocoding secret");
            assertThat(decrypt(c,"SELECT payload_encryption_secret_encrypted FROM gps_source_config")).isEqualTo("OwnTracks secret");
            var ai = new ObjectMapper().readTree(decrypt(c,"SELECT ai_settings_encrypted FROM users"));
            assertThat(destinationKey.decrypt(ai.get("openaiApiKey").asText(),"v1")).isEqualTo("AI secret");
            assertThat(ai.get("model").asText()).isEqualTo("retained-model");
            assertThat(scalar(c,"SELECT token FROM gps_source_config")).isEqualTo("unchanged-api-token");
            assertThat(scalar(c,"SELECT ST_AsText(coordinates) FROM gps_points")).isEqualTo("POINT(30.5 50.4)");
            assertThat(scalar(c,"SELECT nextval('gps_points_id_seq')")).isEqualTo("2");
        }
        state.state="ACTIVATING"; destination.journal().write(state);
        DatabaseCutover cutover = new DatabaseCutover(destination.postgres());
        cutover.activate(state);
        assertThat(cutover.isCommitted(state)).isTrue(); // This is also the lost commit-acknowledgement reconciliation.
        assertThat(cutover.currentIdentity(state)).isEqualTo(DatabaseCutover.CurrentIdentity.STAGED);
        try (Connection c=destination.postgres().connect("postgres",true)) {
            assertThat(databaseOid(c,destination.postgres().database())).isEqualTo(state.stagingOid);
            assertThat(databaseOid(c,state.previousDatabase)).isEqualTo(state.originalOid);
            assertThat(scalar(c,"SELECT datallowconn FROM pg_database WHERE oid="+state.originalOid)).isEqualTo("f");
        }
        assertThat(Files.readAllBytes(Path.of(destination.keyLocation()))).isEqualTo(destinationKeyFile);
        assertThat(destination.journal().read().state).isEqualTo("ACTIVATING");
    }

    @Test void wrongPasswordAndSchemaMismatchNeverCreateStagingOrChangeLiveData() throws Exception {
        new NativeDatabaseBackup(source).write(archive,password,deadline());
        RestoreState state = new RestoreState();
        assertThatThrownBy(() -> new NativeDatabaseBackup(destination).prepare(archive,"wrong".toCharArray(),state,deadline(),p -> {})).isInstanceOf(Exception.class);
        assertThat(state.stagingDatabase).isNull();
        try (Connection c=destination.postgres().connect(destination.postgres().database(),false)) { execute(c,"ALTER TABLE users ADD COLUMN incompatible boolean"); }
        assertThatThrownBy(this::prepare).hasMessageContaining("same application database schema");
        assertOriginalUntouched();
    }

    @Test void brokenSecretsAbortPreparationWithoutClearingOriginalValues() throws Exception {
        try (Connection c=source.postgres().connect(source.postgres().database(),false)) { execute(c,"UPDATE oidc_providers SET client_secret_encrypted='invalid-ciphertext'"); }
        new NativeDatabaseBackup(source).write(archive,password,deadline());
        RestoreState state = new RestoreState();
        assertThatThrownBy(() -> new NativeDatabaseBackup(destination).prepare(archive,password,state,deadline(),p -> {})).isInstanceOf(Exception.class);
        new NativeDatabaseBackup(destination).discard(state);
        assertOriginalUntouched();
    }

    @Test void cutoverTerminatesClientsAndPostgresRollsBackBothRenamesOnFailure() throws Exception {
        new NativeDatabaseBackup(source).write(archive,password,deadline());
        RestoreState state=prepare();
        try (Connection client=destination.postgres().connect(destination.postgres().database(),false)) {
            new DatabaseCutover(destination.postgres()).activate(state);
            assertThat(client.isValid(1)).isFalse();
        }

        NativeBackupContext another = installation("rollback");
        long original;
        try (Connection control=another.postgres().connect("postgres",true)) {
            original=databaseOid(control,another.postgres().database());
            control.setAutoCommit(false);
            assertThatThrownBy(() -> {
                execute(control,"ALTER DATABASE "+PostgresTarget.quote(another.postgres().database())+" RENAME TO gp_rollback_probe");
                execute(control,"ALTER DATABASE definitely_missing_database RENAME TO impossible");
            }).isInstanceOf(SQLException.class);
            control.rollback();
        }
        try (Connection control=another.postgres().connect("postgres",true)) {
            assertThat(databaseOid(control,another.postgres().database())).isEqualTo(original);
            assertThat(databaseOid(control,"gp_rollback_probe")).isZero();
        }
    }

    private RestoreState prepare() throws Exception {
        RestoreState state=new RestoreState(); destination.journal().write(state);
        new NativeDatabaseBackup(destination).prepare(archive,password,state,deadline(),p -> {});
        return state;
    }
    private Instant deadline() { return Instant.now().plusSeconds(180); }
    private String decrypt(Connection c,String sql) throws Exception { return destinationKey.decrypt(scalar(c,sql),"v1"); }
    private void assertOriginalUntouched() throws Exception {
        try(Connection c=destination.postgres().connect(destination.postgres().database(),false)) {
            assertThat(scalar(c,"SELECT email FROM users")).isEqualTo("original@destination.test");
            assertThat(decrypt(c,"SELECT value FROM system_settings WHERE key='backup.password'")).isEqualTo("destination saved password");
        }
    }
    private void setting(Connection c,String name,String value,boolean encrypted,KeyCipher key) throws Exception {
        try (PreparedStatement s=c.prepareStatement("INSERT INTO system_settings(key,value,value_type,category,encryption_key_id) VALUES (?,?,?,'backup',?)")) {
            s.setString(1,name); s.setString(2,encrypted?key.encrypt(value):value); s.setString(3,encrypted?"ENCRYPTED":"STRING"); s.setString(4,encrypted?"v1":null); s.executeUpdate();
        }
    }
}
