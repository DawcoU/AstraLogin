package pl.dawcou.astralogin.logging;

import org.bukkit.configuration.file.FileConfiguration;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {

    private final AstraLogin plugin;
    private final File logsFolder;

    // Formaty czasu do nazwy pliku i do linijki logu
    private final DateTimeFormatter fileDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter logTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public LogManager(AstraLogin plugin) {
        this.plugin = plugin;

        // Tworzymy folder 'logs' wewnątrz głównego folderu pluginu
        logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
    }

    public synchronized void log(String message) {
        if (!plugin.getConfig().getBoolean("settings.logs.enabled", true)) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        int limit = config.getInt("settings.logs.limit", 30);

        // Pobieramy aktualny czas od razu na głównym wątku, żeby mieć idealną dokładność zdarzenia
        LocalDateTime now = LocalDateTime.now();
        String fileName = now.format(fileDateFormatter) + ".log";
        String timePrefix = "[" + now.format(logTimeFormatter) + "] ";

        String fullLogLine = timePrefix + message;

        // Odpalamy asynchroniczny scheduler, który zapisze to na dysku w tle
        plugin.getSchedulerManager().runAsync(() -> {
            try (FileWriter fw = new FileWriter(new File(logsFolder, fileName), true);
                 PrintWriter pw = new PrintWriter(fw)) {

                pw.println(fullLogLine);

            } catch (IOException e) {
                // Najpierw mięso potem kości
                plugin.getNoticeManager().sendLogSaveError(fileName);
                e.printStackTrace();
            }

            // Pobieranie limitu z configu i czyszczenie starych logów
            int finalLimit = Math.max(5, Math.min(60, limit));

            deleteOldLogs(logsFolder, finalLimit);
        });
    }

    private void deleteOldLogs(File logDirectory, int limit) {
        File[] files = logDirectory.listFiles((dir, name) -> name.endsWith(".log"));

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