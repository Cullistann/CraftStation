package core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAnalyzer {

    private static final Pattern EXCEPTION_PATTERN = Pattern
            .compile("([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)*(?:Exception|Error))(?::\\s*(.*))?");
    // Minecraft-server-specific TPS patterns:
    // 1. Purpur/Paper "TPS from last..." with §-color codes: "§a*20.0" or "§a20.0"
    // 2. Purpur/Paper "TPS from last 5s, 1m, 5m, 15m: 20,0, ..."
    // 3. Direct "TPS: 20.0" or "TPS:20.0" format
    // 4. Spark/other plugins that print standalone "20.0 TPS"
    private static final Pattern TPS_COLORED_PATTERN = Pattern.compile(
            "§[a-f0-9r]\\*?(\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TPS_HISTORY_PATTERN = Pattern.compile(
            "TPS\\s+from\\s+last[^:]*:\\s*(\\d+(?:[.,]\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TPS_DIRECT_PATTERN = Pattern.compile(
            "TPS[:\\s]+\\*?(\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TPS_STANDALONE_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s+TPS", Pattern.CASE_INSENSITIVE);

    public static String extractPlayerJoined(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        if (line.contains("joined the game")) {
            return extractPlayerName(line, "joined the game");
        }
        return null;
    }

    public static String extractPlayerLeft(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        if (line.contains("left the game")) {
            return extractPlayerName(line, "left the game");
        }
        return null;
    }

    private static String extractPlayerName(String line, String marker) {
        int markerIdx = line.indexOf(marker);
        if (markerIdx < 0) {
            return null;
        }
        String before = line.substring(0, markerIdx).trim();
        int colonIdx = before.lastIndexOf("]: ");
        String name = null;
        if (colonIdx >= 0) {
            name = before.substring(colonIdx + 3).trim();
        } else {
            int spaceIdx = before.lastIndexOf(" ");
            if (spaceIdx >= 0) {
                name = before.substring(spaceIdx + 1).trim();
            } else if (!before.isEmpty()) {
                name = before;
            }
        }

        if (name == null || name.isBlank()) {
            return null;
        }

        // Güvenlik: Sohbet manipülasyonunu (Chat Spoofing) engelle
        if (name.contains("<") || name.contains(">") || name.contains(":")) {
            return null;
        }
        return name;
    }

    public static Double extractTps(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String upper = line.toUpperCase(java.util.Locale.ROOT);
        if (!upper.contains("TPS") && !line.contains("§")) {
            return null;
        }

        try {
            // Priority 1: Colored TPS output — only valid alongside TPS keyword or in valid range
            Matcher coloredMatcher = TPS_COLORED_PATTERN.matcher(line);
            if (coloredMatcher.find()) {
                String tpsStr = coloredMatcher.group(1).replace(",", ".");
                double tps = Double.parseDouble(tpsStr);
                // §-only lines (no TPS keyword) must be in valid MC TPS range [0, 25]
                if (upper.contains("TPS") || (tps >= 0.0 && tps <= 25.0)) {
                    return tps;
                }
            }

            // Priority 2: Purpur/Paper history format
            Matcher historyMatcher = TPS_HISTORY_PATTERN.matcher(line);
            if (historyMatcher.find()) {
                String tpsStr = historyMatcher.group(1).replace(",", ".");
                return Double.parseDouble(tpsStr);
            }

            // Priority 3: Direct format ("TPS: 20.0" or "TPS:20.0")
            Matcher directMatcher = TPS_DIRECT_PATTERN.matcher(line);
            if (directMatcher.find()) {
                String tpsStr = directMatcher.group(1).replace(",", ".");
                return Double.parseDouble(tpsStr);
            }

            // Priority 4: Standalone format ("20.0 TPS")
            Matcher standaloneMatcher = TPS_STANDALONE_PATTERN.matcher(line);
            if (standaloneMatcher.find()) {
                String tpsStr = standaloneMatcher.group(1).replace(",", ".");
                return Double.parseDouble(tpsStr);
            }
        } catch (NumberFormatException e) {
            // Geçersiz sayı formatı — TPS olarak yorumlanamaz
        }
        return null;
    }

    public static ErrorAnalysis analyzeError(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        if (line.contains("ERROR") || line.contains("WARN") || line.contains("Exception") || line.contains("Error")) {
            // Forge/NeoForge mod loading s\u0131ras\u0131nda beklenen g\u00fcr\u00fclt\u00fc \u2014 ger\u00e7ek hata de\u011fil.
            // Bu sat\u0131rlar hata listesini kirletiyor; filtrele.
            if (isForgeLoadingNoise(line)) {
                return null;
            }

            Matcher matcher = EXCEPTION_PATTERN.matcher(line);
            if (matcher.find()) {
                String exceptionName = matcher.group(1);
                String message = matcher.group(2);

                ErrorAnalysis.Severity severity = ErrorAnalysis.Severity.WARNING;
                if (line.contains("ERROR") || exceptionName.contains("Error")) {
                    severity = ErrorAnalysis.Severity.ERROR;
                } else if (line.contains("WARN")) {
                    severity = ErrorAnalysis.Severity.WARNING;
                }

                return new ErrorAnalysis(exceptionName, message, severity);
            }
        }
        return null;
    }

    /**
     * Forge/NeoForge mod y\u00fckleme s\u0131ras\u0131nda normal olan ama ERROR/WARN i\u00e7eren sat\u0131rlar\u0131
     * filtreler. Bu sat\u0131rlar ger\u00e7ek bir sorun de\u011fil; sunucunun \u00e7\u00f6kmesiyle ili\u015fkisizdir.
     */
    private static boolean isForgeLoadingNoise(String line) {
        // Mixin transformation uyar\u0131lar\u0131 (Forge/NeoForge/Fabric ortak)
        if (line.contains("mixin") || line.contains("Mixin")) return true;
        // ASM/bytecode transformation
        if (line.contains("Transformation error") || line.contains("transform error")) return true;
        // DataPack y\u00fckleme uyar\u0131lar\u0131 (vanilla + modded)
        if (line.contains("DataPack") || line.contains("data pack")) return true;
        // Class y\u00fckleme / reflection uyar\u0131lar\u0131
        if (line.contains("ClassLoading") || line.contains("InaccessibleObjectException")) return true;
        // Forge mod uyumluluk uyar\u0131lar\u0131
        if (line.contains("ModLoadingError") && line.contains("WARN")) return true;
        // SLF4J/logging framework init mesajlar\u0131
        if (line.contains("SLF4J") || line.contains("log4j")) return true;
        // IllegalAccessWarning (Java 9+ module system)
        if (line.contains("WARNING: An illegal reflective access")) return true;
        if (line.contains("WARNING: Please consider reporting")) return true;
        if (line.contains("WARNING: Use --illegal-access")) return true;
        return false;
    }



    public record ErrorAnalysis(String exceptionName, String message, Severity severity) {
        public enum Severity {
            INFO, WARNING, ERROR
        }
    }
}
