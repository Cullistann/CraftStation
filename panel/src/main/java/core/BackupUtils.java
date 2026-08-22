package core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Yedekleme stratejileri için paylaşılan yardımcı metotlar.
 */
public final class BackupUtils {
    
    public static final DateTimeFormatter BACKUP_DATE_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    public static final DateTimeFormatter DISPLAY_DATE_FORMAT = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private BackupUtils() {}
    
    /**
     * SHA-256 checksum hesaplar.
     */
    public static String calculateChecksum(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * JSON string'i escape eder.
     */
    public static String escapeJsonString(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * Zip Slip saldırısına karşı çıkarma yolunu doğrular.
     * @throws IOException eğer yol hedef dizin dışına çıkıyorsa
     */
    public static Path validateExtractPath(Path targetDir, String entryName) throws IOException {
        Path target = targetDir.resolve(entryName).normalize();
        if (!target.startsWith(targetDir.normalize())) {
            throw new IOException("Zip Slip tespit edildi: " + entryName);
        }
        return target;
    }

    /**
     * Collects all files that should be included in a backup.
     * Unifies the file list across all backup strategies.
     * Symlink protected.
     */
    public static List<Path> collectFilesToBackup(String serverDir, String backupDir) throws IOException {
        List<Path> files = new ArrayList<>();
        Path serverPath = Paths.get(serverDir);
        Path resolvedBackupDir = Paths.get(backupDir).toAbsolutePath().normalize();
        Path resolvedPanelDir = serverPath.resolve("panel").toAbsolutePath().normalize();
        Path resolvedJavaDir = serverPath.resolve("java").toAbsolutePath().normalize();
        Path resolvedLogsDir = serverPath.resolve("logs").toAbsolutePath().normalize();
        Path resolvedCacheDir = serverPath.resolve("cache").toAbsolutePath().normalize();
        Path resolvedPaperRemapDir = serverPath.resolve(".paper-remapped").toAbsolutePath().normalize();
        Path resolvedVscodeDir = serverPath.resolve(".vscode").toAbsolutePath().normalize();
        
        // Find all directories that contain level.dat (which are world directories)
        Set<Path> worldPaths = new HashSet<>();
        try (var stream = Files.list(serverPath)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                if (Files.exists(dir.resolve("level.dat"))) {
                    worldPaths.add(dir.toAbsolutePath().normalize());
                }
            });
        }

        // List of allowed configuration files in root
        Set<String> allowedRootFiles = Set.of(
            "server.properties", "bukkit.yml", "spigot.yml", "paper.yml", 
            "pufferfish.yml", "purpur.yml", "commands.yml", "help.yml", 
            "permissions.yml", "eula.txt", "server-icon.png", "whitelist.json", 
            "ops.json", "banned-players.json", "banned-ips.json", "usercache.json"
        );

        Files.walkFileTree(serverPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path absPath = dir.toAbsolutePath().normalize();
                
                // Ignore symbolic links to prevent circular dependency or infinite loops
                if (Files.isSymbolicLink(dir) || attrs.isSymbolicLink()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                
                // Exclude system directories
                if (absPath.equals(resolvedBackupDir) || absPath.startsWith(resolvedBackupDir.resolve(""))) return FileVisitResult.SKIP_SUBTREE;
                if (absPath.equals(resolvedPanelDir) || absPath.startsWith(resolvedPanelDir.resolve(""))) return FileVisitResult.SKIP_SUBTREE;
                if (absPath.equals(resolvedJavaDir) || absPath.startsWith(resolvedJavaDir.resolve(""))) return FileVisitResult.SKIP_SUBTREE;
                if (absPath.equals(resolvedLogsDir) || absPath.startsWith(resolvedLogsDir.resolve(""))) return FileVisitResult.SKIP_SUBTREE;
                if (absPath.equals(resolvedCacheDir) || absPath.startsWith(resolvedCacheDir.resolve(""))) return FileVisitResult.SKIP_SUBTREE;
                if (absPath.equals(resolvedPaperRemapDir) || absPath.startsWith(resolvedPaperRemapDir.resolve(""))) return FileVisitResult.SKIP_SUBTREE;
                if (absPath.equals(resolvedVscodeDir) || absPath.startsWith(resolvedVscodeDir.resolve(""))) return FileVisitResult.SKIP_SUBTREE;
                
                // If it is in the root directory
                Path parentDir = absPath.getParent();
                if (parentDir != null && parentDir.equals(serverPath.toAbsolutePath().normalize())) {
                    // Check if it's a world path, plugins, or config directory
                    String name = absPath.getFileName().toString();
                    if (!worldPaths.contains(absPath) && !name.equalsIgnoreCase("plugins") && !name.equalsIgnoreCase("config")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path absPath = file.toAbsolutePath().normalize();
                
                // Ignore symbolic links
                if (Files.isSymbolicLink(file) || attrs.isSymbolicLink()) {
                    return FileVisitResult.CONTINUE;
                }
                
                // Check if file is in root
                Path parentFileDir = absPath.getParent();
                if (parentFileDir != null && parentFileDir.equals(serverPath.toAbsolutePath().normalize())) {
                    String name = file.getFileName().toString();
                    if (!allowedRootFiles.contains(name)) {
                        return FileVisitResult.CONTINUE;
                    }
                }
                
                files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        
        return files;
    }

    /**
     * Safely clears directories and files that will be restored from backup.
     * Avoids deleting JRE (java/) or panel (panel/) folders.
     * Throws an IOException immediately if any file deletion fails, aborting the process.
     */
    public static void clearForRestore(String serverDir) throws IOException {
        Path serverPath = Paths.get(serverDir);
        
        // Delete existing world folders (folders with level.dat in them)
        try (var stream = Files.list(serverPath)) {
            List<Path> worldDirs = stream.filter(Files::isDirectory)
                .filter(dir -> Files.exists(dir.resolve("level.dat")))
                .toList();
            for (Path dir : worldDirs) {
                deleteDirectoryRecursively(dir);
            }
        }
        
        // Delete plugins directory
        Path pluginsDir = serverPath.resolve("plugins");
        if (Files.exists(pluginsDir)) {
            deleteDirectoryRecursively(pluginsDir);
        }
        
        // Delete config directory
        Path configDir = serverPath.resolve("config");
        if (Files.exists(configDir)) {
            deleteDirectoryRecursively(configDir);
        }
        
        // Delete root config files
        Set<String> allowedRootFiles = Set.of(
            "server.properties", "bukkit.yml", "spigot.yml", "paper.yml", 
            "pufferfish.yml", "purpur.yml", "commands.yml", "help.yml", 
            "permissions.yml", "eula.txt", "server-icon.png", "whitelist.json", 
            "ops.json", "banned-players.json", "banned-ips.json", "usercache.json"
        );
        for (String filename : allowedRootFiles) {
            Path file = serverPath.resolve(filename);
            if (Files.exists(file)) {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    throw new IOException("Failed to delete critical file for restore: " + file.toAbsolutePath() + " - " + e.getMessage(), e);
                }
            }
        }
    }

    private static void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    throw new IOException("Failed to delete file during clean up: " + file.toAbsolutePath() + " - " + e.getMessage(), e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                try {
                    Files.delete(dir);
                } catch (IOException e) {
                    throw new IOException("Failed to delete directory during clean up: " + dir.toAbsolutePath() + " - " + e.getMessage(), e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
