package org.github.tess1o.geopulse.admin.service;

import io.quarkus.runtime.Startup;
import jakarta.annotation.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.github.tess1o.geopulse.admin.backup.*;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupStatusDto;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Startup
@ApplicationScoped
public class BackupMaintenanceService {
    @Inject Config config;
    private NativeBackupContext context;
    private Connection coordination;
    private boolean exclusive;
    private boolean operationLocked;
    private boolean backupRunning;
    private RestoreState restore;
    private AdminBackupStatusDto backupStatus = AdminBackupStatusDto.builder().status("idle").build();

    @PostConstruct
    void initialize() throws Exception {
        String instanceId = "GeoPulse-" + UUID.randomUUID();
        PostgresTarget postgres = new PostgresTarget(value("quarkus.datasource.jdbc.url", ""), value("quarkus.datasource.username", ""),
                value("quarkus.datasource.password", ""), value("geopulse.backup.restore.username", ""),
                value("geopulse.backup.restore.password", ""), value("geopulse.backup.maintenance-database", "postgres"), instanceId);
        context = new NativeBackupContext(postgres, Path.of(value("geopulse.backup.work-path", "/data/geopulse-backups/.work")),
                value("geopulse.backup.binary-directory", ""), value("geopulse.ai.encryption.key.location", "file:/app/keys/ai-encryption-key.txt"),
                value("quarkus.application.version", "unknown"));
        restore = context.journal().read();
        reconcileStartup();
        coordination = postgres.connect(postgres.maintenanceDatabase(), true);
        if (!instanceLock("pg_try_advisory_lock_shared"))
            throw new IllegalStateException("Another GeoPulse backend is activating a restore");
    }

    private String value(String key, String fallback) { return config.getOptionalValue(key, String.class).orElse(fallback); }

    private void reconcileStartup() throws Exception {
        if (restore == null) return;
        if (Set.of("ACTIVATING", "SWAPPED_PENDING_RESTART").contains(restore.state)) {
            {
                DatabaseCutover.CurrentIdentity identity = new DatabaseCutover(context.postgres()).currentIdentity(restore);
                if (identity == DatabaseCutover.CurrentIdentity.STAGED) {
                    restore.state="COMPLETED"; restore.phase="completed"; restore.progress=100; restore.error=null;
                } else if (identity == DatabaseCutover.CurrentIdentity.ORIGINAL) {
                    restore.state="ACTIVATION_RETRYABLE"; restore.phase="activation-rolled-back";
                    restore.error="Activation did not commit. The original database is active and the prepared restore can be retried.";
                } else {
                    restore.state="ACTIVATION_FAILED"; restore.phase="identity-mismatch";
                    restore.error="The configured database identity does not match the recorded original or staged database.";
                }
                context.journal().write(restore);
            }
        } else if ("PREPARING".equals(restore.state)) {
            try { new NativeDatabaseBackup(context).discard(restore); } catch (Exception ignored) { }
            RestoreJournal.removeTree(context.workPath().resolve(restore.operationId + ".extract"));
            restore.state="PREPARATION_FAILED"; restore.phase="interrupted";
            restore.error="Restore preparation was interrupted. The original database remains active.";
            context.journal().write(restore);
        }
    }

