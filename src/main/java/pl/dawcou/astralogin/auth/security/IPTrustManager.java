package pl.dawcou.astralogin.auth.security;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class IPTrustManager implements CommandExecutor, TabCompleter {

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

        File dataDir = new File(plugin.getDataFolder(), "global_data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.file = new File(dataDir, "ip-trust.yml");
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
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);

        loadSettings();
        validateLevels();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("zaufanieip") || command.getName().equalsIgnoreCase("iptrust")) {
            if (args.length == 0) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                return true;
            }

            String op = args[0];

            // Sprawdzamy jaką operację wpisano
            if (op.equalsIgnoreCase("reset")) {
                if (args.length != 2) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    return true;
                }
                if (!sender.hasPermission("astralogin.iptrust.reset")) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }
                String ip = args[1];

                if (ip == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    return true;
                }
                if (!hasIP(ip)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.ip-not-found")
                            .replace("%ip%", ip));
                    return true;
                }

                // Resetujemy zaufanie IP
                resetTrustIP(ip);

                String successMsg = plugin.getLanguageManager().getWithPrefix("ip-trust.reset-success")
                        .replace("%ip%", args[1]);

                sender.sendMessage(successMsg);

                String adminName = sender.getName();

                plugin.getLogManager().log("Admin " + adminName + " reset IP trust: " + ip);
                return true;

            } else if (op.equalsIgnoreCase("set")) {
                if (args.length != 3) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    return true;
                }
                if (!sender.hasPermission("astralogin.iptrust.set")) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }

                String ip = args[1];
                String scoreArg = args[2];

                int score;

                if (ip == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    return true;
                }
                if (!hasIP(ip)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.ip-not-found")
                            .replace("%ip%", ip));
                    return true;
                }
                try {
                    score = Integer.parseInt(scoreArg);
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.invalid-number"));
                    return true;
                }

                setTrustScore(ip, score);

                String successMsg = plugin.getLanguageManager().getWithPrefix("ip-trust.set-success")
                        .replace("%ip%", args[1])
                        .replace("%score%", String.valueOf(score));

                sender.sendMessage(successMsg);
                return true;

            } else if (op.equalsIgnoreCase("info")) {
                if (args.length != 2) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    return true;
                }
                if (!sender.hasPermission("astralogin.iptrust.info")) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }
                String ip = args[1];

                if (ip == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    return true;
                }
                if (!hasIP(ip)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.ip-not-found")
                            .replace("%ip%", ip));
                    return true;
                }

                int score = getTrustScore(ip);
                TrustLevel level = getTrustLevel(ip);
                String rate;

                switch (level) {
                    case FATAL:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-fatal");
                        break;

                    case BAD:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-bad");
                        break;

                    case NEUTRAL:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-neutral");
                        break;

                    case GOOD:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-good");
                        break;

                    case IDEAL:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-ideal");
                        break;

                    default:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-neutral");
                }

                List<String> infoLines = plugin.getLanguageManager().getMessageList("ip-trust.ip-info");

                for (String line : infoLines) {
                    sender.sendMessage(line
                            .replace("%ip%", ip)
                            .replace("%score%", String.valueOf(score))
                            .replace("%rate%", rate));
                }
                return true;
            }
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new java.util.ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("zaufanieip") || cmd.equalsIgnoreCase("iptrust")) {
            if (args.length == 1) {
                if (sender.hasPermission("astralogin.iptrust.reset")) {
                    hints.add("reset");
                }
                if (sender.hasPermission("astralogin.iptrust.info")) {
                    hints.add("info");
                }
                if (sender.hasPermission("astralogin.iptrust.set")) {
                    hints.add("set");
                }
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("info"))) {
                hints.add("<IP>");
            } else if (args.length == 3 && (args[0].equalsIgnoreCase("set"))) {
                hints.add("<score>");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(java.util.stream.Collectors.toList());
    }
}