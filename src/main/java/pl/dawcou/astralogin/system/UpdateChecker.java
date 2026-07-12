package pl.dawcou.astralogin.system;

import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

public class UpdateChecker {

    private final AstraLogin plugin;
    private final String projectId = "sO4dBl28";

    public UpdateChecker(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void getVersion(final Consumer<String> consumer) {
        // Od razu odpalamy to asynchronicznie, żeby nie blokować serwera
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                // Zapytanie bezpośrednio po ID projektu
                URL url = new URL("https://api.modrinth.com/v2/project/" + projectId + "/version");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "AstraLogin-UpdateChecker");

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    String json = response.toString();
                    if (json.contains("\"version_number\":\"")) {
                        String version = json.split("\"version_number\":\"")[1].split("\"")[0];
                        // Przekazujemy wersję do consumera (nadal w wątku Async)
                        consumer.accept(version);
                    }
                }
            } catch (Exception e) {
                // Wracamy na główny wątek, żeby bezpiecznie wysłać błąd do konsoli/managera
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    plugin.getNoticeManager().sendUpdateCheckError();
                });
            }
        });
    }
}