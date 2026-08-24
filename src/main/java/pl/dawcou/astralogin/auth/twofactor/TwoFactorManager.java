package pl.dawcou.astralogin.auth.twofactor;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.bukkit.configuration.file.FileConfiguration;
import pl.dawcou.astralogin.accounts.AccountManager;
import pl.dawcou.astralogin.auth.AstraLogin;
import pl.dawcou.astralogin.system.LoginUtils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TwoFactorManager {

    private final AstraLogin plugin;
    private final GoogleAuthenticator gAuth;

    // Mapa przechowująca tymczasowe klucze graczy podczas konfiguracji (UUID -> Secret Key)
    private final Map<UUID, String> pendingSetups = new HashMap<>();
    // Mapa przechowująca czas wygaśnięcia sesji konfiguracji (UUID -> Timestamp w milisekundach)
    private final Map<UUID, Long> setupExpirations = new HashMap<>();

    // Mapa przechowująca kody zapasowe tymczasowo
    private final Map<UUID, List<String>> pendingBackupCodes = new HashMap<>();

    public TwoFactorManager(AstraLogin plugin) {
        this.plugin = plugin;
        gAuth = new GoogleAuthenticator();
    }

    /**
     * Rozpoczyna proces konfiguracji 2FA dla gracza. Generuje nowy klucz sekretny.
     */
    public String startSetup(UUID uuid) {
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();

        String timeConfig = plugin.getConfig().getString("features.2fa.setup-timeout", "2 minutes");
        long timeoutMillis = LoginUtils.parseTime(timeConfig, 60000L);

        pendingSetups.put(uuid, secret);
        setupExpirations.put(uuid, System.currentTimeMillis() + timeoutMillis);

        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            codes.add(generateRandomBackupCode());
        }
        pendingBackupCodes.put(uuid, codes);

        return secret;
    }

    /**
     * Sprawdza, czy gracz jest w trakcie aktywnej konfiguracji (czy czas nie minął).
     */
    public boolean isSetupActive(UUID uuid) {
        if (!pendingSetups.containsKey(uuid) || !setupExpirations.containsKey(uuid)) {
            return false;
        }

        // Jeśli aktualny czas jest większy niż czas wygaśnięcia, czyścimy dane i zwracamy false
        if (System.currentTimeMillis() > setupExpirations.get(uuid)) {
            invalidateSetup(uuid);
            return false;
        }

        return true;
    }

    /**
     * Pobiera tymczasowy klucz sekretny gracza.
     */
    public String getPendingSecret(UUID uuid) {
        return pendingSetups.get(uuid);
    }

    public List<String> getPendingBackupCodes(UUID uuid) {
        return pendingBackupCodes.get(uuid);
    }

    /**
     * Przerywa i kasuje tymczasową konfigurację gracza (wywoływane po upływie czasu lub błędzie).
     */
    public void invalidateSetup(UUID uuid) {
        pendingSetups.remove(uuid);
        setupExpirations.remove(uuid);
        pendingBackupCodes.remove(uuid);
    }

    public void invalidateSetup(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            invalidateSetup(uuid);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Weryfikuje, czy 6-cyfrowy kod podany przez gracza zgadza się z jego tymczasowym kluczem.
     */
    public boolean verifyCode(String secret, int code) {
        return gAuth.authorize(secret, code);
    }

    public boolean useBackupCode(UUID uuid, String inputCode) {
        AccountManager accountManager = plugin.getAccountDataManager();
        FileConfiguration config = accountManager.getConfig();
        String path = "accounts." + uuid.toString() + ".backup-codes";

        List<String> savedCodes = config.getStringList(path);
        if (savedCodes.isEmpty()) return false;

        // Szukamy kodu (ignorując wielkość liter i ewentualne spakowane spacje)
        String cleanInput = inputCode.trim().toUpperCase();
        if (savedCodes.contains(cleanInput)) {
            savedCodes.remove(cleanInput); // Usuwamy kod - staje się jednorazowy!
            config.set(path, savedCodes);
            accountManager.saveConfig();
            return true;
        }

        return false;
    }

    public String getSavedSecret(UUID uuid) {
        return plugin.getAccountDataManager().getConfig().getString("accounts." + uuid + ".2fa-secret");
    }

    /**
     * Zapisuje aktywowane 2FA do pliku kont gracza accounts.yml.
     */
    public void save2FA(UUID uuid, String secret) {
        AccountManager accountManager = plugin.getAccountDataManager();
        FileConfiguration config = accountManager.getConfig();
        String path = "accounts." + uuid.toString() + ".";

        config.set(path + "2fa-enabled", true);
        config.set(path + "2fa-secret", secret);

        // Pobieramy tymczasowe kody i zapisujemy je na stałe do configu
        List<String> codes = pendingBackupCodes.get(uuid);
        if (codes != null) {
            config.set(path + "backup-codes", codes);
        }

        accountManager.saveConfig();
        invalidateSetup(uuid);
    }

    public void delete2FA(UUID uuid) {
        AccountManager accountManager = plugin.getAccountDataManager();
        FileConfiguration config = accountManager.getConfig();

        // Usuwamy dane z konfiguracji
        config.set("accounts." + uuid.toString() + ".2fa-enabled", null);
        config.set("accounts." + uuid + ".2fa-secret", null);
        config.set("accounts." + uuid + ".backup-codes", null);

        accountManager.saveConfig();

        // Czyścimy ewentualne sesje
        invalidateSetup(uuid);
    }

    public String getRemainingTime(UUID uuid) {
        if (!setupExpirations.containsKey(uuid)) return "0m 0s";

        long expiresAt = setupExpirations.get(uuid);
        long remainingSeconds = (expiresAt - System.currentTimeMillis()) / 1000;

        if (remainingSeconds < 0) return "0m 0s";

        return LoginUtils.formatTime(remainingSeconds);
    }

    private String generateRandomBackupCode() {
        int part1 = ThreadLocalRandom.current().nextInt(1000, 10000);
        int part2 = ThreadLocalRandom.current().nextInt(1000, 10000);
        return part1 + "-" + part2;
    }
}