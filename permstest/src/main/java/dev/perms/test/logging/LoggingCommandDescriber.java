package dev.perms.test.logging;


/**
 * Builds stable lifetime-log action descriptions for shell commands.
 */
public final class LoggingCommandDescriber {
    private LoggingCommandDescriber() {}

    public static String describe(String cmd) {
        try {
            String s = cmd == null ? "" : cmd.trim();
            if (s.isEmpty()) return null;
            String[] p = s.split("\\s+");
            if (p.length == 0) return null;

            if ("pm".equals(p[0])) {
                if (p.length >= 3 && "enable".equals(p[1])) {
                    return "Enable package: " + p[2];
                }
                if (p.length >= 5 && "disable-user".equals(p[1]) && "--user".equals(p[2])) {
                    return "Disable package (user " + p[3] + "): " + p[4];
                }
                if (p.length >= 4 && "grant".equals(p[1])) {
                    return "Grant permission " + p[3] + " to " + p[2];
                }
                if (p.length >= 4 && "revoke".equals(p[1])) {
                    return "Revoke permission " + p[3] + " from " + p[2];
                }
                if (p.length >= 3 && "clear".equals(p[1])) {
                    return "Clear app data: " + p[2];
                }
            }

            if ("appops".equals(p[0])) {
                if (p.length >= 5 && "set".equals(p[1])) {
                    return "Set app-op " + p[3] + "=" + p[4] + " for " + p[2];
                }
            }

            if ("am".equals(p[0])) {
                if (p.length >= 3 && "force-stop".equals(p[1])) {
                    return "Force-stop package: " + p[2];
                }
                if (p.length >= 4 && "start".equals(p[1]) && "-n".equals(p[2])) {
                    return "Start component: " + p[3];
                }
            }

            if ("dumpsys".equals(p[0])) {
                if (p.length >= 2 && "package".equals(p[1])) {
                    if (p.length >= 3) return "Dump package info: " + p[2];
                    return "Dump package info";
                }
                if (p.length >= 2) return "dumpsys " + p[1];
                return "dumpsys";
            }

            if ("logcat".equals(p[0])) {
                boolean clear = (p.length >= 2 && "-c".equals(p[1]));
                if (clear) return "Clear logcat buffers";
                boolean errorsOnly = false;
                for (String t : p) {
                    if (t != null && t.contains("*:E")) {
                        errorsOnly = true;
                        break;
                    }
                }
                if (errorsOnly) return "Read logcat (errors only)";
                return "Read logcat";
            }

            if ("id".equals(p[0])) {
                return "Query current shell identity (id)";
            }

            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
