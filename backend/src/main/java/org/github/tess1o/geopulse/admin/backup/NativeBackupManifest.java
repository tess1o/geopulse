package org.github.tess1o.geopulse.admin.backup;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.*;

@RegisterForReflection
public class NativeBackupManifest {
    public int formatVersion = 1;
    public String applicationVersion;
    public String createdAt;
    public int postgresMajor;
    public Map<String,String> extensions = new TreeMap<>();
    public List<List<String>> migrations = new ArrayList<>();
    public List<List<String>> schema = new ArrayList<>();
    public String schemaFingerprint;
    public String dumpSha256;
    public String sourceKeyFingerprint;
}
