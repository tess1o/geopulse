package org.github.tess1o.geopulse.admin.backup;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.Tag("unit")
class PgToolsTest {

    @Test
    void configuredDirectoryAlwaysWins() {
        assertThat(PgTools.binaryDirectory("/opt/pg/bin", 18, p -> true)).isEqualTo("/opt/pg/bin");
    }

    @Test
    void picksPackagedClientsMatchingTheServerMajor() {
        Set<Path> alpine = Set.of(Path.of("/usr/libexec/postgresql17"), Path.of("/usr/libexec/postgresql18"));
        assertThat(PgTools.binaryDirectory("", 17, alpine::contains)).isEqualTo("/usr/libexec/postgresql17");
        assertThat(PgTools.binaryDirectory(null, 18, alpine::contains)).isEqualTo("/usr/libexec/postgresql18");

        Set<Path> pgdg = Set.of(Path.of("/usr/pgsql-17/bin"), Path.of("/usr/pgsql-18/bin"));
        assertThat(PgTools.binaryDirectory("", 18, pgdg::contains)).isEqualTo("/usr/pgsql-18/bin");

        assertThat(PgTools.binaryDirectory(" ", 17, Path.of("/usr/lib/postgresql/17/bin")::equals)).isEqualTo("/usr/lib/postgresql/17/bin");
    }

    @Test
    void fallsBackToPathWhenNoMatchingClientsAreInstalled() {
        assertThat(PgTools.binaryDirectory("", 16, Path.of("/usr/libexec/postgresql17")::equals)).isEmpty();
    }
}
