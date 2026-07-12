package pl.dawcou.astralogin.accounts;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.auth.AstraLogin;
import pl.dawcou.astralogin.auth.security.IPManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class AccountCommand implements CommandExecutor {

    private final AstraLogin plugin;

    public AccountCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

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
            String regDate = config.getString(path + "register-date", plugin.getLanguageManager().getMessage("account-no-data"));
            String loginDate = config.getString(path + "last-login-date", plugin.getLanguageManager().getMessage("account-no-data"));
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

            if (p != null) {
                boolean confirmed = (args.length > 1 && args[1].equalsIgnoreCase("confirm"));

                if (!confirmed) {
                    String baseMsgStr = plugin.getLanguageManager().getWithPrefix("account-purge-confirm");
                    String btnTextStr = plugin.getLanguageManager().getMessage("account-purge-button");
                    String hoverTextStr = plugin.getLanguageManager().getMessage("account-purge-hover");

                    Component baseMsg = LegacyComponentSerializer.legacySection()
                            .deserialize(baseMsgStr + " ");

                    Component confirmBtn = LegacyComponentSerializer.legacySection()
                            .deserialize(btnTextStr.replace("&", "§"))
                            .clickEvent(ClickEvent.runCommand("/resetaccount " + targetName + " confirm"))
                            .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr.replace("&", "§")));

                    sender.sendMessage(baseMsg.append(confirmBtn));
                    return true;
                }
            }

            String uuidString = targetUUID.toString();

            Player targetP = Bukkit.getPlayer(targetUUID);
            if (targetP != null && targetP.isOnline()) {
                plugin.getLoginSystem().getLoggedIn().remove(targetUUID);
                String purgeReason = plugin.getLanguageManager().getMessage("player-purge-kick");
                targetP.kick(Component.text(purgeReason));
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
                Map<String, List<String>> ipToNamesMap = new HashMap<>();
                IPManager ipManager = plugin.getIPManager();

                for (Map.Entry<String, String> entry : ipManager.getUuidToIpCache().entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                        String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown (" + entry.getKey().substring(0, 6) + ")";

                        ipToNamesMap.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(name);
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

                for (Map.Entry<String, List<String>> entry : ipToNamesMap.entrySet()) {
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

                List<String> formattedAccounts = new ArrayList<>();
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

        else if (command.getName().equalsIgnoreCase("przenieskonto") || command.getName().equalsIgnoreCase("moveaccount")) {
            if (!sender.hasPermission("astralogin.moveaccount")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("move-account-usage"));
                return true;
            }

            String oldNick = args[0];
            String newNick = args[1];

            OfflinePlayer oldPlayer = Bukkit.getOfflinePlayer(oldNick);
            OfflinePlayer newPlayer = Bukkit.getOfflinePlayer(newNick);

            String oldUUID = oldPlayer.getUniqueId().toString();
            String newUUID = newPlayer.getUniqueId().toString();

            if (oldNick.equals(newNick)) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("move-account-player-is-the-same"));
                return true;
            }

            if (!oldPlayer.hasPlayedBefore()) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("move-account-player-not-exists")
                        .replace("%target%", oldNick));
                return true;
            }

            if (oldPlayer.isOnline() || newPlayer.isOnline()) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("move-account-players-already-online")
                        .replace("%old%", oldNick)
                        .replace("%new%", newNick));
                return true;
            }

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("move-account-start")
                    .replace("%old%", oldNick)
                    .replace("%new%", newNick));

            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                Path pluginFolder = plugin.getDataFolder().toPath();

                // --- NOWY KROK: SPRAWDZANIE CZY STARY GRACZ MA JAKIEKOLWIEK DANE ---
                boolean oldPlayerHasData = false;
                try (var stream = Files.walk(pluginFolder)) {
                    var files = stream
                            .filter(Files::isRegularFile)
                            .filter(path -> !path.toString().contains(File.separator + "backups" + File.separator))
                            .toList();

                    for (Path path : files) {
                        String content = Files.readString(path);
                        // Szukamy po UUID lub po nicku w zawartości pliku / nazwie pliku
                        if (path.getFileName().toString().contains(oldUUID) ||
                                path.getFileName().toString().contains(oldNick) ||
                                content.contains(oldUUID)) {
                            oldPlayerHasData = true;
                            break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                // Jeśli stary nick nie ma żadnych danych - blokujemy za pomocą javowego replace
                if (!oldPlayerHasData) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("move-account-old-player-no-data")
                            .replace("%target%", oldNick));
                    return;
                }

                // --- KROK A: UNIWERSALNE SKANOWANIE BEZPIECZEŃSTWA (NOWY NICK) ---
                Path blockingFile = null;
                try (var stream = Files.walk(pluginFolder)) {
                    var files = stream
                            .filter(Files::isRegularFile)
                            .filter(path -> !path.toString().contains(File.separator + "backups" + File.separator))
                            .toList();

                    for (Path path : files) {
                        String content = Files.readString(path);
                        if (path.getFileName().toString().contains(newUUID) || content.contains(newUUID)) {
                            blockingFile = path;
                            break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                // Jeśli znaleźliśmy plik blokujący - przerywamy i wypisujemy jego nazwę!
                if (blockingFile != null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("move-account-new-player-exists")
                            .replace("%uuid%", newUUID));
                    return;
                }

                if (p != null) {
                    boolean confirmed = (args.length > 2 && args[2].equalsIgnoreCase("confirm"));

                    if (!confirmed) {
                        String baseMsgStr = plugin.getLanguageManager().getWithPrefix("move-account-confirm");
                        String btnTextStr = plugin.getLanguageManager().getMessage("move-account-button");
                        String hoverTextStr = plugin.getLanguageManager().getMessage("move-account-hover");

                        Component baseMsg = LegacyComponentSerializer.legacySection()
                                .deserialize(baseMsgStr + " ");

                        Component confirmBtn = LegacyComponentSerializer.legacySection()
                                .deserialize(btnTextStr.replace("&", "§"))
                                .clickEvent(ClickEvent.runCommand("/moveaccount " + oldNick + " " + newNick + " confirm"))
                                .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr.replace("&", "§")));

                        sender.sendMessage(baseMsg.append(confirmBtn));
                        return;
                    }
                }

                // --- KROK B: WŁAŚCIWA MIGRACJA ---
                try (var stream = Files.walk(pluginFolder)) {
                    final int[] modifiedFilesCount = {0};

                    stream.filter(Files::isRegularFile)
                            .filter(path -> !path.toString().contains(File.separator + "backups" + File.separator))
                            .forEach(path -> {
                                try {
                                    String content = Files.readString(path);

                                    if (content.contains(oldUUID)) {
                                        String newContent = content.replace(oldUUID, newUUID);
                                        Files.writeString(path, newContent);

                                        modifiedFilesCount[0]++;
                                    }
                                } catch (IOException e) {
                                    plugin.getNoticeManager().sendMigrationError(path.getFileName().toString());
                                }
                            });


                    // --- KROK C: CZYSZCZENIE I PRZEŁADOWANIE RAMU ---
                    plugin.getPasswordManager().reload();
                    plugin.getIPManager().reload();

                    plugin.getInventoryManager().reload();

                    plugin.getSpawnManager().reload();
                    plugin.getSessionManager().reload();

                    plugin.getAttemptManager().unregisterCache(oldUUID);
                    plugin.getTwoFactorManager().invalidateSetup(oldUUID);


                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("move-account-success")
                            .replace("%old%", oldNick)
                            .replace("%new%", newNick));

                    String adminName = sender.getName();
                    plugin.getLogManager().log("Player " + oldNick + " has been successfully migrated to " + newNick + " by " + adminName);

                } catch (IOException e) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("move-account-error-migration"));
                    e.printStackTrace();
                }
            });
            return true;
        }
        return false;
    }
}