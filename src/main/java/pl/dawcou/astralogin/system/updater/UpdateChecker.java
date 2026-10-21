package pl.dawcou.astralogin.system.updater;

import com.google.gson.Gson;
import org.bukkit.command.CommandSender;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.utils.VersionComparator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

public class UpdateChecker {

    private final AstraLogin plugin;
    private final String projectId;

    private final Gson gson = new Gson();

    public UpdateChecker(AstraLogin plugin, String projectId) {
        this.plugin = plugin;
        this.projectId = projectId;
    }


    public void checkForUpdates(CommandSender target) {
        plugin.getSchedulerManager().runAsync(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(
                        "https://api.modrinth.com/v2/project/" + projectId + "/version"
                );

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty(
                        "User-Agent",
                        "AstraLogin-UpdateChecker"
                );
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                StringBuilder response = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                java.nio.charset.StandardCharsets.UTF_8
                        ))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                ModrinthVersion[] versions = gson.fromJson(
                        response.toString(),
                        ModrinthVersion[].class
                );

                if (versions == null || versions.length == 0) {
                    return;
                }

                ModrinthVersion latest = versions[0];

                ModrinthVersion latestStable = java.util.Arrays.stream(versions)
                        .filter(Objects::nonNull)
                        .filter(version -> !version.isPrerelease())
                        .findFirst()
                        .orElse(null);

                if (latestStable == null) {
                    plugin.getLogger().warning(
                            "No stable AstraLogin version found on Modrinth."
                    );

                    plugin.getSchedulerManager().runSync(
                            () -> plugin.getNoticeManager().sendUpdateCheckError()
                    );
                    return;
                }

                String currentVersion = plugin.getDescription().getVersion();
                String latestStableVersion = latestStable.getVersion();

                checkVersion(
                        target,
                        currentVersion,
                        latest,
                        latestStableVersion
                );

            } catch (Exception e) {
                plugin.getLogger().warning(
                        "Failed to check AstraLogin updates: " + e.getMessage()
                );

                plugin.getSchedulerManager().runSync(
                        () -> plugin.getNoticeManager().sendUpdateCheckError()
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Compares software versions sequentially with proper DEV handling
    // ------------------------------------------------------------------

    private void checkVersion(
            CommandSender sender,
            String current,
            ModrinthVersion latest,
            String latestStableVersion
    ) {
        String latestVersion = latest.getVersion();

        int coreComparison;
        int versionComparison;

        try {
            coreComparison = VersionComparator.compareCore(latestVersion, current);
            versionComparison = VersionComparator.compare(latestVersion, current);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(
                    "Cannot compare version numbers: current="
                            + current + ", latest=" + latestVersion
            );

            plugin.getSchedulerManager().runSync(
                    () -> plugin.getNoticeManager().sendUpdateCheckError()
            );
            return;
        }

        boolean isCurrentExperimental = current.contains("-");
        boolean isLatestExperimental = latestVersion.contains("-");
        boolean isLatestPreRelease = latest.isPrerelease() || isLatestExperimental;

        if (versionComparison == 0) {
            plugin.getSchedulerManager().runSync(() -> {
                if (isCurrentExperimental) {
                    plugin.getNoticeManager()
                            .sendExperimentalNotice(sender, latestStableVersion);
                } else {
                    plugin.getNoticeManager().sendVersionOk(sender);
                }
            });
            return;
        }

        if (isCurrentExperimental) {
            plugin.getSchedulerManager().runSync(
                    () -> plugin.getNoticeManager()
                            .sendExperimentalNotice(sender, latestStableVersion)
            );
        }

        if (isLatestPreRelease && coreComparison == 0) {
            if (!isCurrentExperimental) {
                plugin.getSchedulerManager().runSync(
                        () -> plugin.getNoticeManager()
                                .sendVersionDevNotice(sender, latestStableVersion)
                );
                return;
            }

            int currentBuild = VersionComparator.extractBuildNumber(current);
            int latestBuild = VersionComparator.extractBuildNumber(latestVersion);

            if (latestBuild > currentBuild) {
                plugin.getSchedulerManager().runSync(
                        () -> plugin.getNoticeManager()
                                .sendVersionPreReleaseNotice(sender, latestVersion)
                );
            } else if (currentBuild > latestBuild) {
                plugin.getSchedulerManager().runSync(
                        () -> plugin.getNoticeManager()
                                .sendVersionDevNotice(sender, latestStableVersion)
                );
            }
            return;
        }

        if (coreComparison < 0) {
            plugin.getSchedulerManager().runSync(
                    () -> plugin.getNoticeManager()
                            .sendVersionDevNotice(sender, latestStableVersion)
            );
            return;
        }

        if (isLatestPreRelease) {
            plugin.getSchedulerManager().runSync(
                    () -> plugin.getNoticeManager()
                            .sendVersionPreReleaseNotice(sender, latestVersion)
            );
            return;
        }

        String cleanCurrent = current.split("-")[0].replaceFirst("^[vV]", "");
        String cleanLatest = latestVersion.split("-")[0].replaceFirst("^[vV]", "");

        String[] currentParts = cleanCurrent.split("\\.");
        String[] latestParts = cleanLatest.split("\\.");

        int currentMajor = Integer.parseInt(currentParts[0]);
        int currentMinor = currentParts.length > 1
                ? Integer.parseInt(currentParts[1]) : 0;
        int currentPatch = currentParts.length > 2
                ? Integer.parseInt(currentParts[2]) : 0;

        int latestMajor = Integer.parseInt(latestParts[0]);
        int latestMinor = latestParts.length > 1
                ? Integer.parseInt(latestParts[1]) : 0;
        int latestPatch = latestParts.length > 2
                ? Integer.parseInt(latestParts[2]) : 0;

        plugin.getSchedulerManager().runSync(() -> {
            if (latestMajor > currentMajor) {
                plugin.getNoticeManager().sendMajorUpdateNotice(sender, latestVersion);
            } else if (latestMinor > currentMinor) {
                plugin.getNoticeManager().sendMinorUpdateNotice(sender, latestVersion);
            } else if (latestPatch > currentPatch) {
                plugin.getNoticeManager().sendPatchUpdateNotice(sender, latestVersion);
            } else {
                plugin.getNoticeManager().sendVersionOk(sender);
            }
        });
    }


    private static class ModrinthVersion {
        private String version_number;
        private String version_type;

        public String getVersion() {
            return version_number;
        }

        public boolean isPrerelease() {
            return !"release".equalsIgnoreCase(version_type);
        }
    }

}