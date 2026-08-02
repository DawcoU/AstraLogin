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

                ModrinthVersion latest = versions[0];
                String currentVersion = plugin.getDescription().getVersion();

                checkVersion(target, currentVersion, latest);

            } catch (Exception e) {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    plugin.getNoticeManager().sendUpdateCheckError();
                });
            }
        });
    }

    private void checkVersion(CommandSender sender, String current, ModrinthVersion latest) {
        // 1. Idealne dopasowanie - masz DOKŁADNIE to, co jest najnowsze na Modrintha
        if (current.equalsIgnoreCase(latest.getVersion())) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (current.contains("-")) {
                    // Masz najnowszy pre-release -> przypadek 1C: tylko info o wersji eksperymentalnej
                    plugin.getNoticeManager().sendExperimentalNotice(sender);
                } else {
                    // Masz najnowszą wersję stabilną -> pełen spokój
                    plugin.getNoticeManager().sendVersionOk();
                }
            });
            return;
        }

        // 2. Bezpieczne czyszczenie i parsowanie cyfr wersji
        String cleanCurrent = current.split("-")[0];
        String cleanLatest = latest.getVersion().split("-")[0];

        String[] currentParts = cleanCurrent.split("\\.");
        String[] latestParts = cleanLatest.split("\\.");

        int currentMajor = currentParts.length > 0 ? Integer.parseInt(currentParts[0]) : 0;
        int currentMinor = currentParts.length > 1 ? Integer.parseInt(currentParts[1]) : 0;
        int currentPatch = currentParts.length > 2 ? Integer.parseInt(currentParts[2]) : 0;

        int latestMajor = latestParts.length > 0 ? Integer.parseInt(latestParts[0]) : 0;
        int latestMinor = latestParts.length > 1 ? Integer.parseInt(latestParts[1]) : 0;
        int latestPatch = latestParts.length > 2 ? Integer.parseInt(latestParts[2]) : 0;

        // Flagi pomocnicze do czystych warunków
        boolean isCurrentExperimental = current.contains("-");
        boolean isLatestPreRelease = latest.isPrerelease();

        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {

            // ==========================================
            // KROK 1: Obsługa wydań Pre-Release z sieci
            // ==========================================
            if (isLatestPreRelease) {
                // Masz starszy pre-release, a wyszedł nowszy (np. 1.2.0-pre1 vs 1.2.0-pre2)
                if (isCurrentExperimental && cleanCurrent.equals(cleanLatest)) {
                    // Wyświetla info o nowej testowej ORAZ informuje, że Twoja też jest eksperymentalna
                    plugin.getNoticeManager().sendExperimentalNotice(sender);
                    plugin.getNoticeManager().sendPreReleaseNotice(sender, latest.getVersion());
                    return;
                }

                // Masz stabilną 1.3.0, a wyszła nowa wyższa cyfra w testach 1.4.0-pre1
                // Wyświetlamy TYLKO info o dostępnym pre-release
                plugin.getNoticeManager().sendPreReleaseNotice(sender, latest.getVersion());
                return;
            }

            // ==========================================
            // KROK 2: Standardowe porównywanie stabilnych wersji
            // ==========================================
            if (latestMajor > currentMajor) {
                plugin.getNoticeManager().sendMajorUpdateNotice(sender, latest.getVersion());
            } else if (latestMinor > currentMinor) {
                plugin.getNoticeManager().sendMinorUpdateNotice(sender, latest.getVersion());
            } else if (latestPatch > currentPatch) {
                plugin.getNoticeManager().sendPatchUpdateNotice(sender, latest.getVersion());
            }

            // ==========================================
            // KROK 3: Cyfry równe, ale w sieci jest stabilna, a Ty masz testową (np. 1.2.0-pre2 vs 1.2.0)
            // ==========================================
            else if (isCurrentExperimental) {
                plugin.getNoticeManager().sendExperimentalNotice(sender);
            }

            // ==========================================
            // KROK 4: Twoje cyfry są po prostu większe (wersja DEV)
            // ==========================================
            else if (currentMajor > latestMajor || currentMinor > latestMinor || currentPatch > latestPatch) {
                plugin.getNoticeManager().sendVersionDevNotice(sender, latest.getVersion());
            } else {
                plugin.getNoticeManager().sendVersionOk();
            }
        });
    }

    private static class ModrinthVersion {
        private String version_number;
        private boolean prerelease;

        public String getVersion() {
            return version_number;
        }

        public boolean isPrerelease() {
            return prerelease;
        }
    }
}