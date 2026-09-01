package org.github.tess1o.geopulse.admin.backup;

import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * No shell, no credentials in arguments, bounded stderr, and a deadline that kills hung clients.
 */
public final class PgTools {
    private final NativeBackupContext context;

    public PgTools(NativeBackupContext context) {
        this.context = context;
    }

    public int major(String tool) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        run(tool, List.of("--version"), context.postgres().database(), false, bytes, Instant.now().plusSeconds(15));
        var match = java.util.regex.Pattern.compile("PostgreSQL\\) (\\d+)").matcher(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
        if (!match.find()) throw new IOException("Cannot determine " + tool + " version");
        return Integer.parseInt(match.group(1));
    }

    public void run(String tool, List<String> arguments, String database, boolean admin, OutputStream output, Instant deadline) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(context.binaryDirectory().isBlank() ? tool : Path.of(context.binaryDirectory(), tool).toString());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> env = builder.environment();
        // Do not inherit service files/password files that could override the selected target.
        env.keySet().removeIf(k -> k.startsWith("PG"));
        Properties props = context.postgres().urlProperties();
        env.put("PGHOST", props.getProperty("PGHOST", "localhost"));
        env.put("PGPORT", props.getProperty("PGPORT", "5432"));
        env.put("PGDATABASE", database);
        env.put("PGUSER", admin ? context.postgres().adminUser() : context.postgres().username());
        env.put("PGPASSWORD", admin ? context.postgres().adminPassword() : context.postgres().password());
        env.put("PGCONNECT_TIMEOUT", "15");
        env.put("PGAPPNAME", "GeoPulse-backup-tool");
        for (var entry : Map.of("sslmode", "PGSSLMODE", "sslrootcert", "PGSSLROOTCERT", "sslcert", "PGSSLCERT", "sslkey", "PGSSLKEY").entrySet()) {
            if (props.getProperty(entry.getKey()) != null) env.put(entry.getValue(), props.getProperty(entry.getKey()));
        }
        if ("true".equals(props.getProperty("ssl")) && !env.containsKey("PGSSLMODE"))
            env.put("PGSSLMODE", "verify-full");
        long millis = java.time.Duration.between(Instant.now(), deadline).toMillis();
        if (millis <= 0) throw new IOException("Backup operation timed out");
        Process process = builder.start();
        process.getOutputStream().close();
        try (ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory())) {
            var timeout = timer.schedule(process::destroyForcibly, millis, TimeUnit.MILLISECONDS);
            Thread errors = Thread.ofVirtual().start(() -> {
                // SQL errors can contain plaintext row values. Do not log or return raw stderr.
                try (InputStream input = process.getErrorStream()) {
                    input.transferTo(OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                }
            });
            try {
                try (InputStream in = process.getInputStream()) {
                    in.transferTo(output);
                }
                int exit = process.waitFor();
                errors.join();
                if (Instant.now().isAfter(deadline)) throw new IOException("Backup operation timed out");
                if (exit != 0)
                    throw new IOException(tool + " failed (exit " + exit + "). Check database permissions, tool compatibility, and free disk space.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Backup operation interrupted", e);
            } finally {
                timeout.cancel(false);
                process.destroyForcibly();
            }
        }
    }
}