    public NativeBackupContext context() { return context; }
    public synchronized boolean tryStartBackup(String operation) {
        if (backupRunning || restoreActive()) return false;
        try { if (!acquireOperation()) return false; }
        catch (SQLException e) { throw new IllegalStateException("Cannot coordinate backup with other instances", e); }
        backupRunning=true;
        backupStatus=AdminBackupStatusDto.builder().backupRunning(true).status("running").operation(operation).phase("snapshot")
                .message("Creating encrypted PostgreSQL backup").startedAt(Instant.now()).build();
        return true;
    }
    public synchronized boolean tryStartRestore(String operation, String fileName) {
        if (backupRunning || restoreActive()) return false;
        try {
            if (!acquireOperation()) return false;
            context.journal().archive(restore);
            RestoreState next=new RestoreState(); next.fileName=fileName; next.phase="upload";
            context.journal().write(next); restore=next; return true;
        } catch(Exception e) { releaseOperation(); throw new IllegalStateException("Cannot start restore",e); }
    }
    public synchronized boolean beginActivation() {
        if (restore == null || !Set.of("PREPARING", "ACTIVATION_RETRYABLE").contains(restore.state)) return false;
        try {
            instanceLock("pg_advisory_unlock_shared");
            if (!instanceLock("pg_try_advisory_lock")) {
                instanceLock("pg_advisory_lock_shared");
                restore.state="ACTIVATION_RETRYABLE"; restore.error="Stop all other backend instances before retrying activation."; persist();
                releaseOperation();
                return false;
            }
            exclusive=true; restore.state="ACTIVATING"; restore.phase="cutover"; restore.progress=95; restore.error=null;
            persist(); return true;
        } catch(Exception e) { throw new IllegalStateException("Cannot acquire exclusive restore coordination",e); }
    }
    public synchronized void progress(String phase) {
        if(restore==null) return; restore.phase=phase;
        restore.progress=switch(phase){case "preflight"->20;case "restoring"->35;case "secrets"->75;case "validating"->90;default->5;}; persist();
    }
    public synchronized void swapped() { restore.state="SWAPPED_PENDING_RESTART";restore.phase="restarting";restore.progress=100;persist(); }
    public synchronized void activationRetryable(String error) { restore.state="ACTIVATION_RETRYABLE";restore.phase="activation-rolled-back";restore.error=error;persist(); }
    public synchronized void abortActivation(String error) { activationRetryable(error); releaseLocks(); }
    public synchronized boolean acquireRetry() {
        if (restore==null || !"ACTIVATION_RETRYABLE".equals(restore.state)) return false;
        try { return acquireOperation(); } catch(SQLException e) { throw new IllegalStateException("Cannot coordinate activation retry",e); }
    }
    public synchronized void activationFailed(String error) { restore.state="ACTIVATION_FAILED";restore.phase="activation-failed";restore.error=error;persist(); }
    public synchronized void discarded() { restore.state="DISCARDED";restore.phase="discarded";persist();releaseLocks(); }
    public synchronized RestoreState restoreState(){return restore;}
    public synchronized void updateBackupFile(String name){backupStatus.setFileName(name);}
    public synchronized void finishSuccess(String name,Long size){releaseOperation();backupRunning=false;backupStatus.setBackupRunning(false);backupStatus.setStatus("completed");backupStatus.setFileName(name);backupStatus.setSizeBytes(size);backupStatus.setProgressPercent(100);backupStatus.setCompletedAt(Instant.now());}
    public synchronized void finishFailure(String error){
        if(restore!=null && "PREPARING".equals(restore.state)){restore.state="PREPARATION_FAILED";restore.phase="failed";restore.error=error;persist();releaseLocks();}
        else {releaseOperation();backupRunning=false;backupStatus.setBackupRunning(false);backupStatus.setStatus("failed");backupStatus.setError(error);}
    }
    public synchronized boolean isRestoreRunning(){return restore!=null&&Set.of("PREPARING","ACTIVATING","SWAPPED_PENDING_RESTART").contains(restore.state);}
    public synchronized boolean isRestoreBlocked(){return restore!=null&&restore.blocked();}
    private boolean restoreActive(){return restore!=null&&Set.of("PREPARING","ACTIVATING","SWAPPED_PENDING_RESTART","ACTIVATION_RETRYABLE","ACTIVATION_FAILED").contains(restore.state);}
    public synchronized Map<String,Object> publicStatus(){
        String state=restore==null?"IDLE":restore.state;
        return Map.of("state",state,"blocked",isRestoreBlocked(),"warning","PREPARING".equals(state),
                "restarting","SWAPPED_PENDING_RESTART".equals(state),"message",restore==null?"GeoPulse is available.":restore.message(),
                "backupCreatedAt",restore==null||restore.backupCreatedAt==null?"":restore.backupCreatedAt,
                "completedAt",restore!=null&&"COMPLETED".equals(state)?restore.updatedAt:"");
    }
    public synchronized AdminBackupStatusDto getStatus(){
        if(restore==null||backupRunning)return backupStatus;
        return AdminBackupStatusDto.builder().operationId(restore.operationId).state(restore.state).status(restore.state.toLowerCase(Locale.ROOT))
                .stagingDatabase(restore.stagingDatabase).previousDatabase(restore.previousDatabase)
                .operation("restore").restoreRunning(isRestoreRunning()).restartRequired("SWAPPED_PENDING_RESTART".equals(restore.state))
                .environmentBlocked(isRestoreBlocked()).restoreRequired("ACTIVATION_FAILED".equals(restore.state))
                .fileName(restore.fileName).phase(restore.phase).progressPercent(restore.progress).message(restore.message()).error(restore.error)
                .startedAt(Instant.parse(restore.startedAt)).build();
    }
    private void persist(){try{context.journal().write(restore);}catch(IOException e){throw new IllegalStateException("Cannot durably persist restore state",e);}}
    private boolean acquireOperation() throws SQLException { if(operationLocked)return false; operationLocked=operationLock("pg_try_advisory_lock"); return operationLocked; }
    private void releaseOperation(){if(!operationLocked)return;try{operationLock("pg_advisory_unlock");}catch(SQLException ignored){}operationLocked=false;}
    private boolean operationLock(String fn)throws SQLException{return advisory(fn,context.postgres().lockKey()^0x0100000000000000L);}
    private boolean instanceLock(String fn)throws SQLException{return advisory(fn,context.postgres().lockKey());}
    private boolean advisory(String fn,long key)throws SQLException{try(PreparedStatement s=coordination.prepareStatement("SELECT "+fn+"(?)")){s.setLong(1,key);try(ResultSet r=s.executeQuery()){r.next();return fn.startsWith("pg_advisory_unlock")||fn.equals("pg_advisory_lock_shared")||r.getBoolean(1);}}}
    private void releaseLocks(){releaseOperation();if(exclusive){try{instanceLock("pg_advisory_unlock");instanceLock("pg_advisory_lock_shared");}catch(SQLException ignored){}exclusive=false;}}
    @PreDestroy void close()throws SQLException{if(coordination!=null)coordination.close();}
}
