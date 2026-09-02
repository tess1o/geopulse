package org.github.tess1o.geopulse.admin.backup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.junit.jupiter.api.Tag("unit")
class RestoreStateTest {
    @Test
    void acceptsDocumentedLifecycleAndRejectsIllegalTransitions() {
        RestoreState state = readyState();
        state.transition(RestoreOperationState.ACTIVATING, RestorePhase.CUTOVER, null);
        state.transition(RestoreOperationState.SWAPPED_PENDING_RESTART, RestorePhase.RESTARTING, null);
        state.transition(RestoreOperationState.COMPLETED, RestorePhase.COMPLETED, null);
        assertThat(state.blocked()).isFalse();
        assertThat(state.progress).isEqualTo(100);
        assertThatThrownBy(() -> state.transition(RestoreOperationState.ACTIVATING, RestorePhase.CUTOVER, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesOperationDerivedNamesAndDistinctOids() {
        RestoreState state = readyState();
        state.validateDatabaseIdentity("geopulse", true);
        state.stagingOid = state.originalOid;
        assertThatThrownBy(() -> state.validateDatabaseIdentity("geopulse", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsStatePhaseAndProgressInconsistency() {
        RestoreState state = readyState();
        state.state = RestoreOperationState.COMPLETED;
        state.phase = RestorePhase.CUTOVER;
        state.progress = RestorePhase.CUTOVER.progress();
        assertThatThrownBy(state::validateDurableShape).isInstanceOf(IllegalArgumentException.class);
    }

    private RestoreState readyState() {
        RestoreState state = new RestoreState();
        state.fileName = "backup.gpb";
        String suffix = state.operationId.replace("-", "");
        state.originalDatabase = "geopulse";
        state.stagingDatabase = "gp_restore_" + suffix;
        state.previousDatabase = "gp_previous_" + suffix;
        state.originalOid = 10;
        state.stagingOid = 11;
        return state;
    }
}
