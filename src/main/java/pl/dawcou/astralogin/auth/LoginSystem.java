package pl.dawcou.astralogin.auth;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.api.events.PlayerFailedLoginEvent;
import pl.dawcou.astralogin.api.events.PlayerLoginEvent;
import pl.dawcou.astralogin.api.events.PlayerRegisterEvent;
import pl.dawcou.astralogin.auth.manage.spawn.SpawnType;
import pl.dawcou.astralogin.auth.password.PasswordValidator;
import pl.dawcou.astralogin.auth.security.attempt.AttemptType;
import pl.dawcou.astralogin.system.SoundManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class LoginSystem implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;
    private final TwoFactorSystem twoFactorSystem;

    private final Set<UUID> loggedIn = new HashSet<>();

    public LoginSystem(AstraLogin plugin) {
        this.plugin = plugin;
        this.twoFactorSystem = new TwoFactorSystem(plugin, this);
    }

    public TwoFactorSystem getTwoFactorSystem() {
        return twoFactorSystem;
    }

    // --------------------------------------------------
    // Checks whether a player UUID is currently marked as logged in
    // --------------------------------------------------
    public boolean isLoggedIn(UUID uuid) {
        return loggedIn.contains(uuid);
    }

    // --------------------------------------------------
    // Adds a player UUID to the active logged-in set
    // --------------------------------------------------
    public void addLoggedIn(UUID uuid) {
        loggedIn.add(uuid);
    }

    // --------------------------------------------------
    // Removes a player UUID from the active logged-in set
    // --------------------------------------------------
    public void removeLoggedIn(UUID uuid) {
        loggedIn.remove(uuid);
    }

    // ----------------------------------------------------------------
    // COMMAND HANDLING
    // ----------------------------------------------------------------

    @Override
    public boolean onCommand(@NonNull CommandSender sender, Command command, @NonNull String label, String[] args) {
        Player p = sender instanceof Player ? (Player) sender : null;
        String commandName = command.getName();

        if (isRegisterCommand(commandName)) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            handleRegister(p, args);
            return true;
        }

        if (isLoginCommand(commandName)) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            handleLogin(p, args);
            return true;
        }

        return false;
    }

    private boolean isRegisterCommand(String command) {
        return command.equalsIgnoreCase("zarejestruj")
                || command.equalsIgnoreCase("register")
                || command.equalsIgnoreCase("reg");
    }

    private boolean isLoginCommand(String command) {
        return command.equalsIgnoreCase("zaloguj")
                || command.equalsIgnoreCase("login")
                || command.equalsIgnoreCase("l");
    }

    // ----------------------------------------------------------------
    // REGISTRATION
    // ----------------------------------------------------------------

    private void handleRegister(Player p, String[] args) {
        UUID uuid = p.getUniqueId();

        if (loggedIn.contains(uuid)) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.already-logged"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return;
        }

        if (plugin.getPasswordManager().getPassword(uuid) != null) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.has-account"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return;
        }

        if (args.length != 2) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("register.usage"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return;
        }

        if (!args[0].equals(args[1])) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.not-match"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return;
        }

        // Hasła są już identyczne, więc wystarczy zweryfikować jedno.
        if (!validatePasswordCharacters(p, args[0])) {
            return;
        }

        int min = plugin.getConfig().getInt("features.password.min-password-length", 6);
        min = Math.max(5, min);

        int max = plugin.getConfig().getInt("features.password.max-password-length", 24);
        max = Math.min(32, max);

        if (min > max) {
            min = 6;
            max = 24;
        }

        if (args[0].length() < min) {
            p.sendMessage(
                    plugin.getLanguageManager().getWithPrefix("password.too-short")
                            .replace("%min%", String.valueOf(min))
            );
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return;
        }

        if (args[0].length() > max) {
            p.sendMessage(
                    plugin.getLanguageManager().getWithPrefix("password.too-long")
                            .replace("%max%", String.valueOf(max))
            );
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return;
        }

        String passwordToHash = args[0];
        String playerName = p.getName();
        String ip = Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress();

        plugin.getSchedulerManager().runAsync(() -> {
            var result = plugin.getPasswordManager()
                    .getPasswordHasher()
                    .hashPassword(uuid, passwordToHash);

            switch (result.status()) {
                case SUCCESS:
                    plugin.getPasswordManager().savePassword(uuid, result.hash());
                    plugin.getIPManager().saveIP(uuid, ip);

                    plugin.getSchedulerManager().runSync(() ->
                            completeRegistration(p, uuid, playerName, ip)
                    );
                    break;

                case RATE_LIMITED_SERVER:
                    plugin.getSchedulerManager().runSync(() -> sendServerBusy(p));
                    break;

                case RATE_LIMITED_PLAYER:
                    long seconds = result.remainingSeconds();
                    plugin.getSchedulerManager().runSync(() -> sendRateLimit(p, seconds));
                    break;

                case ERROR:
                default:
                    plugin.getSchedulerManager().runSync(() -> sendPasswordError(p));
                    break;
            }
        });
    }

    private boolean validatePasswordCharacters(Player p, String password) {
        PasswordValidator.ValidationResult result = PasswordValidator.validate(
                password,
                plugin.getConfig(),
                plugin.getLogger()
        );

        switch (result) {
            case INVALID_CHARACTERS:
                p.sendMessage(
                        plugin.getLanguageManager().getWithPrefix("password.validation.invalid-characters")
                );
                break;

            case ONLY_LETTERS_FORBIDDEN:
                p.sendMessage(
                        plugin.getLanguageManager().getWithPrefix("password.validation.only-letters-forbidden")
                );
                break;

            case ONLY_DIGITS_FORBIDDEN:
                p.sendMessage(
                        plugin.getLanguageManager().getWithPrefix("password.validation.only-digits-forbidden")
                );
                break;

            default:
                return true;
        }

        plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
        return false;
    }

    private void completeRegistration(Player p, UUID uuid, String playerName, String ip) {
        if (!p.isOnline()) {
            return;
        }

        plugin.getAccountManager().recordRegister(uuid, playerName);

        if (plugin.getTwoFactorManager().has2FA(uuid)) {
            twoFactorSystem.requestTwoFactor(p, false);

            plugin.getLogManager().log(
                    "Player " + p.getName() + " registered, but has active 2FA. Waiting for code..."
            );

            plugin.getSoundManager().playSound(p, SoundManager.SoundType.SECURITY_REMINDER);
            return;
        }

        PlayerRegisterEvent event = new PlayerRegisterEvent(p, ip);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        finishLogin(p);

        p.sendTitle(
                plugin.getLanguageManager().getMessage("title.register"),
                plugin.getLanguageManager().getMessage("title.register-subtitle"),
                10, 40, 10
        );

        p.sendMessage(plugin.getLanguageManager().getWithPrefix("register.success"));
        plugin.getLogManager().log("Player " + p.getName() + " registered");

        plugin.getSoundManager().playSound(p, SoundManager.SoundType.REGISTER);
    }

    // ----------------------------------------------------------------
    // LOGIN
    // ----------------------------------------------------------------

    private void handleLogin(Player p, String[] args) {
        UUID uuid = p.getUniqueId();
        String password = plugin.getPasswordManager().getPassword(uuid);

        if (password == null) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.no-account"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return;
        }

        if (loggedIn.contains(uuid)) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.already-logged"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return;
        }

        if (args.length != 1) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.usage"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return;
        }

        String inputPassword = args[0];
        String currentIP = Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress();

        plugin.getSchedulerManager().runAsync(() -> {
            var result = plugin.getPasswordManager()
                    .getPasswordHasher()
                    .verifyPassword(uuid, inputPassword, password);

            switch (result.status()) {
                case SUCCESS:
                    handlePasswordVerificationSuccess(
                            p,
                            uuid,
                            currentIP,
                            result.rehashNeeded(),
                            result.newHash()
                    );
                    break;

                case INVALID_PASSWORD:
                    plugin.getSchedulerManager().runSync(() ->
                            handleInvalidPassword(p, currentIP)
                    );
                    break;

                case RATE_LIMITED_SERVER:
                    plugin.getSchedulerManager().runSync(() -> sendServerBusy(p));
                    break;

                case RATE_LIMITED_PLAYER:
                    long seconds = result.remainingSeconds();
                    plugin.getSchedulerManager().runSync(() -> sendRateLimit(p, seconds));
                    break;

                case ERROR:
                default:
                    plugin.getSchedulerManager().runSync(() -> sendPasswordError(p));
                    break;
            }
        });
    }

    private void handlePasswordVerificationSuccess(Player p, UUID uuid, String currentIP, boolean rehashNeeded, String newHash) {
        if (rehashNeeded && newHash != null) {
            plugin.getPasswordManager().savePassword(uuid, newHash);
        }

        plugin.getIPManager().saveIP(uuid, currentIP);

        boolean is2FASessionEnabled = plugin.getConfig()
                .getBoolean("security.2fa.session.enabled", true);

        boolean is2FAEnabled = plugin.getTwoFactorManager().has2FA(uuid);

        boolean hasActive2FA = is2FASessionEnabled
                && plugin.getSessionManager()
                .getTwoFactorSessionManager()
                .hasActive2FASession(uuid, currentIP);

        if (is2FAEnabled && !hasActive2FA) {
            plugin.getSchedulerManager().runSync(() -> twoFactorSystem.requestTwoFactor(p, true));
        } else {
            plugin.getSchedulerManager().runSync(() ->
                    completePasswordLogin(p, uuid, currentIP)
            );
        }
    }

    private void completePasswordLogin(Player p, UUID uuid, String currentIP) {
        if (!p.isOnline()) {
            return;
        }

        PlayerLoginEvent event = new PlayerLoginEvent(p, currentIP);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        finishLogin(p);

        p.sendTitle(
                plugin.getLanguageManager().getMessage("title.login"),
                plugin.getLanguageManager().getMessage("title.login-subtitle"),
                10, 40, 10
        );

        p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.success"));

        plugin.getLogManager().log("Player " + p.getName() + " logged in");
        plugin.getSoundManager().playSound(p, SoundManager.SoundType.LOGIN);

        plugin.getIpTrustManager().changeTrustScore(
                currentIP,
                plugin.getIpTrustManager().getLoginSuccessPoints()
        );

        plugin.getAccountManager().recordLogin(uuid, p.getName());
    }

    private void handleInvalidPassword(Player p, String currentIP) {
        PlayerFailedLoginEvent event = new PlayerFailedLoginEvent(p, currentIP);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.wrong"));
        plugin.getLogManager().log("Player " + p.getName() + " entered the wrong password");

        plugin.getSoundManager().playSound(p, SoundManager.SoundType.WRONG_PASSWORD);

        plugin.getIpTrustManager().changeTrustScore(
                currentIP,
                plugin.getIpTrustManager().getFailedPasswordPoints()
        );

        plugin.getAttemptManager().addTrial(p, AttemptType.PASSWORD);
    }

    // ----------------------------------------------------------------
    // COMMON RESULT MESSAGES
    // ----------------------------------------------------------------

    private void sendServerBusy(Player p) {
        p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.server-busy"));
        plugin.getSoundManager().playSound(p, SoundManager.SoundType.RATE_LIMITED);
    }

    private void sendRateLimit(Player p, long remainingSeconds) {
        p.sendMessage(
                plugin.getLanguageManager().getWithPrefix("login.rate-limit")
                        .replace("%time%", TimeUtils.formatTime(remainingSeconds))
        );

        plugin.getSoundManager().playSound(p, SoundManager.SoundType.RATE_LIMITED);
    }

    private void sendPasswordError(Player p) {
        p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.error"));
        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
    }

    // ----------------------------------------------------------------
    // LOGIN COMPLETION
    // ----------------------------------------------------------------

    private void preparePlayerLogin(Player p, UUID uuid) {
        loggedIn.add(uuid);

        plugin.getInventoryManager().restore(p);
        plugin.getSessionManager().deleteSession(uuid);

        p.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    public void finishLogin(Player p) {
        UUID uuid = p.getUniqueId();
        String ip = Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress();

        preparePlayerLogin(p, uuid);

        plugin.getAttemptManager().clearAttempts(uuid);
        plugin.getIPManager().resetIPAttempts(ip);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, p);
        }

        boolean useLastLoc = plugin.getConfig()
                .getBoolean("features.spawns.teleport-to-last-location", true);

        if (useLastLoc) {
            plugin.getSpawnManager().teleportToLastLocation(p);
            plugin.getSpawnManager().deletePlayerSpawn(uuid);
        } else {
            plugin.getSpawnManager().teleport(p, SpawnType.AFTER_LOGIN);
        }

        plugin.getSeasonalEventManager().sendEventNotice(p);
    }

    public void finishSession(Player p) {
        UUID uuid = p.getUniqueId();

        preparePlayerLogin(p, uuid);

        plugin.getSoundManager().playSound(p, SoundManager.SoundType.AUTO_LOGIN);
        plugin.getSeasonalEventManager().sendEventNotice(p);
    }

    // ----------------------------------------------------------------
    // LOGOUT
    // ----------------------------------------------------------------

    public void logout(Player p) {
        if (p.isOnline()) {
            String kickReason = plugin.getLanguageManager().getMessage("account.logout-success");
            p.kickPlayer(kickReason);
        }

        plugin.getSessionManager().deleteSession(p.getUniqueId());
    }

    // ----------------------------------------------------------------
    // TAB COMPLETION
    // ----------------------------------------------------------------

    @Override
    public List<String> onTabComplete(
            @NonNull CommandSender sender,
            Command command,
            @NonNull String alias,
            String[] args
    ) {
        List<String> hints = new ArrayList<>();
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
                .collect(Collectors.toList());
    }
}