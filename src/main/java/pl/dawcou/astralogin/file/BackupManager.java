package pl.dawcou.astralogin.file;

import org.bukkit.configuration.file.FileConfiguration;
import pl.dawcou.astralogin.auth.AstraLogin;
import pl.dawcou.astralogin.system.LoginUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupManager {

    private final AstraLogin plugin;

    public BackupManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void createBackup() {
        FileConfiguration config = plugin.getConfig();
        String timeString = config.getString("settings.backups.interval", "24 hours");
        int limit = config.getInt("settings.backups.limit", 10);

        if (!config.getBoolean("settings.backups.enabled", true)) {
            return;
        }

        plugin.getSchedulerManager().runAsync(() -> {
            File dataFolder = plugin.getDataFolder();
            File backupDirectory = new File(dataFolder, "backups");

            long defaultIntervalMs = 24L * 60 * 60 * 1000;
            long intervalMs = LoginUtils.parseTime(timeString, defaultIntervalMs);

            long minimumMs = 12L * 60 * 60 * 1000;
            if (intervalMs < minimumMs) {
                intervalMs = minimumMs;
            }

            long now = System.currentTimeMillis();
            long lastBackupTime = 0L;

            if (backupDirectory.exists() && backupDirectory.isDirectory()) {
                File[] files = backupDirectory.listFiles((dir, name) -> name.endsWith(".zip"));
                if (files != null && files.length > 0) {
                    for (File file : files) {
                        if (file.lastModified() > lastBackupTime) {
                            lastBackupTime = file.lastModified();
                        }
                    }
                }
            }

            if (lastBackupTime > 0 && (now - lastBackupTime < intervalMs)) {
                return;
            }

            // Sprawdzamy czy w ogóle jest co pakować
            if (!dataFolder.exists()) {
                return;
            }

            if (!backupDirectory.exists()) {
                backupDirectory.mkdirs();
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
            String zipFileName = "backup_" + LocalDateTime.now().format(formatter) + ".zip";
            File zipFile = new File(backupDirectory, zipFileName);

            if (zipFile.exists()) {
                return;
            }

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                Path sourcePath = dataFolder.toPath();
                // Wyciągamy ścieżki tutaj, żeby utworzyć je tylko RAZ
                Path backupsPath = backupDirectory.toPath();
                Path logsPath = new File(dataFolder, "logs").toPath();

                Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        // Teraz tylko porównujemy gotowe obiekty
                        if (dir.equals(backupsPath) || dir.equals(logsPath)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String zipEntryName = sourcePath.relativize(file).toString().replace("\\", "/");
                        zos.putNextEntry(new ZipEntry(zipEntryName));
                        Files.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });

                plugin.getNoticeManager().sendBackupSave(zipFileName);

                // Pobieranie limitu z configu i czyszczenie starych kopii
                int finalLimit = Math.max(1, Math.min(50, limit));

                deleteOldBackups(backupDirectory, finalLimit);

            } catch (IOException e) {
                plugin.getNoticeManager().sendBackupSaveError(e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void deleteOldBackups(File backupDirectory, int limit) {
        File[] files = backupDirectory.listFiles((dir, name) -> name.endsWith(".zip"));

        if (files == null || files.length <= limit) {
            return;
        }

        // Sortowanie plików od najstarszego do najnowszego
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));

        int filesToDelete = files.length - limit;
        for (int i = 0; i < filesToDelete; i++) {
            files[i].delete();
        }
    }
}