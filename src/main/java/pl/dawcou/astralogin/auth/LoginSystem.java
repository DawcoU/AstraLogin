package pl.dawcou.astralogin.auth;

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

    private final AstraLogin plugin;
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> waitingFor2FA = new HashMap<>();

    public Set<UUID> getLoggedIn() { return loggedIn; }
    public boolean isWaitingFor2FA(UUID uuid) { return waitingFor2FA.containsKey(uuid); }
    public void addWaitingFor2FA(UUID uuid, String uuidString) { this.waitingFor2FA.put(uuid, uuidString); }
    public void removeWaitingFor2FA(UUID uuid) { waitingFor2FA.remove(uuid); }

    public LoginSystem(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("zarejestruj") || command.getName().equalsIgnoreCase("register")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            if (loggedIn.contains(p.getUniqueId())) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.already-logged"));
                return true;
            }

            if (plugin.getPasswordManager().getPassword(p.getUniqueId().toString()) != null) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.has-account"));
                return true;
            }

            if (args.length != 2) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("register.usage"));
                return true;
            }

            if (!args[0].equals(args[1])) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.not-match"));
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
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.too-short").replace("%min%", String.valueOf(min)));
                return true;
            }
            if (args[0].length() > max) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.too-long").replace("%max%", String.valueOf(max)));
                return true;
            }

            // --- PRZYGOTOWANIE DANYCH DO ASYNC ----
            UUID playerUUID = p.getUniqueId(); // Unikalne UUID gracza
            String uuidString = playerUUID.toString(); // UUID jako String
            String playerName = p.getName(); // Nick gracza
            String ip = p.getAddress().getAddress().getHostAddress(); // IP gracza
            String passwordToHash = args[0]; // Surowe hasło do zahashowania BCryptem

            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                // 1. Hashujemy
                String hashedPass = PasswordManager.hashPassword(plugin, passwordToHash);

                // 2. Zapisujemy dane
                plugin.getPasswordManager().savePassword(uuidString, hashedPass);
                plugin.getIPManager().saveIP(uuidString, ip);

                // 3. Wracamy na główny wątek (Sync)
                p.getScheduler().run(plugin, synctask -> {
                    if (!p.isOnline()) return;

                    plugin.getAccountDataManager().recordRegister(playerUUID, playerName, ip);

                    // SPRAWDZAMY CZY MA JUŻ AKTYWNE 2FA (np. po restarcie hasła przez admina)
                    boolean is2FAEnabled = plugin.getAccountDataManager().getConfig().getBoolean("accounts." + uuidString + ".2fa-enabled", false);

                    if (is2FAEnabled) {
                        // Wrzucamy go do poczekalni 2FA!
                        addWaitingFor2FA(playerUUID, uuidString);

                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.required"));

                        Title title2fa = Title.title(
                                Component.text(plugin.getLanguageManager().getMessage("title.twofactor")), // Główny tytuł
                                Component.text(plugin.getLanguageManager().getMessage("twofactor.required")), // Podtytuł
                                Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                        );
                        p.showTitle(title2fa);
                        plugin.getLogManager().log("Player " + p.getName() + " registered, but has active 2FA. Waiting for code...");
                    } else {
                        finishLogin(p);
                        p.sendTitle(
                                plugin.getLanguageManager().getMessage("title.register"),
                                plugin.getLanguageManager().getMessage("title.register-subtitle"),
                                10, 40, 10
                        );
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("register.success"));
                        plugin.getLogManager().log("Player " + p.getName() + " registered");

                        plugin.getAccountDataManager().recordRegister(playerUUID, playerName, ip);
                    }
                }, null);
            });
            return true;
        }

        if (command.getName().equalsIgnoreCase("zaloguj") || command.getName().equalsIgnoreCase("login")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            String uuid = p.getUniqueId().toString();
            String password = plugin.getPasswordManager().getPassword(uuid);

            if (password == null) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.no-account"));
                return true;
            }

            if (loggedIn.contains(p.getUniqueId())) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.already-logged"));
                return true;
            }

            if (args.length == 1) {
                String inputPassword = args[0];
                String currentIP = p.getAddress().getAddress().getHostAddress();
                UUID playerUUID = p.getUniqueId();

                plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                    if (PasswordManager.verifyPassword(inputPassword, password)) {

                        plugin.getIPManager().saveIP(uuid, currentIP);

                        // POBIERAMY STATUSY: Czy sesje 2FA są aktywne w konfiguracji pluginu?
                        boolean is2FASessionEnabled = plugin.getConfig().getBoolean("features.2fa.session.enabled", true);
                        boolean is2FAEnabled = plugin.getAccountDataManager().getConfig().getBoolean("accounts." + uuid + ".2fa-enabled", false);

                        // Jeśli opcja sesji 2FA jest wyłączona w configu, 'hasActive2FA' ZAWSZE traktujemy jako false
                        boolean hasActive2FA = is2FASessionEnabled && plugin.getSessionManager().hasActive2FASession(playerUUID);

                        // KOMBINACJA: Hasło poprawne, 2FA włączone, ale BRAK aktywnej sesji 2FA (lub sesje wyłączone w configu)
                        if (is2FAEnabled && !hasActive2FA) {
                            addWaitingFor2FA(playerUUID, uuid);
                            p.getScheduler().run(plugin, (syncTask) -> {
                                p.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.required"));
                                Title title2fa = Title.title(
                                        Component.text(plugin.getLanguageManager().getMessage("title.twofactor")), // Główny tytuł
                                        Component.text(plugin.getLanguageManager().getMessage("twofactor.required")), // Podtytuł
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
                                        plugin.getLanguageManager().getMessage("title.login"),
                                        plugin.getLanguageManager().getMessage("title.login-subtitle"),
                                        10, 40, 10
                                );
                                p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.success"));

                                plugin.getLogManager().log("Player " + p.getName() + " logged in");
                                plugin.getIpTrustManager().addTrustScore(
                                        currentIP,
                                        plugin.getIpTrustManager().getLoginSuccessPoints()
                                );
                                plugin.getAccountDataManager().recordLogin(playerUUID, p.getName(), currentIP);

                                // Zapisujemy sesję hasła
                                plugin.getSessionManager().saveSession(playerUUID, currentIP);

                                // Zapisujemy sesję 2FA na dysk TYLKO jeśli funkcja sesji 2FA jest włączona w config.yml!
                                if (is2FAEnabled && is2FASessionEnabled) {
                                    plugin.getSessionManager().saveSession2FA(playerUUID, currentIP);
                                }
                            }, null);
                        }

                    } else {
                        p.getScheduler().run(plugin, synctask -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.wrong"));
                            plugin.getLogManager().log("Player " + p.getName() + " entered the wrong password");
                            plugin.getIpTrustManager().addTrustScore(
                                    currentIP,
                                    plugin.getIpTrustManager().getFailedPasswordPoints()
                            );

                            if (plugin.getConfig().getInt("features.attempts.max", 3) > 0) {
                                plugin.getAttemptManager().dodajProbe(p, "Password");
                            }
                        }, null);
                    }
                });
            } else {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.usage"));
            }
            return true;
        }
        return false;
    }

    public void finishLogin(Player p) {
        UUID uuid = p.getUniqueId();
        String ip = p.getAddress().getAddress().getHostAddress();

        loggedIn.add(uuid);
        plugin.getInventoryManager().restore(p);
        plugin.getSessionManager().deleteSession(uuid);

        p.removePotionEffect(PotionEffectType.BLINDNESS);

        // Czyścimy próby
        plugin.getAttemptManager().clearAttempts(uuid);
        plugin.getIPManager().resetIPAttempts(ip);

        for (Player online : Bukkit.getOnlinePlayers()) {
            // Teraz wszyscy na serwerze znowu widzą tego gracza
            online.showPlayer(plugin, p);
        }

        boolean useLastLoc = plugin.getConfig().getBoolean("features.spawns.teleport-to-last-location", true);

        if (useLastLoc) {
            plugin.getSpawnManager().teleportToLastLocation(p);
        } else {
            // Jeśli opcja jest wyłączona, wtedy leci na after_login
            plugin.getSpawnManager().teleport(p, "after_login");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new java.util.ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("zaloguj") || cmd.equalsIgnoreCase("login")) {
            if (args.length == 1) {
                hints.add("<password>");
            }
        }
        if (cmd.equalsIgnoreCase("zarejestruj") || cmd.equalsIgnoreCase("register")) {
            if (args.length == 1) {
                hints.add("<password>");
            }
            if (args.length == 2) {
                hints.add("<repeat>");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(java.util.stream.Collectors.toList());
    }
}