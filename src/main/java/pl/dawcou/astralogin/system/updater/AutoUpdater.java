
package pl.dawcou.astralogin.system.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.SoundManager;
import pl.dawcou.astralogin.system.utils.VersionComparator;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public class AutoUpdater {

    private final AstraLogin plugin;
    private final String projectId;

    public AutoUpdater(AstraLogin plugin, String projectId) {
        this.plugin = plugin;
        this.projectId = projectId;
    }

    public void checkAndDownload(CommandSender commandTarget) {
        plugin.getSchedulerManager().runAsync(() -> {
            try {
                String urlStr =
                        "https://api.modrinth.com/v2/project/"
                                + projectId + "/version";

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .header("User-Agent", "AstraLogin-AutoUpdater")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() != 200) {
                    plugin.getLogger().warning(
                            "Failed to retrieve versions from Modrinth API. HTTP status: "
                                    + response.statusCode()
                    );
                    sendUpdateMessage(commandTarget, "update.api-error", false);
                    return;
                }

                JsonArray versions =
                        JsonParser.parseString(response.body()).getAsJsonArray();

                if (versions.isEmpty()) {
                    plugin.getLogger().info(
                            "Modrinth API returned no published versions"
                    );
                    sendUpdateMessage(commandTarget, "update.no-versions", false);
                    return;
                }

                // The first entry is the latest version returned by Modrinth.
                JsonObject latestVersion =
                        versions.get(0).getAsJsonObject();

                String versionName =
                        latestVersion.get("version_number").getAsString();

                String currentVersion =
                        plugin.getDescription().getVersion();

                int versionComparison;

                try {
                    versionComparison = VersionComparator.compare(
                            versionName,
                            currentVersion
                    );
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning(
                            "Cannot compare version numbers: current="
                                    + currentVersion + ", latest=" + versionName
                    );
                    sendUpdateMessage(commandTarget, "update.failed", false);
                    return;
                }

                if (versionComparison == 0) {
                    plugin.getLogger().info(
                            "AstraLogin is already up to date (" + currentVersion + ")"
                    );
                    sendUpdateMessage(commandTarget, "update.up-to-date", true);
                    return;
                }

                if (versionComparison < 0) {
                    plugin.getLogger().info(
                            "The installed version (" + currentVersion
                                    + ") is newer than the latest published version ("
                                    + versionName + ")"
                    );
                    sendUpdateMessage(commandTarget, "update.local-newer", true);
                    return;
                }

                JsonArray files = latestVersion.getAsJsonArray("files");

                if (files == null || files.isEmpty()) {
                    plugin.getLogger().warning(
                            "The latest Modrinth version contains no downloadable files"
                    );
                    sendUpdateMessage(commandTarget, "update.no-files", false);
                    return;
                }

                JsonObject primaryFile = null;

                for (var fileElement : files) {
                    JsonObject fileObject = fileElement.getAsJsonObject();

                    if (fileObject.has("primary")
                            && fileObject.get("primary").getAsBoolean()) {
                        primaryFile = fileObject;
                        break;
                    }
                }

                if (primaryFile == null) {
                    primaryFile = files.get(0).getAsJsonObject();
                }

                String downloadUrl =
                        primaryFile.get("url").getAsString();

                String filename =
                        primaryFile.get("filename").getAsString();

                File updateDir = Bukkit.getUpdateFolderFile();

                if (!updateDir.exists() && !updateDir.mkdirs()) {
                    plugin.getLogger().warning(
                            "Failed to create the update directory: "
                                    + updateDir.getAbsolutePath()
                    );
                    sendUpdateMessage(commandTarget, "update.directory-error", false);
                    return;
                }

                File destination = new File(updateDir, filename);

                HttpRequest downloadRequest = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header("User-Agent", "AstraLogin-AutoUpdater")
                        .GET()
                        .build();

                HttpResponse<InputStream> downloadResponse = client.send(
                        downloadRequest,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

                if (downloadResponse.statusCode() != 200) {
                    downloadResponse.body().close();

                    plugin.getLogger().warning(
                            "Failed to download the latest version. HTTP status: "
                                    + downloadResponse.statusCode()
                    );
                    sendUpdateMessage(commandTarget, "update.download-error", false);
                    return;
                }

                try (InputStream in = downloadResponse.body()) {
                    Files.copy(
                            in,
                            destination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

                File currentJar = new File(
                        AstraLogin.class.getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI()
                );

                File renamedJar = new File(
                        currentJar.getParentFile(),
                        filename
                );

                if (!currentJar.getName().equals(filename)) {
                    if (!currentJar.renameTo(renamedJar)) {
                        plugin.getLogger().warning(
                                "Failed to rename the current AstraLogin JAR"
                        );
                    }
                }

                plugin.getLogger().info(
                        "Successfully downloaded AstraLogin version "
                                + versionName + " to the update directory"
                );

                sendUpdateMessage(commandTarget, "update.downloaded", true);
                sendUpdateMessage(commandTarget, "update.restart-required", true);

            } catch (Exception e) {
                plugin.getLogger().log(
                        Level.WARNING,
                        "An error occurred while checking for or downloading an update",
                        e
                );

                sendUpdateMessage(commandTarget, "update.failed", false);
            }
        });
    }

    private void sendUpdateMessage(
            CommandSender commandTarget,
            String key,
            boolean success
    ) {
        if (commandTarget == null) {
            return;
        }

        plugin.getSchedulerManager().runSync(() -> {
            commandTarget.sendMessage(
                    plugin.getLanguageManager().getWithPrefix(key)
            );

            if (commandTarget instanceof Player player) {
                plugin.getSoundManager().playSound(
                        player,
                        success
                                ? SoundManager.SoundType.SUCCESS
                                : SoundManager.SoundType.FAIL
                );
            }
        });
    }
}