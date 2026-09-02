package core;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * CraftStation custom backup strategy using a proprietary binary format
 * with optional GZIP compression.
 * <p>
 * NOTE: Despite the internal name "TAR", this does NOT produce standard
 * TAR archives. Files use the ".csbak" / ".csbak.gz" extension to make
 * this clear and prevent users from trying to open them with tar/7-zip.
 */
public class TarBackupStrategy implements IBackupStrategy {

    private static final String STRATEGY_NAME = "TAR";
    private static final String TAR_EXTENSION = ".csbak";
    private static final String GZIP_EXTENSION = ".csbak.gz";

    private boolean useGzip = true;

    /**
     * Creates a TAR backup with optional GZIP compression.
     */
    @Override
    public String createBackup(String serverDir, String backupDir) throws IOException {
        Objects.requireNonNull(serverDir, "serverDir cannot be null");
        Objects.requireNonNull(backupDir, "backupDir cannot be null");

        // Generate backup filename
        Files.createDirectories(Paths.get(backupDir));
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String extension = useGzip ? GZIP_EXTENSION : TAR_EXTENSION;
        String fileName = "backup_" + timestamp + "_" + STRATEGY_NAME + extension;
        Path backupPath = Paths.get(backupDir, fileName);

        // Collect files to backup
        List<Path> filesToBackup = BackupUtils.collectFilesToBackup(serverDir, backupDir);

        // Create TAR archive
        try {
            try (OutputStream os = Files.newOutputStream(backupPath);
                    OutputStream tarStream = useGzip ? new GZIPOutputStream(os) : os) {

                createTarArchive(serverDir, filesToBackup, tarStream);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(backupPath);
            } catch (IOException ignored) {
            }
            throw e;
        }

        return fileName;
    }

    /**
     * Lists TAR backups in the backup directory.
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
                    path -> path.toString().endsWith(TAR_EXTENSION) ||
                            path.toString().endsWith(GZIP_EXTENSION))) {

                for (Path backupFile : stream) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(backupFile, BasicFileAttributes.class);
                        String fileName = backupFile.getFileName().toString();
                        String strategyName = fileName.contains("_TAR") ? "Tar" : "TarGzip";

                        backups.add(new BackupEntry(
                                fileName,
                                attrs.size(),
                                attrs.creationTime().toMillis(),
                                strategyName));
                    } catch (IOException e) {
                        System.err.println("Failed to read backup file attributes: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Failed to list TAR backups: " + e.getMessage());
        }

        return backups;
    }

    /**
     * Deletes a TAR backup.
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
     * Gets information about a TAR backup.
     */
    @Override
    public BackupInfo getBackupInfo(String backupDir, BackupEntry backupEntry) throws IOException {
        Objects.requireNonNull(backupDir, "backupDir cannot be null");
        Objects.requireNonNull(backupEntry, "backupEntry cannot be null");

        Path backupPath = Paths.get(backupDir, backupEntry.fileName());
        if (!Files.exists(backupPath)) {
            throw new FileNotFoundException("Backup file not found: " + backupEntry.fileName());
        }

        BasicFileAttributes attrs = Files.readAttributes(backupPath, BasicFileAttributes.class);
        long compressedSize = attrs.size();
        long originalSize = 0;
        List<String> includedWorlds = new ArrayList<>();

        // Parse custom TAR stream to compute original size and detect worlds dynamically
        try (InputStream is = Files.newInputStream(backupPath);
             InputStream tarStream = backupPath.toString().endsWith(".csbak.gz") ? new java.util.zip.GZIPInputStream(is) : is;
             DataInputStream dis = new DataInputStream(tarStream)) {
            
            int fileCount = dis.readInt();
            for (int i = 0; i < fileCount; i++) {
                String relativePath = dis.readUTF();
                long fileSize = dis.readLong();
                originalSize += fileSize;
                
                String pathStr = relativePath.replace('\\', '/');
                if (pathStr.endsWith("/level.dat") || pathStr.equals("level.dat")) {
                    String worldName = "world";
                    int idx = pathStr.indexOf('/');
                    if (idx > 0) {
                        worldName = pathStr.substring(0, idx);
                    }
                    if (!includedWorlds.contains(worldName)) {
                        includedWorlds.add(worldName);
                    }
                }
                
                // Skip file content
                long skipped = 0;
                while (skipped < fileSize) {
                    long skipAmt = dis.skip(fileSize - skipped);
                    if (skipAmt <= 0) {
                        if (dis.read() == -1) {
                            throw new EOFException("Unexpected EOF while parsing TAR archive");
                        }
                        skipped++;
                    } else {
                        skipped += skipAmt;
                    }
                }
            }
        } catch (IOException e) {
            // Fallback
            originalSize = estimateOriginalSize(backupPath);
            includedWorlds = Arrays.asList("world");
        }

        double compressionRatio = originalSize > 0 ? (double) compressedSize / originalSize : 0.0;

        return new BackupInfo(
                backupEntry,
                includedWorlds,
                compressedSize,
                originalSize,
                compressionRatio,
                calculateChecksum(backupPath));
    }

