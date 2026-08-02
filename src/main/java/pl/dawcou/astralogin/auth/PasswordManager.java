package pl.dawcou.astralogin.auth;

import net.kyori.adventure.text.Component;
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

        File dataDir = new File(plugin.getDataFolder(), "player_data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.file = new File(dataDir, "passwords.yml");
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
            plugin.getIPManager().deleteIP(uuidString);

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
                targetP.kick(Component.text(plugin.getLanguageManager().getMessage("reset-password.player-kick")));
            }

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
            if (obecneHasloWPliku == null || !PasswordManager.verifyPassword(oldPassword, obecneHasloWPliku)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.wrong-old"));
                plugin.getIpTrustManager().addTrustScore(
                        ip,
                        plugin.getIpTrustManager().getFailedPasswordPoints()
                );

                if (plugin.getConfig().getInt("features.attempts.max", 3) > 0) {
                    plugin.getAttemptManager().dodajProbe(p, "Password");
                }
                return true;
            }

            // 3. Sprawdzamy czy nowe hasła się zgadzają
            if (!newPassword.equals(newPasswordConfirm)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.not-match"));
                return true;
            }

            // 4. Sprawdzamy długość
            // Pobieramy min z configu, ale Math.max pilnuje, żeby wartość NIGDY nie była mniejsza niż 5
            int min = plugin.getConfig().getInt("features.password.min-password-length");
            min = Math.max(5, min);

            // Pobieramy max z configu, ale Math.min pilnuje, żeby wartość NIGDY nie przekroczyła 32
            int max = plugin.getConfig().getInt("features.password.max-password-length");
            max = Math.min(32, max);

            // Dodatkowe zabezpieczenie: gdyby admin w configu ustawił min większe niż max (np. min: 20, max: 10)
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

            // 5. HASZUJEMY RAZ I ZAPISUJEMY
            String newHashPassword = hashPassword(plugin, newPassword);
            savePassword(p.getUniqueId().toString(), newHashPassword);

            if (plugin.getLoginSystem().getLoggedIn().contains(p.getUniqueId())) {
                p.kick(Component.text(plugin.getLanguageManager().getMessage("password.changed-kick")));
            } else {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.changed"));
            }

            plugin.getLogManager().log("Player " + p.getName() + " changed his password");
            return true;
        }
        return false;
    }

    public void savePassword(String uuid, String haslo) {
        passwordCache.put(uuid, haslo);
        config.set("passwords." + uuid, haslo);
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
        config.set("passwords." + uuid, null);
        save();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
        this.passwordCache.clear();

        // Ładujemy wszystkie hasła do pamięci RAM przy starcie/przeładowaniu
        if (config.getConfigurationSection("passwords") != null) {
            for (String key : config.getConfigurationSection("passwords").getKeys(false)) {
                this.passwordCache.put(key, config.getString("passwords." + key));
            }
        }
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String hashPassword(AstraLogin plugin, String password) {
        int cost = plugin.getConfig().getInt("security.bcrypt.cost", 10);

        // Walidacja kosztu
        if (cost < 8 || cost > 16) {
            cost = 10;
        }

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