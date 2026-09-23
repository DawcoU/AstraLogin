package pl.dawcou.astralogin.commands.admin;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;

import java.io.File;
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
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account.usage"));
                return true;
            }

            String inputName = args[0];

            String targetName = plugin.getAccountManager().getRegisteredNameIgnoreCase(inputName);
            UUID targetUUID = plugin.getAccountManager().getUuidByUsername(inputName);

            if (targetUUID == null || targetName == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account.not-found")
                        .replace("%target%", inputName));
                return true;
            }

            String targetUUIDString = targetUUID.toString();
            PlayerDataManager pdm = plugin.getPlayerDataManager();

            String ip = plugin.getIPManager().getIP(targetUUIDString);
            if (ip == null) {
                ip = plugin.getLanguageManager().getMessage("account.no-data");
            }

            String regDate = pdm.getString(targetUUID, "account.register-date");
            if (regDate == null) {
                regDate = plugin.getLanguageManager().getMessage("account.not-found");
            }

            String loginDate = pdm.getString(targetUUID, "account.last-login-date");
            if (loginDate == null) {
                loginDate = plugin.getLanguageManager().getMessage("account.not-found");
            }

            boolean isRegistered = pdm.getBoolean(targetUUID, "account.is-registered", false);
            boolean Has2FA = plugin.getTwoFactorManager().has2FA(targetUUID);

            if (!isRegistered) {
                regDate = plugin.getLanguageManager().getMessage("account.status.not-registered");
            }

            String statusText2FA = Has2FA ?
                    plugin.getLanguageManager().getMessage("account.status.active-2fa") :
                    plugin.getLanguageManager().getMessage("account.status.not-active-2fa");

            sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.header").replace("%target%", targetName));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.uuid").replace("%uuid%", targetUUIDString));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.ip").replace("%ip%", ip));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.register-date").replace("%register_date%", regDate));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.login-date").replace("%login_date%", loginDate));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.status-2fa").replace("%status%", statusText2FA));
            sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.footer"));

            return true;
        }

        // ==========================================
        // KOMENDA: /zresetujkonto lub /resetaccount
        // ==========================================
        else if (command.getName().equalsIgnoreCase("zresetujkonto") || command.getName().equalsIgnoreCase("resetaccount")) {
            if (!sender.hasPermission("astralogin.resetaccount")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("purge-account.usage"));
                return true;
            }

            String inputName = args[0];
            String targetName = plugin.getAccountManager().getRegisteredNameIgnoreCase(inputName);
            UUID targetUUID = plugin.getAccountManager().getUuidByUsername(inputName);

            if (targetUUID == null || targetName == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("purge-account.not-found").replace("%target%", inputName));
                return true;
            }

            if (p != null) {
                boolean confirmed = (args.length > 1 && args[1].equalsIgnoreCase("confirm"));

                if (!confirmed) {
                    String baseMsgStr = plugin.getLanguageManager().getWithPrefix("purge-account.confirm");
                    String btnTextStr = plugin.getLanguageManager().getMessage("purge-account.button");
                    String hoverTextStr = plugin.getLanguageManager().getMessage("purge-account.hover");

                    Component baseMsg = LegacyComponentSerializer.legacySection()
                            .deserialize(baseMsgStr + " ");

                    Component confirmBtn = LegacyComponentSerializer.legacySection()
                            .deserialize(btnTextStr.replace("&", "§"))
                            .clickEvent(ClickEvent.runCommand("/resetaccount " + targetName + " confirm"))
                            .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr.replace("&", "§")));

                    plugin.getAdventure().sender(sender).sendMessage(baseMsg.append(confirmBtn));
                    return true;
                }
            }

            Player targetP = Bukkit.getPlayer(targetUUID);
            if (targetP != null && targetP.isOnline()) {
                plugin.getLoginSystem().getLoggedIn().remove(targetUUID);
                String purgeReason = plugin.getLanguageManager().getMessage("purge-account.player-kick");
                targetP.kickPlayer(purgeReason);
            }

            plugin.getPlayerDataManager().deletePlayerData(targetUUID);

            plugin.getAttemptManager().unregisterCache(targetUUID);
            plugin.getTwoFactorManager().invalidateSetup(targetUUID);

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("purge-account.admin-success").replace("%player%", targetName));

            String adminName = sender.getName();
            plugin.getLogManager().log("Admin " + adminName + " PURGED all account data for player " + targetName);

            return true;
        }

        // ==========================================
        // KOMENDA: /listaip lub /iplist
        // ==========================================
        else if (command.getName().equalsIgnoreCase("listaip") || command.getName().equalsIgnoreCase("iplist")) {
            if (!sender.hasPermission("astralogin.iplist")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                return true;
            }

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-list.generating"));

            plugin.getSchedulerManager().runAsync(() -> {
                Map<String, List<String>> ipToNamesMap = plugin.getIPManager().getIpToNamesMap();

                if (ipToNamesMap.isEmpty()) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-list.empty"));
                    return;
                }

                sender.sendMessage(plugin.getLanguageManager().getMessage("ip-list.header"));

                String format = plugin.getLanguageManager().getMessage("ip-list.format");

                for (Map.Entry<String, List<String>> entry : ipToNamesMap.entrySet()) {
                    sender.sendMessage(format
                            .replace("%ip%", entry.getKey())
                            .replace("%count%", String.valueOf(entry.getValue().size()))
                            .replace("%players%", String.join(", ", entry.getValue())));
                }

                sender.sendMessage(plugin.getLanguageManager().getMessage("ip-list.footer"));
            });

            return true;
        }

        // ==========================================
        // KOMENDA: /listakont lub /accountslist
        // ==========================================
        else if (command.getName().equalsIgnoreCase("listakont") || command.getName().equalsIgnoreCase("accountslist")) {
            if (!sender.hasPermission("astralogin.accountslist")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                return true;
            }

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("accounts-list.generating"));

            plugin.getSchedulerManager().runAsync(() -> {
                JsonObject namesSection = plugin.getGlobalDataManager().getJsonObject("usermap.names");

                if (namesSection == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("accounts-list.empty"));
                    return;
                }

                List<String> formattedAccounts = new ArrayList<>();
                String format = plugin.getLanguageManager().getMessage("accounts-list.format");
                PlayerDataManager pdm = plugin.getPlayerDataManager();

                for (String lowerName : namesSection.keySet()) {
                    String realName = namesSection.get(lowerName).getAsString();
                    UUID uuid = plugin.getAccountManager().getUuidByUsername(lowerName);

                    if (uuid != null) {
                        if (plugin.getPasswordManager().isRegistered(uuid)) {
                            String knownName = pdm.getString(uuid, "account.name");
                            if (knownName == null) {
                                knownName = realName;
                            }

                            String line = format
                                    .replace("%name%", knownName)
                                    .replace("%uuid%", uuid.toString());

                            formattedAccounts.add(line);
                        }
                    }
                }

                if (formattedAccounts.isEmpty()) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("accounts-list.empty"));
                    return;
                }

                sender.sendMessage(plugin.getLanguageManager().getMessage("accounts-list.header"));
                for (String accountLine : formattedAccounts) {
                    sender.sendMessage(accountLine);
                }
                sender.sendMessage(plugin.getLanguageManager().getMessage("accounts-list.footer"));
            });

            return true;
        }

        // ==========================================
        // KOMENDA: /przenieskonto lub /moveaccount
        // ==========================================
        else if (command.getName().equalsIgnoreCase("przenieskonto") || command.getName().equalsIgnoreCase("moveaccount")) {
            if (!sender.hasPermission("astralogin.moveaccount")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.usage"));
                return true;
            }

            String oldNickname = args[0];
            String newNickname = args[1];

            if (oldNickname.equalsIgnoreCase(newNickname)) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.player-same"));
                return true;
            }

            UUID oldUUIDObj = plugin.getAccountManager().getUuidByUsername(oldNickname);
            if (oldUUIDObj == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.player-not-exists")
                        .replace("%target%", oldNickname));
                return true;
            }

            String registeredOldName = plugin.getAccountManager().getRegisteredNameIgnoreCase(oldNickname);
            String finalOldNickname = (registeredOldName != null) ? registeredOldName : oldNickname;

            Player oldPlayerP = Bukkit.getPlayer(oldUUIDObj);
            Player newPlayerP = Bukkit.getPlayerExact(newNickname);

            if ((oldPlayerP != null && oldPlayerP.isOnline()) || (newPlayerP != null && newPlayerP.isOnline())) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.players-online")
                        .replace("%old%", finalOldNickname)
                        .replace("%new%", newNickname));
                return true;
            }

            // Sprawdzamy czy stary gracz ma fizyczny plik w data/players
            File playersFolder = new File(plugin.getDataFolder(), "data/players");
            File oldPlayerFile = new File(playersFolder, oldUUIDObj.toString() + ".json");

            if (!oldPlayerFile.exists()) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.old-player-no-data")
                        .replace("%target%", finalOldNickname));
                return true;
            }

            // Wyznaczamy UUID dla nowego gracza (jeśli nie istnieje, wyliczamy standardowy offline UUID)
            UUID newUUIDObj = plugin.getAccountManager().getUuidByUsername(newNickname);
            if (newUUIDObj == null) {
                newUUIDObj = UUID.nameUUIDFromBytes(("OfflinePlayer:" + newNickname.toLowerCase()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            File newPlayerFile = new File(playersFolder, newUUIDObj.toString() + ".json");

            // Jeśli plik docelowy nowego gracza już istnieje, blokujemy operację
            if (newPlayerFile.exists()) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.new-player-exists")
                        .replace("%uuid%", newUUIDObj.toString()));
                return true;
            }

            // Weryfikacja potwerdzenia
            if (p != null) {
                boolean confirmed = (args.length > 2 && args[2].equalsIgnoreCase("confirm"));

                if (!confirmed) {
                    String baseMsgStr = plugin.getLanguageManager().getWithPrefix("account-move.confirm");
                    String btnTextStr = plugin.getLanguageManager().getMessage("account-move.button");
                    String hoverTextStr = plugin.getLanguageManager().getMessage("account-move.hover");

                    Component baseMsg = LegacyComponentSerializer.legacySection()
                            .deserialize(baseMsgStr + " ");

                    Component confirmBtn = LegacyComponentSerializer.legacySection()
                            .deserialize(btnTextStr)
                            .clickEvent(ClickEvent.runCommand("/moveaccount " + finalOldNickname + " " + newNickname + " confirm"))
                            .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr));

                    plugin.getAdventure().sender(sender).sendMessage(baseMsg.append(confirmBtn));
                    return true;
                }
            }

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.start")
                    .replace("%old%", finalOldNickname)
                    .replace("%new%", newNickname));

            final UUID finalNewUUIDObj = newUUIDObj;
            final String oldUUIDStr = oldUUIDObj.toString();
            final String newUUIDStr = newUUIDObj.toString();

            plugin.getSchedulerManager().runAsync(() -> {
                try {
                    // 1. Czyszczenie starych pamięci podręcznych dla starego UUID
                    plugin.getPlayerDataManager().unloadPlayer(oldUUIDObj);
                    plugin.getAttemptManager().unregisterCache(oldUUIDObj);
                    plugin.getTwoFactorManager().invalidateSetup(oldUUIDStr);

                    // 2. Zmiana nazwy pliku w data/players z <oldUUID>.json na <newUUID>.json
                    if (!oldPlayerFile.renameTo(newPlayerFile)) {
                        sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.error-migration"));
                        return;
                    }

                    // 3. Aktualizacja nazwy gracza w nowym pliku
                    plugin.getPlayerDataManager().set(finalNewUUIDObj, "account.name", newNickname);

                    // 4. Przepięcie mapowania w usermap w GlobalDataManager
                    plugin.getGlobalDataManager().remove("usermap.names." + finalOldNickname.toLowerCase());
                    plugin.getGlobalDataManager().remove("usermap.uuids." + finalOldNickname.toLowerCase());

                    plugin.getGlobalDataManager().set("usermap.names." + newNickname.toLowerCase(), newNickname);
                    plugin.getGlobalDataManager().set("usermap.uuids." + newNickname.toLowerCase(), newUUIDStr);
                    plugin.getGlobalDataManager().save();

                    // 5. Wywołanie pełnego reloadu z głównej klasy (działa spójnie dla wszystkich menedżerów)
                    plugin.reload();

                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.success")
                            .replace("%old%", finalOldNickname)
                            .replace("%new%", newNickname));

                    String adminName = sender.getName();
                    plugin.getLogManager().log("Player " + finalOldNickname + " has been successfully migrated to " + newNickname + " by " + adminName);

                } catch (Exception e) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.error-migration"));
                    e.printStackTrace();
                }
            });

            return true;
        }

        // ==========================================
        // KOMENDA: /wyloguj lub /logout
        // ==========================================
        else if (command.getName().equalsIgnoreCase("wyloguj") || command.getName().equalsIgnoreCase("logout")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            if (p.isOnline()) {
                String kickReason = plugin.getLanguageManager().getMessage("account.logout-success");
                p.kickPlayer(kickReason);
            }

            plugin.getSessionManager().deleteSession(p.getUniqueId());
            plugin.getLogManager().log("Player " + p.getName() + " has logged out");

            return true;
        }
        return false;
    }
}