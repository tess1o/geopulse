package org.github.tess1o.geopulse.shared.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class ProcessIdentity {

    private static final Path PROC_STATUS = Paths.get("/proc/self/status");

    private ProcessIdentity() {
    }

    public static String describe() {
        Identity identity = readIdentity();
        String user = identity.user != null ? identity.user : "unknown";
        String uid = identity.uid != null ? identity.uid.toString() : "?";
        String gid = identity.gid != null ? identity.gid.toString() : "?";
        return "user=" + user + " uid=" + uid + " gid=" + gid;
    }

    private static Identity readIdentity() {
        String user = System.getProperty("user.name", "unknown");
        Long uid = null;
        Long gid = null;

        if (Files.isRegularFile(PROC_STATUS)) {
            try {
                List<String> lines = Files.readAllLines(PROC_STATUS);
                for (String line : lines) {
                    if (line.startsWith("Uid:")) {
                        uid = parseEffectiveId(line);
                    } else if (line.startsWith("Gid:")) {
                        gid = parseEffectiveId(line);
                    }
                    if (uid != null && gid != null) {
                        break;
                    }
                }
            } catch (IOException ignored) {
                // Fall back to user.name only when /proc is unavailable.
            }
        }

        return new Identity(user, uid, gid);
    }

    private static Long parseEffectiveId(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 3) {
            return parseLong(parts[2]);
        }
        if (parts.length >= 2) {
            return parseLong(parts[1]);
        }
        return null;
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Identity(String user, Long uid, Long gid) {
    }
}
