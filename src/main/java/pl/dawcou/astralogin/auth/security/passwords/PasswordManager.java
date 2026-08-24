package pl.dawcou.astralogin.auth.security.passwords;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.mindrot.jbcrypt.BCrypt;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PasswordManager implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<String, String> passwordCache = new HashMap<>();

    public PasswordManager(AstraLogin plugin) {
        this.plugin = plugin;

        File dataDir = new File(plugin.getDataFolder(), "data/players");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        file = new File(dataDir, "passwords.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        reload();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("zresetujhaslo")) {
            if (!sender.hasPermission("astralogin.resetpassword")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-password.usage"));
                return true;
            }

            String targetName = args[0];
            UUID targetUUID = null;
            FileConfiguration accountsConfig = plugin.getAccountDataManager().getConfig();

            // 1. Szukamy UUID w historii kont, żeby nie lagować serwera przez getOfflinePlayer
            if (accountsConfig.getConfigurationSection("accounts") != null) {
                for (String uuidKey : accountsConfig.getConfigurationSection("accounts").getKeys(false)) {
                    String knownName = accountsConfig.getString("accounts." + uuidKey + ".last-known-name");
                    if (knownName != null && knownName.equalsIgnoreCase(targetName)) {
                        targetUUID = UUID.fromString(uuidKey);
                        targetName = knownName;
                        break;
                    }
                }
            }

            // 2. Jeśli nie grali u nas, to sprawdzamy tradycyjnie przez Bukkit na wszelki wypadek
            if (targetUUID == null) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                targetUUID = target.getUniqueId();
            }

            String uuidString = targetUUID.toString();

            // 3. Sprawdzamy hasło i usuwamy dane
            if (!isRegistered(uuidString)) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-password.no-account"));
                return true;
            }

            deletePassword(uuidString);

            plugin.getAccountDataManager().invalidateRegistration(targetUUID);

            // 4. Sukces, logi i wiadomości
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-password.admin-success")
                    .replace("%player%", targetName));

            String adminName = sender.getName();
            plugin.getLogManager().log("Admin " + adminName + " reset password for player " + targetName);

            // 5. LOGIKA DLA GRACZA ONLINE (Wyrzucanie i czyszczenie)
            Player targetP = Bukkit.getPlayer(targetUUID); // Pobieramy gracza po UUID

            if (targetP != null && targetP.isOnline()) {
                // Resetujemy próby błędnych logowań dla jego IP
                String playerIP = targetP.getAddress().getAddress().getHostAddress();
                plugin.getIPManager().resetIPAttempts(playerIP);

                // Wyrzucamy gracza z serwera
                targetP.kickPlayer(plugin.getLanguageManager().getMessage("reset-password.player-kick"));
            }

            return true;
        }

        if (command.getName().equalsIgnoreCase("niepamietamhasla") || command.getName().equalsIgnoreCase("forgotpassword")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            if (args.length != 1) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("forgot-password.usage"));
                return true;
            }

            String PIN = args[0];
            String uuidString = p.getUniqueId().toString();

            // Sprawdzamy, czy gracz w ogóle posiada PIN
            String hashedPIN = plugin.getPinManager().getPIN(uuidString);

            if (hashedPIN == null) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("forgot-password.no-has-pin"));
                return true;
            }

            // BCrypt ASYNC
            plugin.getSchedulerManager().runAsync(() -> {

                if (!PasswordManager.verifyPassword(PIN, hashedPIN)) {
                    plugin.getSchedulerManager().runSync(() -> {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("forgot-password.wrong-pin"));

                        String ip = p.getAddress().getAddress().getHostAddress();
                        plugin.getIpTrustManager().addTrustScore(ip, plugin.getIpTrustManager().getFailedPasswordPoints());

                        if (plugin.getConfig().getInt("features.attempts.max", 3) > 0) {
                            plugin.getAttemptManager().dodajProbe(p, "PIN");
                        }
                    });
                    return;
                }

                plugin.getSchedulerManager().runSync(() -> {
                    p.kickPlayer(plugin.getLanguageManager().getMessage("forgot-password.player-kick"));

                });

                // PIN poprawny -> usuwamy hasło
                deletePassword(uuidString);

                // Konto nie jest już oznaczone jako zarejestrowane
                plugin.getAccountDataManager().invalidateRegistration(p.getUniqueId());

                plugin.getLogManager().log("Player " + p.getName() + " reset his password using PIN");
            });

            return true;
        }

        if (command.getName().equalsIgnoreCase("zmienhaslo")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            if (args.length != 3) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.change-usage"));
                return true;
            }

            String ip = p.getAddress().getAddress().getHostAddress();
            String oldPassword = args[0];
            String newPassword = args[1];
            String newPasswordConfirm = args[2];

            // 1. Czy nowe hasło jest takie samo jak stare?
            if (oldPassword.equals(newPassword)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.identical"));
                return true;
            }

            // 2. Sprawdzamy stare hasło
            String obecneHasloWPliku = getPassword(p.getUniqueId().toString());
            if (obecneHasloWPliku == null) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.wrong-old"));
                return true;
            }

            // 3. Sprawdzamy czy nowe hasła się zgadzają
            if (!newPassword.equals(newPasswordConfirm)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.not-match"));
                return true;
            }

            // 4. Sprawdzamy długość
            int min = plugin.getConfig().getInt("features.password.min-password-length");
            min = Math.max(5, min);

            int max = plugin.getConfig().getInt("features.password.max-password-length");
            max = Math.min(32, max);

            // Dodatkowe zabezpieczenie: gdyby admin w configu ustawił min większe niż max
            if (min > max) {
                min = 6;
                max = 24;
            }

            if (newPassword.length() < min) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.too-short").replace("%min%", String.valueOf(min)));
                return true;
            }

            if (newPassword.length() > max) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.too-long").replace("%max%", String.valueOf(max)));
                return true;
            }

            // 5. BCrypt wykonujemy asynchronicznie
            plugin.getSchedulerManager().runAsync(() -> {
                if (!PasswordManager.verifyPassword(oldPassword, obecneHasloWPliku)) {
                    plugin.getSchedulerManager().runSync(() -> {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.wrong-old"));
                        plugin.getIpTrustManager().addTrustScore(ip, plugin.getIpTrustManager().getFailedPasswordPoints());

                        if (plugin.getConfig().getInt("features.attempts.max", 3) > 0) {
                            plugin.getAttemptManager().dodajProbe(p, "Password");
                        }
                    });
                    return;
                }

                String newHashPassword = PasswordManager.hashPassword(plugin, newPassword);

                if (newHashPassword == null) {
                    plugin.getSchedulerManager().runSync(() -> p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.error")));
                    return;
                }

                savePassword(p.getUniqueId().toString(), newHashPassword);

                plugin.getSchedulerManager().runSync(() -> {
                    if (plugin.getLoginSystem().getLoggedIn().contains(p.getUniqueId())) {
                        p.kickPlayer(plugin.getLanguageManager().getMessage("password.changed-kick"));
                    } else {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.changed"));
                    }

                    plugin.getLogManager().log("Player " + p.getName() + " changed his password");
                });
            });

            return true;
        }
        return false;
    }

    public void savePassword(String uuid, String password) {
        passwordCache.put(uuid, password);
        config.set("passwords." + uuid + ".password", password);
        save();
    }

    public String getPassword(String uuid) {
        return passwordCache.get(uuid);
    }

    public boolean isRegistered(String uuid) {
        return passwordCache.containsKey(uuid);
    }

    public void deletePassword(String uuid) {
        passwordCache.remove(uuid);
        config.set("passwords." + uuid + ".password", null);
        save();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
        passwordCache.clear();

        if (config.getConfigurationSection("passwords") != null) {
            for (String key : config.getConfigurationSection("passwords").getKeys(false)) {
                String password = config.getString("passwords." + key + ".password");

                if (password != null) {
                    passwordCache.put(key, password);
                }
            }
        }
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

    public static String hashPassword(AstraLogin plugin, String password) {
        int cost = plugin.getConfig().getInt("security.bcrypt.cost", 10);

        // Walidacja kosztu
        cost = Math.max(8, cost);
        cost = Math.min(16, cost);

        try {
            return BCrypt.hashpw(password, BCrypt.gensalt(cost));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean verifyPassword(String password, String hashed) {
        try {
            if (hashed == null || !hashed.startsWith("$2a$")) return false;
            return BCrypt.checkpw(password, hashed);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new java.util.ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("zmienhaslo") || cmd.equalsIgnoreCase("changepassword")) {
            if (args.length == 1) {
                hints.add("<old password>");
            }
            if (args.length == 2) {
                hints.add("<new password>");
            }
            if (args.length == 3) {
                hints.add("<repeat new password>");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(java.util.stream.Collectors.toList());
    }
}