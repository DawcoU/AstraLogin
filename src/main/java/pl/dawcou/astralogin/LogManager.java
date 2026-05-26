package pl.dawcou.astralogin;

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
        this.logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
    }

    public void log(String message) {
        if (!plugin.getConfig().getBoolean("settings.file-logging", true)) {
            return;
        }

        // Pobieramy aktualny czas od razu na głównym wątku, żeby mieć idealną dokładność zdarzenia
        LocalDateTime now = LocalDateTime.now();
        String fileName = now.format(fileDateFormatter) + ".log";
        String timePrefix = "[" + now.format(logTimeFormatter) + "] ";

        String fullLogLine = timePrefix + message;

        // Odpalamy asynchroniczny scheduler z Paper API, który zapisze to na dysku w tle
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            File logFile = new File(logsFolder, fileName);

            try {
                if (!logFile.exists()) {
                    logFile.createNewFile();
                }

                // FileWriter z parametrem true, aby dopisywać linie na końcu pliku (append)
                try (FileWriter fw = new FileWriter(logFile, true);
                     PrintWriter pw = new PrintWriter(fw)) {
                    pw.println(fullLogLine);
                }

            } catch (IOException e) {
                // Najpierw Twoja ładna informacja o pliku
                plugin.getNoticeManager().sendLogSaveError(fileName);
                // A potem surowy, pełny powód prosto od Javy
                e.printStackTrace();
            }
        });
    }
}