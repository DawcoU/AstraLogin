package pl.dawcou.astralogin.system;

import com.google.gson.Gson;
import org.bukkit.command.CommandSender;
import pl.dawcou.astralogin.AstraLogin;

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
        plugin.getSchedulerManager().runAsync(() -> {
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
                plugin.getSchedulerManager().runSync(() -> plugin.getNoticeManager().sendUpdateCheckError());
            }
        });
    }

    // ------------------------------------------------------------------
    // Compares software versions sequentially with proper DEV handling
    // ------------------------------------------------------------------
    private void checkVersion(CommandSender sender, String current, ModrinthVersion latest) {
        if (current.equalsIgnoreCase(latest.getVersion())) {
            plugin.getSchedulerManager().runSync(() -> {
                if (current.contains("-")) {
                    plugin.getNoticeManager().sendExperimentalNotice(sender);
                } else {
                    plugin.getNoticeManager().sendVersionOk(sender);
                }
            });
            return;
        }

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

        boolean isCurrentExperimental = current.contains("-");
        boolean isLatestExperimental = latest.getVersion().contains("-");
        boolean isLatestPreRelease = latest.isPrerelease() || isLatestExperimental;

        plugin.getSchedulerManager().runSync(() -> {

            // ==========================================
            // KROK 1: Obsługa wersji z sieci typu Pre-Release / Experimental
            // ==========================================
            if (isLatestPreRelease) {
                if (cleanCurrent.equals(cleanLatest)) {
                    if (!isCurrentExperimental) {
                        // Masz np. 4.5.0 na serwerze, a na sieci jest 4.5.0-beta2
                        plugin.getNoticeManager().sendVersionDevNotice(sender, latest.getVersion());
                        return;
                    } else {
                        // Obydwie to wersje testowe (np. 4.5.0-beta1 vs 4.5.0-beta2)
                        int currentBuild = extractBuildNumber(current);
                        int latestBuild = extractBuildNumber(latest.getVersion());

                        plugin.getNoticeManager().sendExperimentalNotice(sender);

                        if (latestBuild > currentBuild) {
                            // Na stronie jest nowsza beta!
                            plugin.getNoticeManager().sendPreReleaseNotice(sender, latest.getVersion());
                        } else if (currentBuild > latestBuild) {
                            // Masz nowszą betę niż na stronie (dev)
                            plugin.getNoticeManager().sendVersionDevNotice(sender, latest.getVersion());
                        }
                        return;
                    }
                }

                boolean isLatestHigher = (latestMajor > currentMajor) ||
                        (latestMajor == currentMajor && latestMinor > currentMinor) ||
                        (latestMajor == currentMajor && latestMinor == currentMinor && latestPatch > currentPatch);

                if (isLatestHigher) {
                    plugin.getNoticeManager().sendPreReleaseNotice(sender, latest.getVersion());
                    return;
                }
            }

            // ==========================================
            // KROK 2: Sekwencyjne porównywanie wersji (Major -> Minor -> Patch)
            // ==========================================
            if (latestMajor > currentMajor) {
                plugin.getNoticeManager().sendMajorUpdateNotice(sender, latest.getVersion());
            } else if (currentMajor > latestMajor) {
                plugin.getNoticeManager().sendVersionDevNotice(sender, latest.getVersion());
            } else if (latestMinor > currentMinor) {
                plugin.getNoticeManager().sendMinorUpdateNotice(sender, latest.getVersion());
            } else if (currentMinor > latestMinor) {
                plugin.getNoticeManager().sendVersionDevNotice(sender, latest.getVersion());
            } else if (latestPatch > currentPatch) {
                plugin.getNoticeManager().sendPatchUpdateNotice(sender, latest.getVersion());
            } else if (currentPatch > latestPatch) {
                plugin.getNoticeManager().sendVersionDevNotice(sender, latest.getVersion());
            } else if (isCurrentExperimental) {
                plugin.getNoticeManager().sendExperimentalNotice(sender);
            } else {
                plugin.getNoticeManager().sendVersionOk(sender);
            }
        });
    }

    // --------------------------------------------------------------------------------
    // Helper method to extract trailing digits from version string
    // --------------------------------------------------------------------------------
    private int extractBuildNumber(String version) {
        if (!version.contains("-")) return 0;
        String suffix = version.substring(version.indexOf("-") + 1);
        String numbersOnly = suffix.replaceAll("\\D+", "");
        return numbersOnly.isEmpty() ? 0 : Integer.parseInt(numbersOnly);
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