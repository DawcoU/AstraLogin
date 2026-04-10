package pl.dawcou.astralogin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

public class UpdateChecker {

    private final AstraLogin plugin;
    private final String projectSlug = "astralogin";

    public UpdateChecker(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void getVersion(final Consumer<String> consumer) {
        try {
            // Oficjalny link API Modrintha do pobierania wersji
            URL url = new URL("https://api.modrinth.com/v2/project/" + projectSlug + "/version");
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
                // Wyciągamy pierwszą znalezioną wersję z listy (najnowszą)
                if (json.contains("\"version_number\":\"")) {
                    String version = json.split("\"version_number\":\"")[1].split("\"")[0];
                    consumer.accept(version);
                }
            }
        } catch (Exception e) {
            plugin.sendUpdateCheckError();
        }
    }
}