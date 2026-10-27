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


    /**
     * Compares installed and published versions and sends at most one notice.
     */
    private void checkVersion(
            CommandSender sender,
            String current,
            ModrinthVersion latest,
            String latestStableVersion
    ) {
        String latestVersion = latest.getVersion();

        try {
            // Positive: published version is newer.
            // Negative: installed version is newer.
            int versionComparison =
                    VersionComparator.compare(latestVersion, current);

            int stableComparison =
                    VersionComparator.compare(latestStableVersion, current);

            boolean currentExperimental = current.contains("-");
            boolean latestPreRelease =
                    latest.isPrerelease() || latestVersion.contains("-");

            // 1. A newer stable release takes priority over a pre-release.
            if (stableComparison > 0) {
                int coreComparison = VersionComparator.compareCore(
                        latestStableVersion, current
                );

                if (currentExperimental && coreComparison == 0) {
                    plugin.getSchedulerManager().runSync(
                            () -> plugin.getNoticeManager()
                                    .sendStableReleaseNotice(
                                            sender, latestStableVersion
                                    )
                    );
                } else {
                    sendStableVersionUpdateNotice(
                            sender, current, latestStableVersion
                    );
                }
                return;
            }

            // 2. The installed version is ahead of both public versions.
            // This is DEV status, regardless of whether its name contains "-dev".
            if (versionComparison < 0 && stableComparison < 0) {
                plugin.getSchedulerManager().runSync(
                        () -> plugin.getNoticeManager()
                                .sendVersionDevNotice(
                                        sender, latestVersion
                                )
                );
                return;
            }

            // 3. A newer pre-release is available.
            if (latestPreRelease && versionComparison > 0) {
                plugin.getSchedulerManager().runSync(
                        () -> plugin.getNoticeManager()
                                .sendVersionPreReleaseNotice(
                                        sender, latestVersion
                                )
                );
                return;
            }

            // 4. No newer release is available, but the installed version
            // is experimental.
            if (currentExperimental) {
                plugin.getSchedulerManager().runSync(
                        () -> plugin.getNoticeManager()
                                .sendExperimentalNotice(
                                        sender, latestStableVersion
                                )
                );
                return;
            }

            // 5. No update notice is needed.
            plugin.getSchedulerManager().runSync(
                    () -> plugin.getNoticeManager().sendLatestVersionNotice(sender)
            );

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(
                    "Cannot compare version numbers: current="
                            + current + ", latest=" + latestVersion
            );

            plugin.getSchedulerManager().runSync(
                    () -> plugin.getNoticeManager().sendUpdateCheckError()
            );
        }
    }


    private void sendStableVersionUpdateNotice(
            CommandSender sender,
            String current,
            String latestStableVersion
    ) {
        int coreComparison = VersionComparator.compareCore(
                latestStableVersion, current
        );

        // The caller should invoke this only when a stable update exists.
        if (coreComparison <= 0) {
            return;
        }

        String cleanCurrent = current.split("-")[0]
                .replaceFirst("^[vV]", "");
        String cleanLatest = latestStableVersion.split("-")[0]
                .replaceFirst("^[vV]", "");

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
                plugin.getNoticeManager().sendMajorUpdateNotice(
                        sender, latestStableVersion
                );
            } else if (latestMinor > currentMinor) {
                plugin.getNoticeManager().sendMinorUpdateNotice(
                        sender, latestStableVersion
                );
            } else if (latestPatch > currentPatch) {
                plugin.getNoticeManager().sendPatchUpdateNotice(
                        sender, latestStableVersion
                );
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