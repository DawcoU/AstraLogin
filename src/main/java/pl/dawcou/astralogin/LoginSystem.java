package pl.dawcou.astralogin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.*;

public class LoginSystem implements CommandExecutor, TabCompleter {

    private final PasswordManager data;
    private final AstraLogin plugin;
    private final InventoryManager storage;
    private final IPManager ipManager;
    private final AttemptManager attemptManager;
    private final SpawnManager spawnManager;

    private final Set<UUID> zalogowani = new HashSet<>();
    private final Map<UUID, String> waitingFor2FA = new HashMap<>();

    public Set<UUID> getZalogowani() { return zalogowani; }
    public boolean isWaitingFor2FA(UUID uuid) { return waitingFor2FA.containsKey(uuid); }
    public void addWaitingFor2FA(UUID uuid, String uuidString) { this.waitingFor2FA.put(uuid, uuidString); }
    public void removeWaitingFor2FA(UUID uuid) { waitingFor2FA.remove(uuid); }
    public InventoryManager getStorage() { return storage; }
    public PasswordManager getData() { return data; }
    public AttemptManager getAttemptManager() { return attemptManager; }
    public IPManager getIpManager() { return this.ipManager; }

    public LoginSystem(AstraLogin plugin, PasswordManager data, InventoryManager storage, IPManager ipManager, SpawnManager spawnManager) {
        this.plugin = plugin;
        this.data = data;
        this.storage = storage;
        this.ipManager = ipManager;
        this.spawnManager = spawnManager;
        this.attemptManager = new AttemptManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("zresetujhaslo")) {
            if (!sender.hasPermission("astralogin.resetpassword")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("usage-reset-password"));
                return true;
            }

            String targetName = args[0];
            UUID targetUUID = null;
            org.bukkit.configuration.file.FileConfiguration accountsConfig = plugin.getAccountDataManager().getConfig();

            // 1. Szukamy UUID w historii kont, żeby nie lagować serwera przez getOfflinePlayer
            if (accountsConfig.getConfigurationSection("accounts") != null) {
                for (String uuidKey : accountsConfig.getConfigurationSection("accounts").getKeys(false)) {
                    String knownName = accountsConfig.getString("accounts." + uuidKey + ".last-known-name");
                    if (knownName != null && knownName.equalsIgnoreCase(targetName)) {
                        targetUUID = UUID.fromString(uuidKey);
                        targetName = knownName; // Pobieramy poprawną wielkość liter z pliku (np. DawcoU)
                        break;
                    }
                }
            }

            // 2. Jeśli nie grali u nas, to sprawdzamy tradycyjnie przez Bukkit na wszelki wypadek
            if (targetUUID == null) {
                org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                targetUUID = target.getUniqueId();
            }

            String uuidString = targetUUID.toString();

