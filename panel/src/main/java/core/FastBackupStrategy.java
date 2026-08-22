package core;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Fast backup strategy that only backs up changed files since last backup.
 * Uses incremental backup approach for speed.
 */
public class FastBackupStrategy implements IBackupStrategy {

    private static final String STRATEGY_NAME = "FAST";
    private static final String EXTENSION = ".fast.zip";
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String CHECKSUM_FILE = ".last_backup_checksum";

    private final Map<String, String> lastChecksums = new ConcurrentHashMap<>();

    /**
     * Creates a fast incremental backup.
     */
    @Override
    public String createBackup(String serverDir, String backupDir) throws IOException {
        Objects.requireNonNull(serverDir, "serverDir cannot be null");
        Objects.requireNonNull(backupDir, "backupDir cannot be null");

        loadLastChecksums(backupDir);
        Files.createDirectories(Paths.get(backupDir));

        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        String fileName = "backup_" + timestamp + "_" + STRATEGY_NAME + EXTENSION;
        Path backupPath = Paths.get(backupDir, fileName);

        ScanResult scanResult = scanFiles(serverDir, backupDir);
        List<ChangedFile> changedFiles = scanResult.changedFiles();
        Map<String, String> newChecksums = scanResult.currentChecksums();
        boolean noChanges = changedFiles.isEmpty();

        if (noChanges) {
            System.out.println("No files changed since last backup. Creating manifest-only incremental backup.");
        }

        long totalOriginalSize = 0;

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(backupPath))) {
            zos.setLevel(1);

            for (ChangedFile changedFile : changedFiles) {
                Path file = changedFile.path();
                String relativePath = Paths.get(serverDir).relativize(file).toString().replace('\\', '/');

                ZipEntry entry = new ZipEntry(relativePath);
                entry.setTime(Files.getLastModifiedTime(file).toMillis());
                zos.putNextEntry(entry);

                long fileSize = Files.size(file);
                totalOriginalSize += fileSize;

                try (InputStream is = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }

                zos.closeEntry();
            }

            ZipEntry manifestEntry = new ZipEntry(MANIFEST_ENTRY);
            zos.putNextEntry(manifestEntry);

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("strategy", STRATEGY_NAME);
            manifest.put("created", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            manifest.put("type", "incremental");
            manifest.put("changedFiles", changedFiles.size());
            manifest.put("originalSize", totalOriginalSize);

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\n");

            boolean first = true;
            for (Map.Entry<String, Object> entry : manifest.entrySet()) {
                if (!first) {
                    jsonBuilder.append(",\n");
                }
                first = false;

                jsonBuilder.append("  \"").append(entry.getKey()).append("\": ");

                Object value = entry.getValue();
                if (value instanceof String) {
                    jsonBuilder.append("\"").append(escapeJsonString((String) value)).append("\"");
                } else {
                    jsonBuilder.append(value);
                }
            }

            jsonBuilder.append("\n}");

            zos.write(jsonBuilder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        } catch (IOException e) {
            try {
                Files.deleteIfExists(backupPath);
            } catch (IOException ignored) {
            }
            throw new IOException("Failed to create fast backup: " + e.getMessage(), e);
        }

        saveChecksums(newChecksums, backupDir);

        if (noChanges) {
            System.out.println("Manifest-only incremental backup created: " + fileName);
        } else {
            System.out.println("Fast backup created: " + fileName);
            System.out.println("Changed files: " + changedFiles.size());
            System.out.println("Original size: " + totalOriginalSize + " bytes");
        }

        return fileName;
    }

    /**
     * Lists fast backups in the backup directory.
     */
    @Override
    public List<BackupEntry> listBackups(String backupDir) {
        List<BackupEntry> backups = new ArrayList<>();

        try {
            Path backupPath = Paths.get(backupDir);
            if (!Files.exists(backupPath)) {
                return backups;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupPath,
                    path -> path.toString().endsWith(EXTENSION))) {

                for (Path backupFile : stream) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(backupFile, BasicFileAttributes.class);
                        String fileName = backupFile.getFileName().toString();

                        backups.add(new BackupEntry(
                                fileName,
                                attrs.size(),
                                attrs.creationTime().toMillis(),
                                STRATEGY_NAME));
                    } catch (IOException e) {
                        System.err.println("Failed to read backup file attributes: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Failed to list fast backups: " + e.getMessage());
        }

        return backups;
    }

    /**
     * Deletes a fast backup.
     */
    @Override
    public void deleteBackup(String backupDir, BackupEntry backupEntry) throws IOException {
        Objects.requireNonNull(backupDir, "backupDir cannot be null");
        Objects.requireNonNull(backupEntry, "backupEntry cannot be null");

        Path backupPath = Paths.get(backupDir, backupEntry.fileName());
        if (!Files.exists(backupPath)) {
            throw new FileNotFoundException("Backup file not found: " + backupEntry.fileName());
        }

        Files.delete(backupPath);
    }

    /**
     * Gets information about a fast backup.
     */
    @Override
    public BackupInfo getBackupInfo(String backupDir, BackupEntry backupEntry) throws IOException {
        Objects.requireNonNull(backupDir, "backupDir cannot be null");
        Objects.requireNonNull(backupEntry, "backupEntry cannot be null");

        Path backupPath = Paths.get(backupDir, backupEntry.fileName());
        if (!Files.exists(backupPath)) {
            throw new FileNotFoundException("Backup file not found: " + backupEntry.fileName());
        }

        // Parse manifest from ZIP
        Map<String, Object> manifest = parseManifest(backupPath);

        long compressedSize = Files.size(backupPath);
        long originalSize = ((Number) manifest.get("originalSize")).longValue();
        double compressionRatio = originalSize > 0 ? (double) compressedSize / originalSize : 0.0;

        List<String> includedWorlds = new ArrayList<>();
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(backupPath.toFile())) {
            Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                int idx = entryName.indexOf('/');
                if (idx > 0) {
                    String segment = entryName.substring(0, idx);
                    Path backupParent = Paths.get(backupDir).toAbsolutePath().getParent();
                    if (!includedWorlds.contains(segment) && backupParent != null && Files.exists(backupParent.resolve(segment).resolve("level.dat"))) {
                        includedWorlds.add(segment);
                    }
                }
            }
        } catch (IOException ignored) {}

        if (includedWorlds.isEmpty()) {
            includedWorlds = Arrays.asList("world");
        }

        return new BackupInfo(
                backupEntry,
                includedWorlds,
                compressedSize,
                originalSize,
                compressionRatio,
                calculateChecksum(backupPath));
    }

    /**
     * Restores a fast backup.
     */
    @Override
    public void restoreBackup(String serverDir, String backupDir, BackupEntry backupEntry) throws IOException {
        Objects.requireNonNull(serverDir, "serverDir cannot be null");
        Objects.requireNonNull(backupDir, "backupDir cannot be null");
        Objects.requireNonNull(backupEntry, "backupEntry cannot be null");

        Path backupPath = Paths.get(backupDir, backupEntry.fileName());
        if (!Files.exists(backupPath)) {
            throw new FileNotFoundException("Backup file not found: " + backupEntry.fileName());
        }

        // Extract ZIP archive
        extractZipArchive(backupPath, Paths.get(serverDir));

        System.out.println("Fast backup restored: " + backupEntry.fileName());
    }

    // Private helper methods

    private ScanResult scanFiles(String serverDir, String backupDir) throws IOException {
        List<ChangedFile> changedFiles = new ArrayList<>();
        Map<String, String> currentChecksums = new ConcurrentHashMap<>();
        Path serverPath = Paths.get(serverDir);

        if (!Files.exists(serverPath)) {
            throw new FileNotFoundException("Server directory not found: " + serverDir);
        }

        List<Path> filesToBackup = BackupUtils.collectFilesToBackup(serverDir, backupDir);

        for (Path file : filesToBackup) {
            String relativePath = serverPath.relativize(file).toString().replace('\\', '/');
            String currentChecksum = calculateChecksum(file);

            currentChecksums.put(relativePath, currentChecksum);

            // Check if file has changed
            String lastChecksum = lastChecksums.get(relativePath);
            if (lastChecksum == null || !lastChecksum.equals(currentChecksum)) {
                changedFiles.add(new ChangedFile(file, currentChecksum));
            }
        }

        return new ScanResult(changedFiles, currentChecksums);
    }

    private void loadLastChecksums(String backupDir) throws IOException {
        lastChecksums.clear();
        Path checksumFile = Paths.get(backupDir, CHECKSUM_FILE);

        if (!Files.exists(checksumFile)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(checksumFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    lastChecksums.put(parts[0], parts[1]);
                }
            }
        }
    }

    private void saveChecksums(Map<String, String> checksums, String backupDir) throws IOException {
        Path checksumFile = Paths.get(backupDir, CHECKSUM_FILE);

        try (BufferedWriter writer = Files.newBufferedWriter(checksumFile)) {
            for (Map.Entry<String, String> entry : checksums.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
        }
    }

    private Map<String, Object> parseManifest(Path backupPath) throws IOException {
        Map<String, Object> manifest = new HashMap<>();
        String json = null;

        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(backupPath.toFile())) {
            java.util.zip.ZipEntry manifestEntry = zipFile.getEntry(MANIFEST_ENTRY);

            if (manifestEntry != null) {
                try (InputStream is = zipFile.getInputStream(manifestEntry);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

                    StringBuilder jsonBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        jsonBuilder.append(line);
                    }

                    json = jsonBuilder.toString();
                }
            }
        }

        manifest.put("originalSize", parseLong(json, "originalSize", 0L));
        manifest.put("changedFiles", parseInt(json, "changedFiles", 0));

        return manifest;
    }

    private long parseLong(String json, String key, long defaultValue) {
        if (json == null) {
            return defaultValue;
        }
        String marker = "\"" + key + "\":";
        int idx = json.indexOf(marker);
        if (idx == -1) {
            return defaultValue;
        }
        int start = idx + marker.length();
        int end = json.indexOf(',', start);
        if (end == -1) {
            end = json.indexOf('}', start);
        }
        if (end == -1) {
            return defaultValue;
        }
        try {
            return Long.parseLong(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int parseInt(String json, String key, int defaultValue) {
        return (int) parseLong(json, key, defaultValue);
    }

    private void extractZipArchive(Path zipFile, Path targetDir) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile.toFile())) {
            Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                Path targetFile = BackupUtils.validateExtractPath(targetDir, entry.getName());

                // Skip manifest entry for restoration
                if (entry.getName().equals(MANIFEST_ENTRY)) {
                    continue;
                }

                // C4 fix: directory entry'leri dosya olarak açmayı önle
                if (entry.isDirectory()) {
                    Files.createDirectories(targetFile);
                    continue;
                }

                // Create parent directories (null guard)
                if (targetFile.getParent() != null) {
                    Files.createDirectories(targetFile.getParent());
                }

                // Extract file
                try (InputStream is = zip.getInputStream(entry);
                        OutputStream os = Files.newOutputStream(targetFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;

                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }
        }
    }

    private String calculateChecksum(Path filePath) throws IOException {
        try {
            return BackupUtils.calculateChecksum(filePath);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }

    private String escapeJsonString(String input) {
        return BackupUtils.escapeJsonString(input);
    }

    /**
     * Record representing a changed file with its checksum.
     */
    private record ChangedFile(Path path, String checksum) {
    }

    /**
     * Record representing the scan result.
     */
    private record ScanResult(List<ChangedFile> changedFiles, Map<String, String> currentChecksums) {
    }
}