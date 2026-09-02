package org.github.tess1o.geopulse.admin.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.Tag("unit")
class RestoreJournalTest {
    @TempDir Path directory;
    @Test void persistsReadinessAndOidsWithoutSecrets() throws Exception {
        RestoreJournal journal=new RestoreJournal(directory);
        RestoreState state=new RestoreState();
        state.fileName="backup.gpb";
        state.originalDatabase="geopulse";
        state.stagingDatabase="gp_restore_"+state.operationId.replace("-", "");
        state.previousDatabase="gp_previous_"+state.operationId.replace("-", "");
        state.originalOid=42;
        state.stagingOid=43;
        state.transition(RestoreOperationState.ACTIVATING, RestorePhase.CUTOVER, null);
        state.transition(RestoreOperationState.SWAPPED_PENDING_RESTART, RestorePhase.RESTARTING, null);
        journal.write(state);
        RestoreState recovered=new RestoreJournal(directory).read();
        assertThat(recovered.blocked()).isTrue();
        assertThat(recovered.originalOid).isEqualTo(42);
        assertThat(recovered.stagingOid).isEqualTo(43);
        assertThat(Files.readString(directory.resolve("restore-state.json"))).doesNotContain("password", "sourceKey");
        journal.archive(recovered);
        assertThat(directory.resolve("history").resolve(state.operationId + ".json")).isRegularFile();
    }
    @Test void corruptJournalFailsClosed() throws Exception {
        Files.writeString(directory.resolve("restore-state.json"),"{\"state\":");
        assertThatThrownBy(()->new RestoreJournal(directory).read()).isInstanceOf(java.io.IOException.class);
    }

    @Test void inconsistentDatabaseIdentityFailsClosed() throws Exception {
        RestoreState state=new RestoreState();
        state.fileName="backup.gpb";
        state.originalDatabase="geopulse";
        state.stagingDatabase="unrelated_database";
        state.previousDatabase="gp_previous_"+state.operationId.replace("-", "");
        state.originalOid=42;
        state.stagingOid=43;
        state.transition(RestoreOperationState.ACTIVATING, RestorePhase.CUTOVER, null);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("restore-state.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(state));
        assertThatThrownBy(()->new RestoreJournal(directory).read()).isInstanceOf(java.io.IOException.class);
    }
}
