package pl.dawcou.astralogin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

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

    // Teraz jest czyściutko i wydajnie:
    private final Set<UUID> zalogowani = new HashSet<>();
    private final Map<UUID, Long> sesje = new HashMap<>();
    private final Map<UUID, String> sesjeIP = new HashMap<>();
    private final LoginAttemptSystem attemptSystem;

    public LoginSystem(PasswordManager data, AstraLogin plugin) {
        this.data = data;
        this.plugin = plugin;
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
                sender.sendMessage("§8------------ " + AstraLogin.PREFIX + " §8------------");
                sender.sendMessage("§aPlugin created by: §eDawcoU");
                sender.sendMessage("");
                sender.sendMessage("§6Plugin files:");
                sender.sendMessage("§fconfig.yml: §7Plugin Settings");
                sender.sendMessage("§fpasswords.yml: §7Player password file §4(Read only)");
                sender.sendMessage("§fips.yml: §7Player IP file §4(Read only)");
                sender.sendMessage("§8------------------------");
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

    private void finishLogin(Player p) {
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

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // Jeśli gracz był zalogowany, zapisujemy sesję
        if (zalogowani.contains(uuid) && plugin.getConfig().getBoolean("features.session-enabled")) {
            sesje.put(uuid, System.currentTimeMillis());
            sesjeIP.put(uuid, p.getAddress().getAddress().getHostAddress());
        }

        zalogowani.remove(uuid);
        attemptSystem.resetuj(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();

        if (plugin.getConfig().getBoolean("check-updates", true) && p.hasPermission("astralogin.update")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                new UpdateChecker(plugin).getVersion(version -> {
                    if (!plugin.getDescription().getVersion().equals(version)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            p.sendMessage("");
                            p.sendMessage("§8§m----------------- " + AstraLogin.PREFIX + " §8§m-----------------");
                            p.sendMessage("§eDostępna jest nowa wersja AstraLogin: §fv" + version);
                            p.sendMessage("§aPobierz ją tutaj:");
                            p.sendMessage("§b§nhttps://modrinth.com/plugin/astralogin/version/" + version);
                            p.sendMessage("§8§m----------------------------------");
                            p.sendMessage("");
                        });
                    }
                });
            });
        }

        zalogowani.remove(p.getUniqueId());

        // --- SYSTEM SESJI (NOWOŚĆ 2.0.0) ---
        if (plugin.getConfig().getBoolean("features.session-enabled")) {
            if (sesje.containsKey(uuid) && sesjeIP.get(uuid).equals(currentIP)) {
                long lastLogout = sesje.get(uuid);
                long sessionLimit = plugin.getConfig().getLong("features.session-time") * 60 * 1000; // minuty na milisekundy

                if (System.currentTimeMillis() - lastLogout <= sessionLimit) {
                    finishLogin(p);
                    p.sendMessage(AstraLogin.PREFIX + " " + c("messages.session-restored"));
                    sesje.remove(uuid);
                    sesjeIP.remove(uuid);
                    return; // Gracz zalogowany, kończymy onJoin
                }
            }
        }

        if (plugin.getConfig().getBoolean("visuals.use-blindness")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        }

        if (data.maHaslo(p.getUniqueId().toString())) {
            p.sendMessage(AstraLogin.PREFIX + " " + c("messages.reminder-login"));
        } else {
            p.sendMessage(AstraLogin.PREFIX + " " + c("messages.reminder-register"));
        }

        if (plugin.getConfig().getBoolean("features.login-time-enabled")) {
            final int[] time = {plugin.getConfig().getInt("features.login-time-limit")};
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline() || zalogowani.contains(p.getUniqueId())) {
                        this.cancel();
                        return;
                    }
                    if (time[0] <= 0) {
                        p.kickPlayer(c("messages.kick-timeout"));
                        this.cancel();
                        return;
                    }

                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(c("messages.actionbar-timer").replace("%time%", String.valueOf(time[0]))));

                    time[0]--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }

    private String c(String path) {
        String msg = plugin.getConfig().getString(path);
        if (msg == null) return "§cMissing message: " + path;
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String name = e.getName();
        UUID uuid = e.getUniqueId();
        String currentIP = e.getAddress().getHostAddress();

        Player target = Bukkit.getPlayerExact(name);
        if (target != null && target.isOnline()) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, c("messages.already-online"));
            return;
        }

        if (plugin.getConfig().getBoolean("security.anti-multiaccount", true)) {
            String existingUUID = ipData.getGraczByIP(currentIP);

            // Jeśli IP istnieje w bazie, ale pod innym UUID niż to, które właśnie wchodzi
            if (existingUUID != null && !existingUUID.equals(uuid.toString())) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, c("messages.anti-multiaccount-kick"));
                return;
            }
        }

        if (plugin.getConfig().getBoolean("security.ip-lock-enabled")) {
            String savedIP = ipData.getIP(uuid.toString());
            if (savedIP != null) {
                if (!new IPSecurity().isIPSafe(savedIP, currentIP)) {
                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, c("messages.ip-lock-kick"));
                }
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!zalogowani.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(AstraLogin.PREFIX + " " + c("messages.reminder-login"));
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (zalogowani.contains(e.getPlayer().getUniqueId())) return;

        String m = e.getMessage().toLowerCase();
        if (m.startsWith("/zaloguj") || m.startsWith("/zarejestruj") ||
                m.startsWith("/login") || m.startsWith("/register") ||
                m.startsWith("/zmienhaslo") || m.startsWith("/changepassword")) {
            return;
        }

        e.setCancelled(true);
        e.getPlayer().sendMessage(AstraLogin.PREFIX + " " + c("messages.reminder-login"));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!zalogowani.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!zalogowani.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!zalogowani.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDmg(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && !zalogowani.contains(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!zalogowani.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInv(InventoryClickEvent e) {
        if (!zalogowani.contains(e.getWhoClicked().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!zalogowani.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!zalogowani.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player && !zalogowani.contains(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    // --- DODATKOWE BLOKADY (BUNKER MODE) ---

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player && !zalogowani.contains(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!zalogowani.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player && !zalogowani.contains(e.getTarget().getUniqueId())) {
            e.setCancelled(true);
        }
    }
}