package pl.dawcou.astralogin.auth.security.ip;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.IOException;

public class IPTrustManager {

    private final AstraLogin plugin;
    private final File file;
    private FileConfiguration config;

    private static final int MAX_EVENT_POINTS = 10;

    // Zakres reputacji z configu
    private int minTrust;
    private int maxTrust;

    // Poziomy reputacji
    private int fatalLevel;
    private int badLevel;
    private int neutralLevel;
    private int goodLevel;

    // Punkty za zdarzenia
    private int loginSuccessPoints;
    private int failedPasswordPoints;
    private int twofaSuccessPoints;
    private int twofaFailedPoints;
    private int activeAccountAttemptPoints;
    private int unknownIpLoginPoints;
    private int ipSpamPoints;
    private int multiIpPoints;

    public enum TrustLevel {
        FATAL,
        BAD,
        NEUTRAL,
        GOOD,
        IDEAL
    }

    public int getLoginSuccessPoints() {
        return loginSuccessPoints;
    }

    public int getFailedPasswordPoints() {
        return failedPasswordPoints;
    }

    public int getTwofaSuccessPoints() {
        return twofaSuccessPoints;
    }

    public int getTwofaFailedPoints() {
        return twofaFailedPoints;
    }

    public int getActiveAccountAttemptPoints() {
        return activeAccountAttemptPoints;
    }

    public int getUnknownIpLoginPoints() {
        return unknownIpLoginPoints;
    }

    public int getIpSpamPoints() {
        return ipSpamPoints;
    }

    public int getMultiIpPoints() {
        return multiIpPoints;
    }

    public IPTrustManager(AstraLogin plugin) {
        this.plugin = plugin;

        File dataDir = new File(plugin.getDataFolder(), "data/global");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        file = new File(dataDir, "ip-trust.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        reload();
    }

    private void loadSettings() {
        minTrust = plugin.getConfig().getInt(
                "security.ip-trust.score.min",
                -30
        );

        maxTrust = plugin.getConfig().getInt(
                "security.ip-trust.score.max",
                30
        );


        fatalLevel = plugin.getConfig().getInt(
                "security.ip-trust.levels.fatal",
                -20
        );

        badLevel = plugin.getConfig().getInt(
                "security.ip-trust.levels.bad",
                -5
        );

        neutralLevel = plugin.getConfig().getInt(
                "security.ip-trust.levels.neutral",
                5
        );

        goodLevel = plugin.getConfig().getInt(
                "security.ip-trust.levels.good",
                15
        );


        loginSuccessPoints = getTrustPoints(
                "security.ip-trust.points.login.success",
                true
        );

        failedPasswordPoints = getTrustPoints(
                "security.ip-trust.points.login.failed-password",
                false
        );

        twofaSuccessPoints = getTrustPoints(
                "security.ip-trust.points.twofa.success",
                true
        );

        twofaFailedPoints = getTrustPoints(
                "security.ip-trust.points.twofa.failed-code",
                false
        );

        multiIpPoints = getTrustPoints(
                "security.ip-trust.points.security.multi-ip",
                false
        );

        activeAccountAttemptPoints = getTrustPoints(
                "security.ip-trust.points.security.active-account-attempt",
                false
        );

        unknownIpLoginPoints = getTrustPoints(
                "security.ip-trust.points.security.unknown-ip-login",
                false
        );

        ipSpamPoints = getTrustPoints(
                "security.ip-trust.points.security.ip-spam",
                false
        );
    }

    private void validateLevels() {
        if (fatalLevel < minTrust ||
                goodLevel > maxTrust) {

            plugin.getLogger().warning(
                    "IP Trust levels outside score range!"
            );

            fatalLevel = -20;
            badLevel = -5;
            neutralLevel = 5;
            goodLevel = 15;
        }

        if (minTrust >= maxTrust) {
            plugin.getLogger().warning(
                    "Invalid IP Trust range! Using defaults."
            );

            minTrust = -30;
            maxTrust = 30;
        }

        if (!(fatalLevel < badLevel &&
                badLevel < neutralLevel &&
                neutralLevel < goodLevel)) {

            plugin.getLogger().warning(
                    "Invalid IP Trust levels! Using defaults."
            );

            fatalLevel = -20;
            badLevel = -5;
            neutralLevel = 5;
            goodLevel = 15;
        }
    }

    public TrustLevel getTrustLevel(String ip) {
        int score = getTrustScore(ip);

        if (score <= fatalLevel) {
            return TrustLevel.FATAL;
        }

        if (score <= badLevel) {
            return TrustLevel.BAD;
        }

        if (score <= neutralLevel) {
            return TrustLevel.NEUTRAL;
        }

        if (score <= goodLevel) {
            return TrustLevel.GOOD;
        }

        return TrustLevel.IDEAL;
    }

    // Pomocnicza metoda zamieniająca IP na bezpieczny klucz YAML (kropki na podkreślenia)
    private String sanitizeIp(String ip) {
        if (ip == null) return "unknown";
        return ip.replace('.', '_');
    }

    public void resetTrustIP(String ip) {
        config.set("ips." + sanitizeIp(ip), null);
        save();
    }

    public boolean hasIP(String ip) {
        return config.contains("ips." + sanitizeIp(ip));
    }

    public int getTrustScore(String ip) {
        String safeIp = sanitizeIp(ip);
        int score = config.getInt(
                "ips." + safeIp + ".score",
                0
        );

        int fixed = Math.max(
                minTrust,
                Math.min(maxTrust, score)
        );

        if (score != fixed) {
            config.set(
                    "ips." + safeIp + ".score",
                    fixed
            );

            save();
        }

        return fixed;
    }

    public void addTrustScore(String ip, int score) {
        if (!plugin.getConfig().getBoolean("security.ip-trust.enabled", true)) {
            return;
        }
        String safeIp = sanitizeIp(ip);
        int newScore = Math.max(minTrust, Math.min(maxTrust, getTrustScore(ip) + score));

        config.set("ips." + safeIp + ".score", newScore);
        save();
    }

    public void setTrustScore(String ip, int score) {
        String safeIp = sanitizeIp(ip);
        int newScore = Math.max(minTrust, Math.min(maxTrust, score));

        config.set("ips." + safeIp + ".score", newScore);
        save();
    }

    public int getTrustPoints(String path, boolean positive) {
        int points = plugin.getConfig().getInt(path, 0);

        if (Math.abs(points) > MAX_EVENT_POINTS) {
            plugin.getLogger().warning(
                    "IP Trust point value is too high: " + path
            );
            return 0;
        }

        if (positive && points < 0) {
            plugin.getLogger().warning(
                    "IP Trust point cannot be negative: " + path
            );
            return 0;
        }

        if (!positive && points > 0) {
            plugin.getLogger().warning(
                    "IP Trust point cannot be positive: " + path
            );
            return 0;
        }

        return points;
    }

    private void save() {
        synchronized (config) {
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);

        loadSettings();
        validateLevels();
    }
}