    /**
     * Restores a TAR backup.
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

        // Clean up the server for restore safely and dynamically
        BackupUtils.clearForRestore(serverDir);

        // Extract TAR archive
        extractTarArchive(backupPath, Paths.get(serverDir));
    }

    /**
     * Sets whether to use GZIP compression.
     */
    public void setUseGzip(boolean useGzip) {
        this.useGzip = useGzip;
    }

    /**
     * Gets whether GZIP compression is enabled.
     */
    public boolean isUseGzip() {
        return useGzip;
    }

    // Package-private helper methods for testability and archive operations

    InputStream openInputStream(Path file) throws IOException {
        return Files.newInputStream(file);
    }

    void createTarArchive(String serverDir, List<Path> files, OutputStream output) throws IOException {
        // Simplified TAR creation - in a real implementation, you would use a TAR
        // library
        // or implement proper TAR header format

        // For now, we'll create a simple archive format
        DataOutputStream dos = new DataOutputStream(output);

        // Write header with file count
        dos.writeInt(files.size());

        for (Path file : files) {
            // M2 fix: Windows backslash'leri archive'da sorun çıkarıyordu
            String relativePath = Paths.get(serverDir).relativize(file).toString().replace('\\', '/');
            dos.writeUTF(relativePath);

            // Write file size
            long fileSize = Files.size(file);
            dos.writeLong(fileSize);

            // C5 fix: Files.copy dosya büyürse stream desenkronize ediyordu
            // Tam fileSize kadar byte kopyala; erken biterse kalan miktarı zero-pad ile doldur
            try (InputStream fis = openInputStream(file)) {
                byte[] buffer = new byte[8192];
                long remaining = fileSize;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int bytesRead = fis.read(buffer, 0, toRead);
                    if (bytesRead == -1) {
                        // Dosya diskte küçüldüyse akış hizalamasını korumak için zero-pad bas
                        byte[] zeroPad = new byte[8192];
                        while (remaining > 0) {
                            int padSize = (int) Math.min(zeroPad.length, remaining);
                            dos.write(zeroPad, 0, padSize);
                            remaining -= padSize;
                        }
                        break;
                    }
                    dos.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
            }
        }

        dos.flush();
    }

    void extractTarArchive(Path tarFile, Path targetDir) throws IOException {
        // Simplified TAR extraction
        try (InputStream is = Files.newInputStream(tarFile);
                InputStream tarStream = tarFile.toString().endsWith(".csbak.gz") ? new java.util.zip.GZIPInputStream(is) : is;
                DataInputStream dis = new DataInputStream(tarStream)) {

            // Read file count
            int fileCount = dis.readInt();

            for (int i = 0; i < fileCount; i++) {
                // Read relative path
                String relativePath = dis.readUTF();

                // Read file size
                long fileSize = dis.readLong();

                // Create target file
                Path targetFile = BackupUtils.validateExtractPath(targetDir, relativePath);
                if (targetFile.getParent() != null) {
                    Files.createDirectories(targetFile.getParent());
                }

                // Copy file content
                try (OutputStream os = Files.newOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    long remaining = fileSize;

                    while (remaining > 0) {
                        int bytesToRead = (int) Math.min(buffer.length, remaining);
                        int bytesRead = dis.read(buffer, 0, bytesToRead);
                        if (bytesRead == -1) {
                            throw new EOFException("Unexpected end of TAR archive");
                        }
                        os.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }
            }
        }
    }

    private long estimateOriginalSize(Path tarFile) throws IOException {
        // Simplified estimation - in a real implementation, parse TAR headers
        // For now, return a reasonable estimate
        BasicFileAttributes attrs = Files.readAttributes(tarFile, BasicFileAttributes.class);
        long tarSize = attrs.size();

        // If compressed with GZIP, original is larger
        if (tarFile.toString().endsWith(".csbak.gz")) {
            return (long) (tarSize * 1.5); // Rough estimate
        }

        // TAR without compression is slightly larger than original due to headers
        return (long) (tarSize * 0.9); // Rough estimate
    }


    private String calculateChecksum(Path filePath) throws IOException {
        try {
            return BackupUtils.calculateChecksum(filePath);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }
}