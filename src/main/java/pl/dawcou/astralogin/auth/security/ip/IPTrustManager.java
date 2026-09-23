package pl.dawcou.astralogin.auth.security.ip;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.GlobalDataManager;

public class IPTrustManager {

    private final AstraLogin plugin;
    private final GlobalDataManager globalDataManager;

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

    public IPTrustManager(AstraLogin plugin, GlobalDataManager globalDataManager) {
        this.plugin = plugin;
        this.globalDataManager = globalDataManager;

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

    public void resetTrustIP(String ip) {
        if (ip == null) return;
        // Usuwa cały obiekt IP (ip_trust -> 127.0.0.1)
        globalDataManager.removeExplicit("ip_trust", ip);
    }

    public boolean hasIP(String ip) {
        if (ip == null) return false;
        // Sprawdza, czy istnieje pole "score" wewnątrz danego IP
        return globalDataManager.getElementExplicit("ip_trust", ip, "score") != null;
    }

    public int getTrustScore(String ip) {
        if (ip == null) return 0;

        int score = globalDataManager.getIntExplicit(0, "ip_trust", ip, "score");

        int fixed = Math.max(
                minTrust,
                Math.min(maxTrust, score)
        );

        // Jeśli wartość w pliku była poza zakresem min/max, naprawiamy ją w JSONie
        if (score != fixed) {
            globalDataManager.setExplicit(fixed, "ip_trust", ip, "score");
        }

        return fixed;
    }

    public void setTrustScore(String ip, int score) {
        if (ip == null) return;
        int newScore = Math.max(minTrust, Math.min(maxTrust, score));

        globalDataManager.setExplicit(newScore, "ip_trust", ip, "score");
    }

    public void addTrustScore(String ip, int score) {
        if (!plugin.getConfig().getBoolean("security.ip-trust.enabled", true) || ip == null) {
            return;
        }
        int newScore = Math.max(minTrust, Math.min(maxTrust, getTrustScore(ip) + score));

        globalDataManager.setExplicit(newScore, "ip_trust", ip, "score");
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

    public void reload() {
        loadSettings();
        validateLevels();
    }
}