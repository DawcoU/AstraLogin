package pl.dawcou.astralogin;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LoginSystem implements CommandExecutor, Listener, TabCompleter {

    private final PasswordManager data;
    private final AstraLogin plugin;
    private final InventoryManager storage;
    private final IPManager ipManager;
    private final AttemptManager attemptManager;
    private final SpawnManager spawnManager;

    private final Set<UUID> zalogowani = new HashSet<>();
    private final Map<UUID, Long> sesje = new HashMap<>();
    private final Map<UUID, String> sesjeIP = new HashMap<>();

    public Map<UUID, Long> getSesje() {
        return sesje;
    }

    public Map<UUID, String> getSesjeIP() {
        return sesjeIP;
    }

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
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!data.maHaslo(target.getUniqueId().toString())) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-account-reset"));
                return true;
            }
            data.usunKonto(target.getUniqueId().toString());
            ipManager.usunIP(target.getUniqueId().toString());
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("admin-reset-password-success", "%player%", args[0]));

            String adminName = sender.getName();
            // Pobieramy nick gracza, któremu resetujemy hasło
            String targetName = target.getName() != null ? target.getName() : args[0];

            // Zapisujemy czyste, profesjonalne info do pliku logów
            plugin.getLogManager().log("Admin " + adminName + " reset password for player " + targetName);

            if (target.isOnline() && target.getPlayer() != null) {
                Player targetP = target.getPlayer();
                zalogowani.remove(target.getUniqueId());

                ipManager.resetIPAttempts(targetP.getAddress().getAddress().getHostAddress());
                target.getPlayer().kickPlayer(plugin.getLanguageManager().getMessage("player-reset-password-kick"));
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
            String obecneHasloWPliku = data.getHaslo(p.getUniqueId().toString());
            if (obecneHasloWPliku == null || !PasswordManager.verifyPassword(stareWpisane, obecneHasloWPliku)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("wrong-old-password"));

                if (plugin.getConfig().getInt("features.attempts.max", 3) > 0) {
                    attemptManager.dodajProbe(p);
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
            data.zapiszHaslo(p.getUniqueId().toString(), noweHasloHash);

            zalogowani.remove(p.getUniqueId());
            p.kickPlayer(plugin.getLanguageManager().getMessage("success-change-password"));
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
                // 1. PREFIX I KOLORY: Używamy fromLegacyText
                String baseMsg = plugin.getLanguageManager().getWithPrefix("spawn-exists", "%type%", type);
                BaseComponent[] baseComponent = TextComponent.fromLegacyText(baseMsg + " ");

                // 2. PRZYCISK: Też z kolorami z configu
                String btnText = plugin.getLanguageManager().getMessage("spawn-overwrite-button");
                TextComponent confirmBtn = new TextComponent(TextComponent.fromLegacyText(btnText));

                confirmBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/astralogin setspawn " + type + " confirm"));

                // 3. HOVER (NAPRAWA %type%): Musimy ręcznie zamienić placeholder
                String hoverText = plugin.getLanguageManager().getMessage("spawn-overwrite-hover")
                        .replace("%type%", type); // <-- TO TEGO BRAKOWAŁO!

                confirmBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(hoverText)));

                // Składamy wiadomość, żeby prefix nie był biały
                TextComponent finalMsg = new TextComponent("");
                for (BaseComponent bc : baseComponent) {
                    finalMsg.addExtra(bc);
                }
                finalMsg.addExtra(confirmBtn);

                p.spigot().sendMessage(finalMsg);
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
            String baseMsg = plugin.getLanguageManager().getWithPrefix("spawn-delete-confirm", "%type%", type);
            BaseComponent[] message = TextComponent.fromLegacyText(baseMsg + " ");

            String btnText = plugin.getLanguageManager().getMessage("spawn-delete-button");
            TextComponent confirmBtn = new TextComponent(TextComponent.fromLegacyText(btnText));

            confirmBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/astralogin delspawn " + type + " confirm"));

            String hoverText = plugin.getLanguageManager().getMessage("spawn-delete-hover")
                    .replace("%type%", type);

            confirmBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(hoverText)));

            // Budujemy wiadomość, aby prefix nie był biały
            TextComponent finalMsg = new TextComponent("");
            for (BaseComponent bc : message) {
                finalMsg.addExtra(bc);
            }
            finalMsg.addExtra(confirmBtn);

            p.spigot().sendMessage(finalMsg);
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
                sender.sendMessage("§7------------ " + AstraLogin.PREFIX + " §7----------");
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

            if (data.getHaslo(p.getUniqueId().toString()) != null) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("has-account"));
                return true;
            }

            if (args.length == 2) {
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

                // --- PRZYGOTOWANIE DANYCH DO ASYNC ---
                String ip = p.getAddress().getAddress().getHostAddress();
                String passwordToHash = args[0];
                String uuid = p.getUniqueId().toString();

                plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                    // 1. Hashujemy (ciężkie dla CPU)
                    String hashedPass = PasswordManager.hashPassword(passwordToHash);

                    // 2. Zapisujemy dane (operacje na plikach - IO)
                    data.zapiszHaslo(uuid, hashedPass);
                    ipManager.zapiszIP(uuid, ip);

                    // 3. Wracamy na główny wątek (Sync)
                    p.getScheduler().run(plugin, synctask -> {
                        if (!p.isOnline()) return;

                        finishLogin(p);
                        p.sendTitle(
                                plugin.getLanguageManager().getMessage("title-register"),
                                plugin.getLanguageManager().getMessage("subtitle-register"),
                                10, 40, 10
                        );
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("success-register"));
                        storage.restore(p);
                        plugin.getLogManager().log("Player " + p.getName() + " Registered");
                    }, null);
                });

            } else {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("usage-register"));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("zaloguj") || command.getName().equalsIgnoreCase("login") || command.getName().equalsIgnoreCase("l")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("only-players"));
                return true;
            }

            String uuid = p.getUniqueId().toString();
            String pass = data.getHaslo(uuid);

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

                plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                    if (PasswordManager.verifyPassword(inputPassword, pass)) {

                        if (ipManager.getIP(uuid) == null) {
                            ipManager.zapiszIP(uuid, currentIP);
                        }

                        p.getScheduler().run(plugin, synctask -> {
                            if (!p.isOnline()) return;

                            finishLogin(p);
                            p.sendTitle(
                                    plugin.getLanguageManager().getMessage("title-login"),
                                    plugin.getLanguageManager().getMessage("subtitle-login"),
                                    10, 40, 10
                            );
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("success-login"));
                            storage.restore(p);
                            plugin.getLogManager().log("Player " + p.getName() + " logged in");
                        }, null);

                    } else {
                        p.getScheduler().run(plugin, synctask -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("wrong-password"));
                            plugin.getLogManager().log("Player " + p.getName() + " entered the wrong password");

                            // Sprawdzamy nową ścieżkę w configu
                            if (plugin.getConfig().getInt("features.attempts.max", 3) > 0) {
                                attemptManager.dodajProbe(p);
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
        sesje.remove(uuid);
        sesjeIP.remove(uuid);

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

    public Set<UUID> getZalogowani() { return zalogowani; }
    public InventoryManager getStorage() { return storage; }
    public PasswordManager getData() { return data; }
    public AttemptManager getAttemptManager() { return attemptManager; }
    public IPManager getIpManager() { return this.ipManager; }
}