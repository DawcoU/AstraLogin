package pl.dawcou.astralogin;

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
    private final IPManager ipData;
    private final AstraLogin plugin;
    private final InventoryStorage storage;
    private final Set<UUID> zalogowani = new HashSet<>();
    private final Map<UUID, Long> sesje = new HashMap<>();
    private final Map<UUID, String> sesjeIP = new HashMap<>();
    private final LoginAttemptSystem attemptSystem;

    public LoginSystem(AstraLogin plugin, PasswordManager data, InventoryStorage storage) {
        this.plugin = plugin;
        this.data = data;
        this.storage = storage;

        // Tutaj tworzysz resztę, żeby nie musieć ich podawać w onEnable:
        this.ipData = new IPManager(plugin);
        this.attemptSystem = new LoginAttemptSystem(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (command.getName().equalsIgnoreCase("zresetujhaslo")) {
            if (!sender.hasPermission("astralogin.resetpassword")) {
                sender.sendMessage(AstraLogin.PREFIX + " " + c("messages.no-permission"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(AstraLogin.PREFIX + " " + c("messages.usage-reset"));
                return true;
            }
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!data.maHaslo(target.getUniqueId().toString())) {
                sender.sendMessage(AstraLogin.PREFIX + " " + c("messages.no-account-reset"));
                return true;
            }
            data.usunKonto(target.getUniqueId().toString());
            ipData.usunIP(target.getUniqueId().toString());
            sender.sendMessage(AstraLogin.PREFIX + " " + c("messages.admin-reset-success").replace("%player%", args[0]));
            if (target.isOnline() && target.getPlayer() != null) {
                zalogowani.remove(target.getUniqueId());
                target.getPlayer().kickPlayer(c("messages.player-reset-kick"));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("zmienhaslo")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can change their password!");
                return true;
            }
            Player p = (Player) sender;

            if (args.length != 3) {
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.usage-change-password"));
                return true;
            }

            String stareWpisane = args[0];
            String nowe1 = args[1];
            String nowe2 = args[2];

            // 1. SZYBKI CHECK: Czy nowe hasło jest takie samo jak stare (tekstowo)?
            // Robimy to ZANIM odpalimy BCrypta, żeby nie marnować zasobów.
            if (stareWpisane.equals(nowe1)) {
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.password-is-identical"));
                return true;
            }

            // 2. Najpierw sprawdzamy stare hasło (POPRAWNIE - metodą verify)
            String obecneHasloWPliku = data.getHaslo(p.getUniqueId().toString());
            if (obecneHasloWPliku == null || !HashPassword.verify(stareWpisane, obecneHasloWPliku)) {
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.wrong-old-password"));
                return true;
            }

            // 3. Sprawdzamy czy nowe hasła się zgadzają
            if (!nowe1.equals(nowe2)) {
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.passwords-not-match"));
                return true;
            }

            // 4. Sprawdzamy długość
            int min = plugin.getConfig().getInt("requirements.min-password-length");
            int max = plugin.getConfig().getInt("requirements.max-password-length");

            if (nowe1.length() < min || nowe1.length() > max) {
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.password-too-short-or-long"));
                return true;
            }

            // 5. HASZUJEMY RAZ I ZAPISUJEMY (Nowe hasło, nie stare!)
            String noweHasloHash = HashPassword.hash(nowe1);
            data.zapiszHaslo(p.getUniqueId().toString(), noweHasloHash);

            // Reszta logiki z kickowaniem...
            zalogowani.remove(p.getUniqueId());
            p.kickPlayer(c("messages.success-change-password"));

            return true;
        }

        if (command.getName().equalsIgnoreCase("astralogin") || command.getName().equalsIgnoreCase("al")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("astralogin.reload")) {
                    sender.sendMessage(AstraLogin.PREFIX + " " + c("messages.no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage(AstraLogin.PREFIX + " " + c("messages.reload-success"));
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

        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (command.getName().equalsIgnoreCase("zarejestruj") || command.getName().equalsIgnoreCase("register")) {
            if (data.getHaslo(p.getUniqueId().toString()) != null) {
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.has-account"));
                return true;
            }

            if (args.length == 2) {
                if (!args[0].equals(args[1])) {
                    p.sendMessage(AstraLogin.PREFIX + " " + c("messages.passwords-not-match"));
                    return true;
                }

                int min = plugin.getConfig().getInt("requirements.min-password-length");
                int max = plugin.getConfig().getInt("requirements.max-password-length");

                if (args[0].length() < min) {
                    p.sendMessage(AstraLogin.PREFIX + " " + c("messages.password-too-short").replace("%min%", String.valueOf(min)));
                    return true;
                }
                if (args[0].length() > max) {
                    p.sendMessage(AstraLogin.PREFIX + " " + c("messages.password-too-long").replace("%max%", String.valueOf(max)));
                    return true;
                }

                String ip = p.getAddress().getAddress().getHostAddress();
                data.zapiszHaslo(p.getUniqueId().toString(), HashPassword.hash(args[0]));
                ipData.zapiszIP(p.getUniqueId().toString(), ip);

                finishLogin(p);
                p.sendTitle(c("messages.title-register"), c("messages.subtitle-register"), 10, 40, 10);
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.success-register"));
                storage.restore(p);
            } else {
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.usage-register"));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("zaloguj") || command.getName().equalsIgnoreCase("login")) {
            String pass = data.getHaslo(p.getUniqueId().toString());
            if (pass == null) {
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.no-account"));
                return true;
            }
            if (zalogowani.contains(p.getUniqueId())) {
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.already-logged"));
                return true;
            }

            if (args.length == 1) {
                if (HashPassword.verify(args[0], pass)) {

                    // Sprawdzamy czy gracz ma przypisane IP
                    if (ipData.getIP(p.getUniqueId().toString()) == null) {
                        // Pobieramy aktualne IP gracza
                        String currentIP = p.getAddress().getAddress().getHostAddress();

                        // ZAPISUJEMY używając poprawnej nazwy zmiennej: currentIP
                        ipData.zapiszIP(p.getUniqueId().toString(), currentIP);

                    }

                    finishLogin(p);
                    p.sendTitle(c("messages.title-login"), c("messages.subtitle-login"), 10, 40, 10);
                    p.sendMessage(AstraLogin.PREFIX + " " + c("messages.success-login"));
                    storage.restore(p);
                } else {
                    p.sendMessage(AstraLogin.PREFIX + " " + c("messages.wrong-password"));
                    if (plugin.getConfig().getBoolean("features.max-attempts-enabled")) {
                        attemptSystem.dodajProbe(p);
                    }
                }
            } else {
                p.sendMessage(AstraLogin.PREFIX + " " + c("messages.usage-login"));
            }
            return true;
        }
        return false;
    }

    public void finishLogin(Player p) {
        zalogowani.add(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        attemptSystem.resetuj(p.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Jeśli to komenda resetu, zwracamy null (czyli listę graczy online wg Bukkit)
        if (command.getName().equalsIgnoreCase("zresetujhaslo") && args.length == 1) return null;

        List<String> hints = new java.util.ArrayList<>();
        String cmd = command.getName();

        if ((cmd.equalsIgnoreCase("astralogin") || cmd.equalsIgnoreCase("al")) && args.length == 1) {
            hints.add("info");
            if (sender.hasPermission("astralogin.reload")) {
                hints.add("reload");
            }
        }

        // Najszybsze filtrowanie podpowiedzi pod Jave 17
        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(java.util.stream.Collectors.toList());
    }

    private String c(String path) {
        String msg = plugin.getConfig().getString(path);
        if (msg == null) return "§cMissing message: " + path;
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }

    public Set<UUID> getZalogowani() { return zalogowani; }
    public Map<UUID, Long> getSesje() { return sesje; }
    public Map<UUID, String> getSesjeIP() { return sesjeIP; }
    public InventoryStorage getStorage() { return storage; }
    public PasswordManager getData() { return data; }
    public IPManager getIpData() { return ipData; }
    public LoginAttemptSystem getAttemptSystem() { return attemptSystem; }
}