            // 3. Sprawdzamy hasło i usuwamy dane z Twoich plików passwords.yml i ips.yml
            if (!data.hasPassword(uuidString)) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-account-reset"));
                return true;
            }

            data.deletePassword(uuidString);
            ipManager.deleteIP(uuidString);

            // 4. Wywołujemy naszą metodę z managera
            plugin.getAccountDataManager().invalidateRegistration(targetUUID);

            // 5. Sukces, logi i wiadomości
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("admin-reset-password-success", "%player%", targetName));

            String adminName = sender.getName();
            plugin.getLogManager().log("Admin " + adminName + " reset password for player " + targetName);

            // 6. NAPRAWIONA LOGIKA DLA GRACZA ONLINE (Wyrzucanie i czyszczenie)
            org.bukkit.entity.Player targetP = Bukkit.getPlayer(targetUUID); // Pobieramy gracza po UUID (szybkie i bezpieczne)

            if (targetP != null && targetP.isOnline()) {
                // Usuwamy z listy zalogowanych
                zalogowani.remove(targetUUID);

                // Resetujemy próby błędnych logowań dla jego IP
                String playerIP = targetP.getAddress().getAddress().getHostAddress();
                ipManager.resetIPAttempts(playerIP);

                // Wyrzucamy gracza z serwera wiadomością z pliku językowego przez getMessage
                targetP.kick(net.kyori.adventure.text.Component.text(plugin.getLanguageManager().getMessage("player-reset-password-kick")));
            }

            return true;
        }

        if (command.getName().equalsIgnoreCase("zmienhaslo")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("only-players"));
                return true;
            }

            if (args.length != 3) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("usage-change-password"));
                return true;
            }

            String stareWpisane = args[0];
            String nowe1 = args[1];
            String nowe2 = args[2];

            // 1. SZYBKI CHECK: Czy nowe hasło jest takie samo jak stare (tekstowo)?
            // Robimy to ZANIM odpalimy BCrypta, żeby nie marnować zasobów
            if (stareWpisane.equals(nowe1)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password-is-identical"));
                return true;
            }

            // 2. Najpierw sprawdzamy stare hasło (POPRAWNIE - metodą verify)
            String obecneHasloWPliku = data.getPassword(p.getUniqueId().toString());
            if (obecneHasloWPliku == null || !PasswordManager.verifyPassword(stareWpisane, obecneHasloWPliku)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("wrong-old-password"));

                if (plugin.getConfig().getInt("features.attempts.max", 3) > 0) {
                    attemptManager.dodajProbe(p, "Password");
                }
                return true;
            }

            // 3. Sprawdzamy czy nowe hasła się zgadzają
            if (!nowe1.equals(nowe2)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("passwords-not-match"));
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

            if (nowe1.length() < min) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password-too-short").replace("%min%", String.valueOf(min)));
                return true;
            }
            if (nowe1.length() > max) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password-too-long").replace("%max%", String.valueOf(max)));
                return true;
            }

            // 5. HASZUJEMY RAZ I ZAPISUJEMY (Nowe hasło, nie stare!)
            String noweHasloHash = PasswordManager.hashPassword(nowe1);
            data.savePassword(p.getUniqueId().toString(), noweHasloHash);

            if (zalogowani.contains(p.getUniqueId())) {
                zalogowani.remove(p.getUniqueId());
                p.kick(net.kyori.adventure.text.Component.text(plugin.getLanguageManager().getMessage("success-change-password-kick")));
            } else {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("success-change-password"));
            }

            plugin.getLogManager().log("Player " + p.getName() + " Changed his password");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("setspawn")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("only-players"));
                return true;
            }

            if (args.length < 2) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn-usage", "%cmd%", "setspawn"));
                return true;
            }

            String type = args[1].toLowerCase();

            if (!p.hasPermission("astralogin.spawn")) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }

            boolean confirmed = (args.length > 2 && args[2].equalsIgnoreCase("confirm"));

            if (spawnManager.hasSpawn(type) && !confirmed) {
                // 1. Pobieramy wiadomości z Twojego managera (jako zwykłe Stringi)
                String baseMsgStr = plugin.getLanguageManager().getWithPrefix("spawn-exists", "%type%", type);
                String btnTextStr = plugin.getLanguageManager().getMessage("spawn-overwrite-button");
                String hoverTextStr = plugin.getLanguageManager().getMessage("spawn-overwrite-hover").replace("%type%", type);

                // 2. Tworzymy główną wiadomość i automatycznie pozwalamy Paperowi na parsowanie starych kolorów '&'
                net.kyori.adventure.text.Component baseMsg = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(baseMsgStr + " ");

                // 3. Tworzymy klikalny przycisk z tekstem, eventem kliknięcia oraz hoverem (podpowiedzią po najechaniu)
                net.kyori.adventure.text.Component confirmBtn = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(btnTextStr)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/astralogin setspawn " + type + " confirm"))
                        .hoverEvent(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(hoverTextStr));

                // 4. Składamy wszystko w jedną całość i wysyłamy prosto do gracza
                p.sendMessage(baseMsg.append(confirmBtn));
                return true;
            }

            if (type.equals("before_login") || type.equals("after_login")) {
                spawnManager.setSpawn(type, p);
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn-set-success", "%type%", type));
            } else {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn-invalid-type"));
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("delspawn")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("only-players"));
                return true;
            }

            if (args.length < 2) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn-usage", "%cmd%", "delspawn"));
                return true;
            }

            String type = args[1].toLowerCase();

            if (!p.hasPermission("astralogin.spawn")) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }

            // 1. LOGIKA POTWIERDZENIA
            boolean confirmed = (args.length > 2 && args[2].equalsIgnoreCase("confirm"));

            if (confirmed) {
                if (spawnManager.hasSpawn(type)) {
                    spawnManager.delSpawn(type);
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn-deleted-success", "%type%", type));
                } else {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn-does-not-exist", "%type%", type));
                }
                return true;
            }

            // 2. SPRAWDZAMY CZY W OGÓLE ISTNIEJE
            if (!spawnManager.hasSpawn(type)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn-does-not-exist", "%type%", type));
                return true;
            }

            // 3. POKAZYWANIE PRZYCISKU Z POPRAWNYM HOVEREM I KOLORAMI
            String baseMsgStr = plugin.getLanguageManager().getWithPrefix("spawn-delete-confirm", "%type%", type);
            String btnTextStr = plugin.getLanguageManager().getMessage("spawn-delete-button");
            String hoverTextStr = plugin.getLanguageManager().getMessage("spawn-delete-hover").replace("%type%", type);

            // Automatycznie parsujemy kolory '&' z Twojego managera do komponentu
            net.kyori.adventure.text.Component baseMsg = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(baseMsgStr + " ");

            // Tworzymy klikalny przycisk usuwania z hoverem
            net.kyori.adventure.text.Component confirmBtn = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(btnTextStr)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/astralogin delspawn " + type + " confirm"))
                    .hoverEvent(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(hoverTextStr));

            // Łączymy w jedno i wysyłamy nowoczesną metodą bezpośrednio do gracza
            p.sendMessage(baseMsg.append(confirmBtn));
            return true;
        }

        if (command.getName().equalsIgnoreCase("astralogin") || command.getName().equalsIgnoreCase("al")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("astralogin.reload")) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                IPSecurity.ipCheckOctets = plugin.getConfig().getInt("security.ip-security.ip-check-octets", 4);
                plugin.setLanguageManager(new LanguageManager(plugin));
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reload-success"));
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
                sender.sendMessage(plugin.getLanguageManager().parseToLegacy("<gray>------------ " + AstraLogin.PREFIX + " <gray>----------"));
                sender.sendMessage("§aPlugin created by: §eDawcoU");
                sender.sendMessage("§aPlugin version: §ev" + plugin.getDescription().getVersion());
                sender.sendMessage("");
                sender.sendMessage("§6Copyright © 2026 DawcoU All rights reserved");
                sender.sendMessage("§7-----------------------");
                return true;
            }
        }

        if (command.getName().equalsIgnoreCase("zarejestruj") || command.getName().equalsIgnoreCase("register") || command.getName().equalsIgnoreCase("reg")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("only-players"));
                return true;
            }

            if (zalogowani.contains(p.getUniqueId())) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("already-logged"));
                return true;
            }

            if (data.getPassword(p.getUniqueId().toString()) != null) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("has-account"));
                return true;
            }

            if (args.length != 2) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("usage-register"));
                return true;
            }

            if (!args[0].equals(args[1])) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("passwords-not-match"));
                return true;
            }

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

            if (args[0].length() < min) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password-too-short", "%min%", String.valueOf(min)));
                return true;
            }
            if (args[0].length() > max) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password-too-long", "%max%", String.valueOf(max)));
                return true;
            }

            // --- PRZYGOTOWANIE DANYCH DO ASYNC ----
            UUID playerUUID = p.getUniqueId();                     // Unikalne UUID gracza (do metod data managera)
            String uuidString = playerUUID.toString();            // UUID jako String (do Twoich dotychczasowych plików)
            String playerName = p.getName();                      // Nick gracza
            String ip = p.getAddress().getAddress().getHostAddress(); // IP gracza
            String passwordToHash = args[0];                      // Surowe hasło do zahashowania BCryptem

            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                // 1. Hashujemy (ciężkie dla CPU)
                String hashedPass = PasswordManager.hashPassword(passwordToHash);

                // 2. Zapisujemy dane (operacje na plikach - IO)
                data.savePassword(uuidString, hashedPass);
                ipManager.saveIP(uuidString, ip);

                // 3. Wracamy na główny wątek (Sync)
                p.getScheduler().run(plugin, synctask -> {
                    if (!p.isOnline()) return;

                    plugin.getAccountDataManager().recordRegister(playerUUID, playerName, ip);

                    // SPRAWDZAMY CZY MA JUŻ AKTYWNE 2FA (np. po restarcie hasła przez admina)
                    boolean is2FAEnabled = plugin.getAccountDataManager().getConfig().getBoolean("accounts." + uuidString + ".2fa-enabled", false);

                    if (is2FAEnabled) {
                        // Zamiast finishLogin, wrzucamy go do poczekalni 2FA!
                        addWaitingFor2FA(playerUUID, uuidString);

                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-required"));

                        Title title2fa = Title.title(
                                Component.text(plugin.getLanguageManager().getMessage("title-2fa")), // Główny tytuł
                                Component.text(plugin.getLanguageManager().getMessage("2fa-required")), // Podtytuł
                                Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                        );
                        p.showTitle(title2fa);
                        plugin.getLogManager().log("Player " + p.getName() + " registered, but has active 2FA. Waiting for code...");
                    } else {
                        finishLogin(p);
                        p.sendTitle(
                                plugin.getLanguageManager().getMessage("title-register"),
                                plugin.getLanguageManager().getMessage("subtitle-register"),
                                10, 40, 10
                        );
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("success-register"));
                        plugin.getLogManager().log("Player " + p.getName() + " registered");

                        plugin.getAccountDataManager().recordRegister(playerUUID, playerName, ip);
                    }
                }, null);
            });
            return true;
        }

        if (command.getName().equalsIgnoreCase("zaloguj") || command.getName().equalsIgnoreCase("login") || command.getName().equalsIgnoreCase("l")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("only-players"));
                return true;
            }

            String uuid = p.getUniqueId().toString();
            String pass = data.getPassword(uuid);

            if (pass == null) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("no-account"));
                return true;
            }

            if (zalogowani.contains(p.getUniqueId())) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("already-logged"));
                return true;
            }

            if (args.length == 1) {
                String inputPassword = args[0];
                String currentIP = p.getAddress().getAddress().getHostAddress();
                UUID playerUUID = p.getUniqueId();

                plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                    if (PasswordManager.verifyPassword(inputPassword, pass)) {

                        if (ipManager.getIP(uuid) == null) {
                            ipManager.saveIP(uuid, currentIP);
                        }

                        // ⚡ POBIERAMY STATUSY: Czy sesje 2FA są aktywne w konfiguracji pluginu?
                        boolean is2FASessionEnabled = plugin.getConfig().getBoolean("features.2fa.session.enabled", true);
                        boolean is2FAEnabled = plugin.getAccountDataManager().getConfig().getBoolean("accounts." + uuid + ".2fa-enabled", false);

                        // Jeśli opcja sesji 2FA jest wyłączona w configu, 'hasActive2FA' ZAWSZE traktujemy jako false
                        boolean hasActive2FA = is2FASessionEnabled && plugin.getSessionManager().hasActive2FASession(playerUUID);

                        // KOMBINACJA: Hasło poprawne, 2FA włączone, ale BRAK aktywnej sesji 2FA (lub sesje wyłączone w configu)
                        if (is2FAEnabled && !hasActive2FA) {
                            addWaitingFor2FA(playerUUID, uuid);
                            p.getScheduler().run(plugin, (sTask) -> {
                                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-required"));
                                Title title2fa = Title.title(
                                        Component.text(plugin.getLanguageManager().getMessage("title-2fa")), // Główny tytuł
                                        Component.text(plugin.getLanguageManager().getMessage("2fa-required")), // Podtytuł
                                        Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                                );
                                p.showTitle(title2fa);

                                // Zapisujemy zwykłą sesję hasła, skoro hasło było wpisane poprawnie!
                                plugin.getSessionManager().saveSession(playerUUID, currentIP);
                            }, null);
                        }
                        // KOMBINACJA: Hasło poprawne, a sesja 2FA jest aktywna (lub gracz nie ma włączonego 2FA)
                        else {
                            p.getScheduler().run(plugin, (syncTask) -> {
                                if (!p.isOnline()) return;

                                finishLogin(p);
                                p.sendTitle(
                                        plugin.getLanguageManager().getMessage("title-login"),
                                        plugin.getLanguageManager().getMessage("subtitle-login"),
                                        10, 40, 10
                                );
                                p.sendMessage(plugin.getLanguageManager().getWithPrefix("success-login"));

                                plugin.getLogManager().log("Player " + p.getName() + " logged in");
                                plugin.getAccountDataManager().recordLogin(playerUUID, p.getName(), currentIP);

                                // Zapisujemy sesję hasła
                                plugin.getSessionManager().saveSession(playerUUID, currentIP);

                                // ⚡ BEZPIECZNIK: Zapisujemy sesję 2FA na dysk TYLKO jeśli funkcja sesji 2FA jest włączona w config.yml!
                                if (is2FAEnabled && is2FASessionEnabled) {
                                    plugin.getSessionManager().saveSession2FA(playerUUID, currentIP);
                                }
                            }, null);
                        }

                    } else {
                        p.getScheduler().run(plugin, synctask -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("wrong-password"));
                            plugin.getLogManager().log("Player " + p.getName() + " entered the wrong password");

                            if (plugin.getConfig().getInt("features.attempts.max", 3) > 0) {
                                attemptManager.dodajProbe(p, "Password");
                            }
                        }, null);
                    }
                });
            } else {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("usage-login"));
            }
            return true;
        }
        return false;
    }

    public void finishLogin(Player p) {
        UUID uuid = p.getUniqueId();
        String ip = p.getAddress().getAddress().getHostAddress();

        zalogowani.add(uuid);
        storage.restore(p);
        plugin.getSessionManager().deleteSession(uuid);

        p.removePotionEffect(PotionEffectType.BLINDNESS);

        // Czyścimy próby
        attemptManager.clearAttempts(uuid);
        plugin.getIPManager().resetIPAttempts(ip);

        for (Player online : Bukkit.getOnlinePlayers()) {
            // Teraz wszyscy na serwerze znowu widzą tego gracza
            online.showPlayer(plugin, p);
        }

        // Pobieramy opcję z głównego configu pluginu
        boolean useLastLoc = plugin.getConfig().getBoolean("features.spawns.teleport-to-last-location", true);

        if (useLastLoc) {
            // Używamy nowej metody, którą dopisaliśmy do SpawnManagera
            spawnManager.teleportToLastLocation(p);
        } else {
            // Jeśli opcja jest wyłączona, wtedy leci na after_login
            spawnManager.teleport(p, "after_login");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("zresetujhaslo") && args.length == 1) return null;

        List<String> hints = new java.util.ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("astralogin") || cmd.equalsIgnoreCase("al")) {
            if (args.length == 1) {
                hints.add("info");
                if (sender.hasPermission("astralogin.reload")) hints.add("reload");
                if (sender.hasPermission("astralogin.spawn")) {
                    hints.add("setspawn");
                    hints.add("delspawn");
                }
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("delspawn"))) {
                hints.add("before_login");
                hints.add("after_login");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(java.util.stream.Collectors.toList());
    }
}