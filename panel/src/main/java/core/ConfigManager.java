package core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern configuration manager with Java 25 features.
 * Thread-safe with caching and validation.
 */
public final class ConfigManager {

    // Singleton instance
    private static volatile ConfigManager instance;

    private final String serverDir;
    private final Map<String, Map<String, String>> configCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000L; // 30 seconds

    /**
     * Private constructor for singleton pattern.
     */
    private ConfigManager(String serverDir) {
        this.serverDir = Objects.requireNonNull(serverDir, "serverDir cannot be null");
    }

    /**
     * Singleton factory method.
     *
     * @throws IllegalStateException if already initialized with a different serverDir
     */
    public static ConfigManager getInstance(String serverDir) {
        String resolved = Objects.requireNonNull(serverDir, "serverDir cannot be null");
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager(resolved);
                }
            }
        } else if (!instance.serverDir.equals(resolved)) {
            throw new IllegalStateException(
                    "ConfigManager zaten '" + instance.serverDir + "' için başlatılmış. Yeni istek: '" + resolved + "'");
        }
        return instance;
    }

    /**
     * Resets the singleton instance (for testing).
     */
    static void resetInstance() {
        instance = null;
    }

    /**
     * Loads properties from a file with caching.
     */
    public synchronized Map<String, String> loadProperties(String filename) {
        String cacheKey = "props:" + filename;
        long now = System.currentTimeMillis();

        Map<String, String> cachedProperties = configCache.compute(cacheKey, (key, currentCache) -> {
            Long timestamp = cacheTimestamps.get(key);
            if (currentCache != null && timestamp != null && now - timestamp < CACHE_TTL_MS) {
                return currentCache;
            }

            Map<String, String> properties = new LinkedHashMap<>();
            Path path = Path.of(serverDir, filename);

            try {
                if (!Files.exists(path)) {
                    path = Path.of(serverDir, "config", filename);
                }

                if (Files.exists(path)) {
                    List<String> lines = readAllLinesWithFallback(path);

                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }

                        int eqIndex = trimmed.indexOf('=');
                        if (eqIndex > 0) {
                            String k = trimmed.substring(0, eqIndex).trim();
                            String v = trimmed.substring(eqIndex + 1).trim();

                            if (isValidConfigKey(k)) {
                                properties.put(k, v);
                            }
                        }
                    }

                    cacheTimestamps.put(key, now);
                }
            } catch (IOException e) {
                System.err.println("Config read error for " + filename + ": " + e.getMessage());
            }

            return properties;
        });

        return new LinkedHashMap<>(cachedProperties);
    }

    /**
     * Saves properties to a file, preserving comments and formatting.
     */
    public synchronized void saveProperties(String filename, Map<String, String> properties) throws IOException {
        Path path = Path.of(serverDir, filename);
        if (!Files.exists(path) && Files.exists(Path.of(serverDir, "config", filename))) {
            path = Path.of(serverDir, "config", filename);
        }

        // Read existing lines to preserve comments
        List<String> existingLines = Files.exists(path)
                ? readAllLinesWithFallback(path)
                : new ArrayList<>();

        List<String> newLines = new ArrayList<>();
        Set<String> writtenKeys = new HashSet<>();

        for (String line : existingLines) {
            String trimmed = line.trim();

            // Preserve comments and empty lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                newLines.add(line);
                continue;
            }

            int eqIndex = trimmed.indexOf('=');
            if (eqIndex > 0) {
                String key = trimmed.substring(0, eqIndex).trim();

                if (properties.containsKey(key)) {
                    // Update existing property
                    newLines.add(key + "=" + properties.get(key));
                    writtenKeys.add(key);
                } else {
                    // Keep property that's not being updated
                    newLines.add(line);
                }
            } else {
                // Keep lines without equals sign
                newLines.add(line);
            }
        }

        // Add new properties that weren't in the original file
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!writtenKeys.contains(entry.getKey())) {
                newLines.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        // Write to file
        Files.write(path, newLines, java.nio.charset.StandardCharsets.UTF_8);

        // Invalidate cache
        String cacheKey = "props:" + filename;
        configCache.remove(cacheKey);
        cacheTimestamps.remove(cacheKey);
    }

    /**
     * Gets a single property value with default fallback.
     */
    public String getProperty(String filename, String key, String defaultValue) {
        Map<String, String> properties = loadProperties(filename);
        return properties.getOrDefault(key, defaultValue);
    }

    /**
     * Sets a single property value.
     */
    public synchronized void setProperty(String filename, String key, String value) throws IOException {
        Map<String, String> properties = loadProperties(filename);
        properties.put(key, value);
        saveProperties(filename, properties);
    }

    /**
     * Loads raw text from a file.
     */
    public synchronized String loadRawText(String filename) {
        Path path = Path.of(serverDir, filename);

        try {
            if (!Files.exists(path)) {
                // Try config directory
                path = Path.of(serverDir, "config", filename);
            }

            if (Files.exists(path)) {
                return readStringWithFallback(path);
            }
        } catch (IOException e) {
            System.err.println("Text read error for " + filename + ": " + e.getMessage());
        }

        return "";
    }

    /**
     * Saves raw text to a file.
     */
    public synchronized void saveRawText(String filename, String content) throws IOException {
        Path path = Path.of(serverDir, filename);
        if (!Files.exists(path) && Files.exists(Path.of(serverDir, "config", filename))) {
            path = Path.of(serverDir, "config", filename);
        }
        Files.writeString(path, content, java.nio.charset.StandardCharsets.UTF_8);

        // Invalidate cache if this was a properties file
        if (filename.endsWith(".properties") || filename.endsWith(".yml") || filename.endsWith(".yaml")) {
            String cacheKey = "props:" + filename;
            configCache.remove(cacheKey);
            cacheTimestamps.remove(cacheKey);
        }
    }

    /**
     * Gets panel-specific property.
     */
    public String getPanelProperty(String key, String defaultValue) {
        return getProperty("panel.properties", key, defaultValue);
    }

    /**
     * Sets panel-specific property.
     */
    public void setPanelProperty(String key, String value) throws IOException {
        setProperty("panel.properties", key, value);
    }

    /**
     * Validates a configuration key format.
     * Allows standard Minecraft server.properties keys like rcon.password,
     * level-seed, resource-pack-sha1, etc.
     */
    private boolean isValidConfigKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 256) {
            return false;
        }

        // Only allow safe characters: letters, digits, hyphen, underscore, dot
        for (char c : key.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_' && c != '.') {
                return false;
            }
        }

        return true;
    }

    /**
     * Clears the configuration cache.
     */
    public void clearCache() {
        configCache.clear();
        cacheTimestamps.clear();
    }

    /**
     * Gets cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("cacheSize", configCache.size());
        stats.put("cachedFiles", new ArrayList<>(configCache.keySet()));

        List<Map<String, Object>> fileStats = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : configCache.entrySet()) {
            Map<String, Object> fileStat = new LinkedHashMap<>();
            fileStat.put("filename", entry.getKey());
            fileStat.put("propertyCount", entry.getValue().size());
            fileStat.put("cachedSince", cacheTimestamps.get(entry.getKey()));
            fileStats.add(fileStat);
        }

        stats.put("fileDetails", fileStats);
        return stats;
    }

    /**
     * Gets server RAM allocation from startup script.
     * Returns value in GB, converting from MB if necessary.
     */
    public int getServerRam() {
        Path path = Path.of(serverDir, "sunucu_baslat.bat");
        if (!Files.exists(path)) {
            path = Path.of(serverDir, "baslat.bat");
        }

        try {
            if (!Files.exists(path)) {
                return 6; // Default fallback
            }

            String content = readStringWithFallback(path);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("-Xmx(\\d+)([GgMmKk])");
            java.util.regex.Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                int value = Integer.parseInt(matcher.group(1));
                char unit = Character.toUpperCase(matcher.group(2).charAt(0));
                return switch (unit) {
                    case 'G' -> value;
                    case 'M' -> Math.max(1, value / 1024);
                    case 'K' -> Math.max(1, value / (1024 * 1024));
                    default -> value;
                };
            }
        } catch (Exception e) {
            System.err.println("Error reading server RAM: " + e.getMessage());
        }

        return 6; // Default fallback
    }

    /**
     * Sets server RAM allocation in startup script.
     */
    public void setServerRam(int gigabytes) throws IOException {
        Path path = Path.of(serverDir, "sunucu_baslat.bat");
        if (!Files.exists(path)) {
            path = Path.of(serverDir, "baslat.bat");
        }

        if (!Files.exists(path)) {
            throw new IOException("Startup script not found");
        }

        String content = readStringWithFallback(path);

        // Replace -Xmx and -Xms values (support both G and M units)
        content = content.replaceAll("-Xmx\\d+[GgMm]", "-Xmx" + gigabytes + "G");
        content = content.replaceAll("-Xms\\d+[GgMm]", "-Xms" + gigabytes + "G");

        Files.writeString(path, content, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String readStringWithFallback(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);

        // 1. Try UTF-8 (Exception-free check using CharsetDecoder CoderResult)
        java.nio.charset.CharsetDecoder utf8Decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder();
        utf8Decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
        utf8Decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
        java.nio.CharBuffer out = java.nio.CharBuffer.allocate(bytes.length);
        java.nio.charset.CoderResult result = utf8Decoder.decode(buf, out, true);

        if (!result.isError()) {
            utf8Decoder.flush(out);
            out.flip();
            return out.toString();
        }

        // 2. Try Windows-1254 (Turkish ANSI)
        try {
            java.nio.charset.Charset winCharset = java.nio.charset.Charset.forName("windows-1254");
            java.nio.charset.CharsetDecoder winDecoder = winCharset.newDecoder();
            winDecoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
            winDecoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            buf.rewind();
            out = java.nio.CharBuffer.allocate(bytes.length);
            result = winDecoder.decode(buf, out, true);
            if (!result.isError()) {
                winDecoder.flush(out);
                out.flip();
                return out.toString();
            }
        } catch (Exception ignored) {
            // windows-1254 not supported in current environment
        }

        // 3. Fallback to ISO-8859-1 (raw byte mapping, never throws MalformedInputException)
        return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private List<String> readAllLinesWithFallback(Path path) throws IOException {
        String content = readStringWithFallback(path);
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}