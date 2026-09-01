package org.github.tess1o.geopulse.admin.backup;

import org.postgresql.Driver;
import java.net.URI;
import java.sql.*;
import java.util.Properties;

/** Connection information only. It never reads environment variables or initializes Quarkus. */
public record PostgresTarget(String jdbcUrl, String username, String password, String administratorUsername,
                             String administratorPassword, String maintenanceDatabase, String instanceId) {
    public Properties urlProperties() {
        Properties props = Driver.parseURL(jdbcUrl, new Properties());
        if (props == null || props.getProperty("PGHOST", "").contains(","))
            throw new IllegalArgumentException("Backup requires a direct, single-server PostgreSQL JDBC URL");
        return props;
    }
    public String database() { return urlProperties().getProperty("PGDBNAME"); }
    public String url(String database) {
        try {
            URI uri = URI.create(jdbcUrl.substring(5));
            return "jdbc:" + new URI(uri.getScheme(), uri.getAuthority(), "/" + database, uri.getQuery(), null).toASCIIString();
        } catch (Exception e) { throw new IllegalArgumentException("Invalid PostgreSQL JDBC URL", e); }
    }
    public String adminUser() { return administratorUsername == null || administratorUsername.isBlank() ? username : administratorUsername; }
    public String adminPassword() { return administratorUsername == null || administratorUsername.isBlank() ? password : administratorPassword; }
    public Connection connect(String database, boolean administrator) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", administrator ? adminUser() : username);
        props.setProperty("password", administrator ? adminPassword() : password);
        props.setProperty("ApplicationName", instanceId);
        props.setProperty("connectTimeout", "15");
        return DriverManager.getConnection(url(database), props);
    }
    public long lockKey() { return 0x4750420000000000L ^ Integer.toUnsignedLong(database().hashCode()); }
    public static String quote(String identifier) {
        if (identifier == null || identifier.isEmpty() || identifier.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid SQL identifier");
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
