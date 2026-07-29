package pl.dawcou.astralogin.system;

import com.google.gson.Gson;
import org.bukkit.command.CommandSender;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private final AstraLogin plugin;
    private final String projectId = "sO4dBl28";
    private final Gson gson = new Gson();

    public UpdateChecker(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates(CommandSender target) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                URL url = new URL(
                        "https://api.modrinth.com/v2/project/" + projectId + "/version"
                );

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setRequestProperty(
                        "User-Agent",
                        "AstraLogin-UpdateChecker"
                );

                StringBuilder response = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                ModrinthVersion[] versions = gson.fromJson(
                        response.toString(),
                        ModrinthVersion[].class
                );

                if (versions.length == 0) {
                    return;
                }

                // Modrinth zwraca najnowszą wersję jako pierwszą
                ModrinthVersion latest = versions[0];

                String currentVersion = plugin.getDescription().getVersion();

                checkVersion(target, currentVersion, latest.getVersion());

            } catch (Exception e) {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    plugin.getNoticeManager().sendUpdateCheckError();
                });
            }
        });
    }

    private void checkVersion(CommandSender sender, String current, String latest) {
        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");

        int currentMajor = Integer.parseInt(currentParts[0]);
        int currentMinor = Integer.parseInt(currentParts[1]);
        int currentPatch = Integer.parseInt(currentParts[2]);

        int latestMajor = Integer.parseInt(latestParts[0]);
        int latestMinor = Integer.parseInt(latestParts[1]);
        int latestPatch = Integer.parseInt(latestParts[2]);

        if (currentMajor > latestMajor
                || currentMinor > latestMinor
                || currentPatch > latestPatch) {

            plugin.getNoticeManager().sendVersionDevNotice(latest);

        } else if (latestMajor > currentMajor) {
            plugin.getNoticeManager().sendMajorUpdateNotice(sender, latest);

        } else if (latestMinor > currentMinor) {
            plugin.getNoticeManager().sendMinorUpdateNotice(sender, latest);

        } else if (latestPatch > currentPatch) {
            plugin.getNoticeManager().sendPatchUpdateNotice(sender, latest);

        } else {
            plugin.getNoticeManager().sendVersionOk();
        }
    }

    private static class ModrinthVersion {
        private String version_number;

        public String getVersion() {
            return version_number;
        }
    }
}