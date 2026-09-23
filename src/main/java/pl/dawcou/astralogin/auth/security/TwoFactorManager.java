package pl.dawcou.astralogin.auth.security;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.passwords.PasswordHasher;
import pl.dawcou.astralogin.data.PlayerDataManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//--------------------------------------------------
// Menedżer dwuetapowej weryfikacji (2FA) oparty na PlayerDataManager
//--------------------------------------------------
public class TwoFactorManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;
    private final GoogleAuthenticator gAuth;

    private final SecureRandom secureRandom = new SecureRandom();

    // Mapa przechowująca tymczasowe klucze graczy podczas konfiguracji (UUID -> Secret Key)
    private final Map<UUID, String> pendingSetups = new ConcurrentHashMap<>();
    // Mapa przechowująca czas wygaśnięcia sesji konfiguracji (UUID -> Timestamp w milisekundach)
    private final Map<UUID, Long> setupExpirations = new ConcurrentHashMap<>();

    // Mapa przechowująca kody zapasowe tymczasowo
    private final Map<UUID, List<String>> pendingBackupCodes = new ConcurrentHashMap<>();

    public TwoFactorManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.gAuth = new GoogleAuthenticator();
    }

    /**
     * Rozpoczyna proces konfiguracji 2FA dla gracza. Generuje nowy klucz sekretny.
     */
    public String startSetup(UUID uuid) {
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();

        String timeConfig = plugin.getConfig().getString("security.2fa.setup-timeout", "2 minutes");
        long timeoutMillis = TimeUtils.parseTime(timeConfig, 60000L);

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
        } catch (IllegalArgumentException ignored) {}
    }

    /**
     * Weryfikuje, czy 6-cyfrowy kod podany przez gracza zgadza się z jego tymczasowym kluczem.
     */
    public boolean verifyCode(String secret, int code) {
        return gAuth.authorize(secret, code);
    }

    public void useBackupCode(UUID uuid, String inputCode, java.util.function.Consumer<PasswordHasher.VerificationResult> callback) {
        List<String> savedCodes = getSavedBackupCodes(uuid);
        if (savedCodes.isEmpty()) {
            callback.accept(new PasswordHasher.VerificationResult(
                    PasswordHasher.HashStatus.INVALID_PASSWORD, false, null
            ));
            return;
        }

        String cleanInput = inputCode.trim().toUpperCase();

        plugin.getSchedulerManager().runAsync(() -> {
            String matchedCodeToRemove = null;
            PasswordHasher.VerificationResult lastResult = null;

            for (String savedCode : savedCodes) {
                var result = plugin.getPasswordManager().getPasswordHasher().verifyPassword(uuid, cleanInput, savedCode);
                lastResult = result;

                switch (result.status()) {
                    case SUCCESS -> matchedCodeToRemove = savedCode;
                    case INVALID_PASSWORD -> {
                        // Wariancja dla starych kodów bez hashowania (czysty tekst)
                        if (savedCode.equalsIgnoreCase(cleanInput)) {
                            matchedCodeToRemove = savedCode;
                        }
                    }
                    default -> { }
                }

                if (matchedCodeToRemove != null) {
                    break;
                }
            }

            final String finalCodeToRemove = matchedCodeToRemove;
            final var finalResult = lastResult;

            if (finalCodeToRemove != null) {
                plugin.getSchedulerManager().runSync(() -> {
                    savedCodes.remove(finalCodeToRemove);

                    if (savedCodes.isEmpty()) {
                        playerDataManager.remove(uuid, "2fa.backup-codes");
                    } else {
                        saveBackupCodes(uuid, savedCodes);
                    }

                    callback.accept(new PasswordHasher.VerificationResult(
                            PasswordHasher.HashStatus.SUCCESS, false, null
                    ));
                });
            } else {
                plugin.getSchedulerManager().runSync(() -> callback.accept(
                        finalResult != null ? finalResult : new PasswordHasher.VerificationResult(PasswordHasher.HashStatus.INVALID_PASSWORD, false, null)
                ));
            }
        });
    }

    public String getSavedSecret(UUID uuid) {
        return playerDataManager.getString(uuid, "2fa.secret");
    }

    /**
     * Zapisuje aktywowane 2FA do pliku gracza w JSON.
     */
    public void save2FA(UUID uuid, String secret, Runnable onComplete) {
        List<String> codes = pendingBackupCodes.get(uuid);

        plugin.getSchedulerManager().runAsync(() -> {
            List<String> hashedCodes = new ArrayList<>();

            if (codes != null) {
                for (String code : codes) {
                    String cleanCode = code.trim().toUpperCase();
                    String hashed = plugin.getPasswordManager().getPasswordHasher().hashPassword(cleanCode);

                    if (hashed != null) {
                        hashedCodes.add(hashed);
                    } else {
                        hashedCodes.add(cleanCode);
                    }
                }
            }

            plugin.getSchedulerManager().runSync(() -> {
                playerDataManager.set(uuid, "2fa.enabled", true);
                playerDataManager.set(uuid, "2fa.secret", secret);

                if (!hashedCodes.isEmpty()) {
                    saveBackupCodes(uuid, hashedCodes);
                }

                invalidateSetup(uuid);

                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    public void delete2FA(UUID uuid) {
        playerDataManager.remove(uuid, "2fa.enabled");
        playerDataManager.remove(uuid, "2fa.secret");
        playerDataManager.remove(uuid, "2fa.backup-codes");

        // Czyścimy ewentualne sesje
        invalidateSetup(uuid);
    }

    public boolean has2FA(UUID uuid) {
        return playerDataManager.getBoolean(uuid, "2fa.enabled", false);
    }

    public String getRemainingTime(UUID uuid) {
        if (!setupExpirations.containsKey(uuid)) return "0m 0s";

        long expiresAt = setupExpirations.get(uuid);
        long remainingSeconds = (expiresAt - System.currentTimeMillis()) / 1000;

        if (remainingSeconds < 0) return "0m 0s";

        return TimeUtils.formatTime(remainingSeconds);
    }

    private String generateRandomBackupCode() {
        int part1 = 1000 + secureRandom.nextInt(9000);
        int part2 = 1000 + secureRandom.nextInt(9000);
        return part1 + "-" + part2;
    }

    // Helpery do zapisu/odczytu listy kodów z PlayerDataManager
    private List<String> getSavedBackupCodes(UUID uuid) {
        List<String> list = new ArrayList<>();
        String raw = playerDataManager.getString(uuid, "2fa.backup-codes");
        if (raw != null && !raw.isEmpty()) {
            for (String code : raw.split(",")) {
                if (!code.isEmpty()) list.add(code);
            }
        }
        return list;
    }

    private void saveBackupCodes(UUID uuid, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            playerDataManager.remove(uuid, "2fa.backup-codes");
            return;
        }
        playerDataManager.set(uuid, "2fa.backup-codes", String.join(",", codes));
    }
}