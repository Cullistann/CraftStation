package core;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/**
 * ZIP compression backup strategy.
 * Provides good compression with reasonable speed.
 */
public final class ZipBackupStrategy implements IBackupStrategy {

    private static final String STRATEGY_NAME = "ZIP";
    private static final int COMPRESSION_LEVEL = 6; // Balanced compression

    @Override
    public String createBackup(String serverDir, String backupDir) throws IOException {
        Objects.requireNonNull(serverDir, "serverDir cannot be null");
        Objects.requireNonNull(backupDir, "backupDir cannot be null");

        // Create backup directory if it doesn't exist
        Files.createDirectories(Paths.get(backupDir));

        // Generate timestamp
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS").format(new Date());
        String zipName = "backup_" + timestamp + "_" + STRATEGY_NAME + ".zip";
        Path zipPath = Paths.get(backupDir, zipName);

        List<Path> filesToBackup = BackupUtils.collectFilesToBackup(serverDir, backupDir);

        long originalSize = 0;

        try {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
                zos.setLevel(COMPRESSION_LEVEL);

                Path serverPath = Paths.get(serverDir);
                for (Path file : filesToBackup) {
                    String entryName = serverPath.relativize(file).toString().replace('\\', '/');
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zipEntry.setTime(attrs.lastModifiedTime().toMillis());
                    
                    zos.putNextEntry(zipEntry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    
                    originalSize += attrs.size();
                }

                // Find world folders from the files list dynamically for manifest
                Set<String> worldFolders = new LinkedHashSet<>();
                for (Path file : filesToBackup) {
                    Path relative = serverPath.relativize(file);
                    if (relative.getNameCount() > 1) {
                        String firstSegment = relative.getName(0).toString();
                        if (Files.exists(serverPath.resolve(firstSegment).resolve("level.dat"))) {
                            worldFolders.add(firstSegment);
                        }
                    }
                }
                if (worldFolders.isEmpty()) {
                    worldFolders.add("world"); // Fallback
                }

                // Add manifest file
                addManifest(zos, originalSize, worldFolders.toArray(new String[0]));
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(zipPath);
            } catch (IOException ignored) {
            }
            throw e;
        }

        return zipName;
    }

    @Override
    public List<BackupEntry> listBackups(String backupDir) {
        List<BackupEntry> backups = new ArrayList<>();

        try {
            Path dirPath = Paths.get(backupDir);
            if (!Files.exists(dirPath)) {
                return backups;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.zip")) {
                for (Path path : stream) {
                    String fileName = path.getFileName().toString();

                    // Check if this backup was created by this strategy
                    if (fileName.contains("_" + STRATEGY_NAME + ".zip")) {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        long size = attrs.size();
                        long creationTime = attrs.creationTime().toMillis();

                        backups.add(new BackupEntry(fileName, size, creationTime, STRATEGY_NAME));
                    }
                }
            }

            // Sort by creation time (newest first)
            backups.sort((a, b) -> Long.compare(b.creationTime(), a.creationTime()));

        } catch (IOException e) {
            System.err.println("Error listing backups: " + e.getMessage());
        }

        return backups;
    }

    @Override
    public void deleteBackup(String backupDir, BackupEntry backupEntry) throws IOException {
        Objects.requireNonNull(backupEntry, "backupEntry cannot be null");

        Path backupPath = Paths.get(backupDir, backupEntry.fileName());
        Files.deleteIfExists(backupPath);
    }

    @Override
    public BackupInfo getBackupInfo(String backupDir, BackupEntry backupEntry) throws IOException {
        Objects.requireNonNull(backupEntry, "backupEntry cannot be null");

        Path backupPath = Paths.get(backupDir, backupEntry.fileName());

        if (!Files.exists(backupPath)) {
            throw new FileNotFoundException("Backup file not found: " + backupEntry.fileName());
        }

        List<String> includedWorlds = new ArrayList<>();
        long compressedSize = Files.size(backupPath);
        long originalSize = 0;
        String checksum = calculateChecksum(backupPath);

        // Read ZIP file to get information
        try (ZipFile zipFile = new ZipFile(backupPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                // Track world folders dynamically from entries
                String entryName = entry.getName();
                if (entryName.endsWith("/level.dat") || entryName.equals("level.dat")) {
                    String worldName = "world";
                    int idx = entryName.indexOf('/');
                    if (idx > 0) {
                        worldName = entryName.substring(0, idx);
                    }
                    if (!includedWorlds.contains(worldName)) {
                        includedWorlds.add(worldName);
                    }
                }

                // Skip directories for size calculation
                if (!entry.isDirectory()) {
                    originalSize += entry.getSize();
                }
            }
        }

        double compressionRatio = originalSize > 0 ? (double) compressedSize / originalSize : 0.0;

        return new BackupInfo(
                backupEntry,
                includedWorlds,
                compressedSize,
                originalSize,
                compressionRatio,
                checksum);
    }

    @Override
    public void restoreBackup(String serverDir, String backupDir, BackupEntry backupEntry) throws IOException {
        Objects.requireNonNull(serverDir, "serverDir cannot be null");
        Objects.requireNonNull(backupEntry, "backupEntry cannot be null");

        Path backupPath = Paths.get(backupDir, backupEntry.fileName());

        if (!Files.exists(backupPath)) {
            throw new FileNotFoundException("Backup file not found: " + backupEntry.fileName());
        }

        // Clean up the server for restore safely and dynamically
        BackupUtils.clearForRestore(serverDir);

        // Extract ZIP file
        try (ZipFile zipFile = new ZipFile(backupPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path outputPath = BackupUtils.validateExtractPath(Paths.get(serverDir), entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    // Ensure parent directory exists
                    if (outputPath.getParent() != null) {
                        Files.createDirectories(outputPath.getParent());
                    }

                    // Extract file
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    // Private helper methods

    private void addManifest(ZipOutputStream zos, long originalSize, String[] worldFolders)
            throws IOException {

        ZipEntry manifestEntry = new ZipEntry("backup_manifest.json");
        zos.putNextEntry(manifestEntry);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("strategy", STRATEGY_NAME);
        manifest.put("created", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));
        manifest.put("originalSize", originalSize);
        manifest.put("worlds", Arrays.asList(worldFolders));

        // Simple JSON generation without GSON dependency
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
            } else if (value instanceof Number) {
                jsonBuilder.append(value);
            } else if (value instanceof Boolean) {
                jsonBuilder.append(value);
            } else if (value instanceof List) {
                jsonBuilder.append("[");
                List<?> list = (List<?>) value;
                boolean firstItem = true;
                for (Object item : list) {
                    if (!firstItem) {
                        jsonBuilder.append(", ");
                    }
                    firstItem = false;
                    jsonBuilder.append("\"").append(escapeJsonString(item.toString())).append("\"");
                }
                jsonBuilder.append("]");
            } else {
                jsonBuilder.append("\"").append(escapeJsonString(value.toString())).append("\"");
            }
        }

        jsonBuilder.append("\n}");

        zos.write(jsonBuilder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
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

}