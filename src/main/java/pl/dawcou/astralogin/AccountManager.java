package pl.dawcou.astralogin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

public class AccountManager implements CommandExecutor {

    private final AstraLogin plugin;

    public AccountManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // ==========================================
        // KOMENDA: /konto lub /account
        // ==========================================
        if (command.getName().equalsIgnoreCase("konto") || command.getName().equalsIgnoreCase("account")) {
            if (!sender.hasPermission("astralogin.account")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-usage"));
                return true;
            }

            String targetName = args[0];
            String targetUUIDString = null;
            FileConfiguration config = plugin.getAccountDataManager().getConfig();

            // Szukamy gracza w pliku accounts.yml po nicku
            if (config.getConfigurationSection("accounts") != null) {
                for (String uuidKey : config.getConfigurationSection("accounts").getKeys(false)) {
                    String knownName = config.getString("accounts." + uuidKey + ".last-known-name");
                    if (knownName != null && knownName.equalsIgnoreCase(targetName)) {
                        targetUUIDString = uuidKey;
                        targetName = knownName;
                        break;
                    }
                }
            }

            if (targetUUIDString == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-not-found")
                        .replace("%target%", targetName));
                return true;
            }

            String path = "accounts." + targetUUIDString + ".";
            String ip = config.getString(path + "last-ip", "Brak danych");
            String regDate = config.getString(path + "register-date", "Brak danych");
            String loginDate = config.getString(path + "last-login-date", "Brak danych");
            boolean isRegistered = config.getBoolean(path + "is-registered", false);
            boolean Has2FA = config.getBoolean(path + "2fa-enabled", false);

            if (!isRegistered) {
                regDate = plugin.getLanguageManager().getMessage("account-status-not-active-register");
            }

            String statusText2FA = Has2FA ?
                    plugin.getLanguageManager().getMessage("account-status-active-2fa") :
                    plugin.getLanguageManager().getMessage("account-status-not-active-2fa");

            sender.sendMessage(plugin.getLanguageManager().getMessage("account-stats-header").replace("%target%", targetName));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account-stats-uuid").replace("%uuid%", targetUUIDString));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account-stats-ip").replace("%ip%", ip));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account-stats-reg-date").replace("%register_date%", regDate));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account-stats-login-date").replace("%login_date%", loginDate));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account-stats-status-2fa").replace("%status%", statusText2FA));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account-stats-footer"));

            return true;
        }

        // ==========================================
        // KOMENDA: /zresetujkonto lub /resetaccount
        // ==========================================
        else if (command.getName().equalsIgnoreCase("zresetujkonto") || command.getName().equalsIgnoreCase("resetaccount")) {
            if (!sender.hasPermission("astralogin.resetaccount")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("usage-purge-account"));
                return true;
            }

            String targetName = args[0];
            UUID targetUUID = null;
            FileConfiguration accountsConfig = plugin.getAccountDataManager().getConfig();

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

            if (targetUUID == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-not-found").replace("%target%", targetName));
                return true;
            }

            String uuidString = targetUUID.toString();

            Player targetP = Bukkit.getPlayer(targetUUID);
            if (targetP != null && targetP.isOnline()) {
                plugin.getLoginSystem().getZalogowani().remove(targetUUID);
                String purgeReason = plugin.getLanguageManager().getMessage("player-purge-kick");
                targetP.kick(net.kyori.adventure.text.Component.text(purgeReason));
            }

            plugin.getPasswordManager().deletePassword(uuidString);
            plugin.getIPManager().deleteIP(uuidString);
            plugin.getInventoryManager().deleteInventoryCache(uuidString);
            plugin.getSpawnManager().deletePlayerSpawn(uuidString);
            plugin.getSessionManager().deleteSession(targetUUID);
            plugin.getSessionManager().deleteSession2FA(targetUUID);

            plugin.getAccountDataManager().purgeAccountData(targetUUID);

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("admin-purge-success").replace("%player%", targetName));

            String adminName = sender.getName();
            plugin.getLogManager().log("Admin " + adminName + " PURGED all account data for player " + targetName);

            return true;
        }

        else if (command.getName().equalsIgnoreCase("listaip") || command.getName().equalsIgnoreCase("iplist")) {
            if (!sender.hasPermission("astralogin.iplist")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("listaip-generating"));

            plugin.getServer().getAsyncScheduler().runNow(plugin, (task) -> {
                java.util.Map<String, java.util.List<String>> ipToNamesMap = new java.util.HashMap<>();
                IPManager ipManager = plugin.getIPManager();

                for (java.util.Map.Entry<String, String> entry : ipManager.getUuidToIpCache().entrySet()) {
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(entry.getKey());
                        org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                        String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown (" + entry.getKey().substring(0, 6) + ")";

                        ipToNamesMap.computeIfAbsent(entry.getValue(), k -> new java.util.ArrayList<>()).add(name);
                    } catch (IllegalArgumentException e) {
                        // Ignoruj wadliwe UUID
                    }
                }

                if (ipToNamesMap.isEmpty()) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("listaip-empty"));
                    return;
                }

                // Wysyłamy nagłówek bezpośrednio z managera
                sender.sendMessage(plugin.getLanguageManager().getMessage("listaip-header"));

                // Pobieramy format raz przed pętlą
                String format = plugin.getLanguageManager().getMessage("listaip-format");

                for (java.util.Map.Entry<String, java.util.List<String>> entry : ipToNamesMap.entrySet()) {
                    // Podmieniamy zmienne od razu przy wysyłaniu wiadomości, bez tworzenia zbędnych zmiennych pośrednich!
                    sender.sendMessage(format
                            .replace("{ip}", entry.getKey())
                            .replace("{count}", String.valueOf(entry.getValue().size()))
                            .replace("{players}", String.join(", ", entry.getValue())));
                }

                // Wysyłamy stopkę bezpośrednio z managera
                sender.sendMessage(plugin.getLanguageManager().getMessage("listaip-footer"));
            });

            return true;
        }

        else if (command.getName().equalsIgnoreCase("listakont") || command.getName().equalsIgnoreCase("accountslist")) {
            if (!sender.hasPermission("astralogin.accountslist")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("accounts-list-generating"));

            plugin.getServer().getAsyncScheduler().runNow(plugin, (task) -> {
                FileConfiguration config = plugin.getAccountDataManager().getConfig();

                if (config.getConfigurationSection("accounts") == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("accounts-list-empty"));
                    return;
                }

                java.util.List<String> formattedAccounts = new java.util.ArrayList<>();
                String format = plugin.getLanguageManager().getMessage("accounts-list-format");

                for (String uuidKey : config.getConfigurationSection("accounts").getKeys(false)) {
                    String path = "accounts." + uuidKey + ".";

                    // Filtrujemy tylko realnie zarejestrowanych graczy
                    if (config.getBoolean(path + "is-registered", false)) {
                        String knownName = config.getString(path + "last-known-name", "Unknown");

                        // Formatujemy linijkę – teraz bez zmiennej {ip}
                        String line = format
                                .replace("{name}", knownName)
                                .replace("{uuid}", uuidKey);

                        formattedAccounts.add(line);
                    }
                }

                if (formattedAccounts.isEmpty()) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("accounts-list-empty"));
                    return;
                }

                // Wysyłanie sformatowanej listy kont
                sender.sendMessage(plugin.getLanguageManager().getMessage("accounts-list-header"));
                for (String accountLine : formattedAccounts) {
                    sender.sendMessage(accountLine);
                }
                sender.sendMessage(plugin.getLanguageManager().getMessage("accounts-list-footer"));
            });

            return true;
        }

        return false;
    }
}