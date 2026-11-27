package pl.dawcou.astralogin.auth.security.twofactor.method;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.password.PasswordHasher;
import pl.dawcou.astralogin.auth.security.encryption.EncryptedDataType;
import pl.dawcou.astralogin.auth.security.encryption.EncryptionException;
import pl.dawcou.astralogin.data.PlayerDataManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class TotpManager {

    private static final int BACKUP_CODE_COUNT = 5;

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;
    private final GoogleAuthenticator gAuth;
    private final SecureRandom secureRandom = new SecureRandom();

    private volatile long setupTimeoutMillis;

    private final Map<UUID, String> pendingSetups = new ConcurrentHashMap<>();
    private final Map<UUID, Long> setupExpirations = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> pendingBackupCodes = new ConcurrentHashMap<>();

    public TotpManager(AstraLogin plugin, PlayerDataManager playerDataManager, long setupTimeoutMillis) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.setupTimeoutMillis = Math.max(1L, setupTimeoutMillis);
        this.gAuth = new GoogleAuthenticator();
    }

    // --------------------------------------------------
    // Configuration and lifecycle
    // --------------------------------------------------

    public void updateSetupTimeout(long setupTimeoutMillis) {
        this.setupTimeoutMillis = Math.max(1L, setupTimeoutMillis);
    }

    public void reload() {
        String configuredTimeout = plugin.getConfig().getString("security.2fa.setup-timeout", "2 minutes");
        updateSetupTimeout(TimeUtils.parseTime(configuredTimeout, 120000L));
    }

    // --------------------------------------------------
    // Setup lifecycle and timeouts
    // --------------------------------------------------

    public String startSetup(UUID uuid) {
        invalidateSetup(uuid);

        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();

        pendingSetups.put(uuid, secret);
        setupExpirations.put(uuid, System.currentTimeMillis() + setupTimeoutMillis);
        generatePendingBackupCodes(uuid);

        return secret;
    }

    public List<String> startBackupCodeSetup(UUID uuid) {
        if (uuid == null) {
            return List.of();
        }

        generatePendingBackupCodes(uuid);
        return getPendingBackupCodes(uuid);
    }

    public boolean isSetupActive(UUID uuid) {
        String secret = pendingSetups.get(uuid);
        Long expiration = setupExpirations.get(uuid);

        if (secret == null || expiration == null) {
            return false;
        }

        if (System.currentTimeMillis() >= expiration) {
            invalidateSetup(uuid);
            return false;
        }

        return true;
    }

    public String getPendingSecret(UUID uuid) {
        return pendingSetups.get(uuid);
    }

    public List<String> getPendingBackupCodes(UUID uuid) {
        List<String> codes = pendingBackupCodes.get(uuid);
        return codes == null ? null : new ArrayList<>(codes);
    }

    public String getRemainingTime(UUID uuid) {
        Long expiration = setupExpirations.get(uuid);
        if (expiration == null) {
            return "0m 0s";
        }

        long remainingSeconds = (expiration - System.currentTimeMillis()) / 1000;
        if (remainingSeconds <= 0) {
            invalidateSetup(uuid);
            return "0m 0s";
        }

        return TimeUtils.formatTime(remainingSeconds);
    }

    public void invalidateSetup(UUID uuid) {
        if (uuid == null) {
            return;
        }

        pendingSetups.remove(uuid);
        setupExpirations.remove(uuid);
        pendingBackupCodes.remove(uuid);
    }

    // --------------------------------------------------
    // TOTP verification and storage
    // --------------------------------------------------

    public boolean verifyCode(String secret, int code) {
        if (secret == null || secret.isBlank() || code < 0 || code > 999999) {
            return false;
        }

        try {
            return gAuth.authorize(secret, code);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public String getSavedSecret(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        String encryptedSecret = playerDataManager.getString(uuid, "security.two-factor.secret");
        if (encryptedSecret == null || encryptedSecret.isBlank()) {
            return null;
        }

        // Backwards compatibility for secrets stored before encryption was added.
        if (!encryptedSecret.startsWith("v1.")) {
            return encryptedSecret;
        }

        try {
            return plugin.getEncryptionManager().decrypt(encryptedSecret, uuid, EncryptedDataType.TWO_FACTOR_SECRET);
        } catch (EncryptionException exception) {
            plugin.getLogger().warning("Could not decrypt TOTP secret for player " + uuid + ".");
            return null;
        }
    }

    public void save2FA(UUID uuid, String secret, Consumer<Boolean> callback) {
        if (uuid == null || secret == null || secret.isBlank() || !isSetupActive(uuid) || !secret.equals(pendingSetups.get(uuid))) {
            dispatchBoolean(callback, false);
            return;
        }

        hashPendingBackupCodes(uuid, hashedCodes -> {
            if (hashedCodes == null) {
                dispatchBoolean(callback, false);
                return;
            }

            // Setup could expire while backup codes were being hashed asynchronously.
            if (!isSetupActive(uuid) || !secret.equals(pendingSetups.get(uuid))) {
                dispatchBoolean(callback, false);
                return;
            }

            final String encryptedSecret;
            try {
                encryptedSecret = plugin.getEncryptionManager().encrypt(secret, uuid, EncryptedDataType.TWO_FACTOR_SECRET);
            } catch (EncryptionException exception) {
                plugin.getLogger().severe("Could not encrypt TOTP secret for player " + uuid + ".");
                dispatchBoolean(callback, false);
                return;
            }

            playerDataManager.set(uuid, "security.two-factor.enabled", true);
            playerDataManager.set(uuid, "security.two-factor.method", "TOTP");
            playerDataManager.set(uuid, "security.two-factor.secret", encryptedSecret);

            saveBackupCodes(uuid, hashedCodes);
            invalidateSetup(uuid);
            dispatchBoolean(callback, true);
        });
    }

    // --------------------------------------------------
    // Backup codes generation and verification
    // --------------------------------------------------

    public void savePendingBackupCodes(UUID uuid, Consumer<Boolean> callback) {
        if (uuid == null) {
            dispatchBoolean(callback, false);
            return;
        }

        hashPendingBackupCodes(uuid, hashedCodes -> {
            if (hashedCodes == null) {
                dispatchBoolean(callback, false);
                return;
            }

            saveBackupCodes(uuid, hashedCodes);
            pendingBackupCodes.remove(uuid);
            dispatchBoolean(callback, true);
        });
    }

    public void useBackupCode(UUID uuid, String inputCode, Consumer<PasswordHasher.VerificationResult> callback) {
        if (uuid == null || inputCode == null || inputCode.isBlank()) {
            dispatchBackupResult(callback, invalidPasswordResult());
            return;
        }

        List<String> savedCodes = getSavedBackupCodes(uuid);
        if (savedCodes.isEmpty()) {
            dispatchBackupResult(callback, invalidPasswordResult());
            return;
        }

        String cleanInput = inputCode.trim().toUpperCase();

        plugin.getSchedulerManager().runAsync(() -> {
            String matchedCode = null;
            PasswordHasher.VerificationResult lastResult = null;

            try {
                for (String savedCode : savedCodes) {
                    PasswordHasher.VerificationResult result = plugin.getPasswordManager()
                            .getPasswordHasher()
                            .verifyPassword(cleanInput, savedCode);

                    lastResult = result;
                    if (result == null) {
                        continue;
                    }

                    switch (result.status()) {
                        case SUCCESS -> matchedCode = savedCode;
                        case INVALID_PASSWORD -> {
                            // Backwards compatibility for legacy plain-text backup codes.
                            if (savedCode.equalsIgnoreCase(cleanInput)) {
                                matchedCode = savedCode;
                            }
                        }
                        default -> {
                        }
                    }

                    if (matchedCode != null) {
                        break;
                    }
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not verify a backup code: " + exception.getMessage());
            }

            final String codeToRemove = matchedCode;
            final PasswordHasher.VerificationResult finalResult = lastResult;

            if (codeToRemove == null) {
                dispatchBackupResult(callback, finalResult != null ? finalResult : invalidPasswordResult());
                return;
            }

            plugin.getSchedulerManager().runSync(() -> {
                // Re-read stored codes to avoid two concurrent requests consuming the same code.
                List<String> currentCodes = getSavedBackupCodes(uuid);
                boolean removed = currentCodes.removeIf(code -> code.equals(codeToRemove));

                if (!removed) {
                    if (callback != null) {
                        callback.accept(invalidPasswordResult());
                    }
                    return;
                }

                saveBackupCodes(uuid, currentCodes);
                if (callback != null) {
                    callback.accept(successResult());
                }
            });
        });
    }

    private void generatePendingBackupCodes(UUID uuid) {
        Set<String> generated = new HashSet<>();
        while (generated.size() < BACKUP_CODE_COUNT) {
            generated.add(generateRandomBackupCode());
        }
        pendingBackupCodes.put(uuid, new ArrayList<>(generated));
    }

    private void hashPendingBackupCodes(UUID uuid, Consumer<List<String>> callback) {
        List<String> codes = getPendingBackupCodes(uuid);
        if (codes == null || codes.isEmpty()) {
            plugin.getSchedulerManager().runSync(() -> {
                if (callback != null) {
                    callback.accept(null);
                }
            });
            return;
        }

        plugin.getSchedulerManager().runAsync(() -> {
            List<String> hashedCodes = new ArrayList<>();

            try {
                for (String code : codes) {
                    String cleanCode = code.trim().toUpperCase();
                    String hashed = plugin.getPasswordManager()
                            .getPasswordHasher()
                            .hashPassword(cleanCode)
                            .hash();

                    if (hashed == null || hashed.isBlank()) {
                        throw new IllegalStateException("PasswordHasher returned an empty backup code hash.");
                    }

                    hashedCodes.add(hashed);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not hash 2FA backup codes: " + exception.getMessage());
                plugin.getSchedulerManager().runSync(() -> {
                    if (callback != null) {
                        callback.accept(null);
                    }
                });
                return;
            }

            plugin.getSchedulerManager().runSync(() -> {
                if (callback != null) {
                    callback.accept(hashedCodes);
                }
            });
        });
    }

    private String generateRandomBackupCode() {
        int part1 = 1000 + secureRandom.nextInt(9000);
        int part2 = 1000 + secureRandom.nextInt(9000);
        return part1 + "-" + part2;
    }

    private List<String> getSavedBackupCodes(UUID uuid) {
        List<String> codes = new ArrayList<>();
        JsonElement element = playerDataManager.getElement(uuid, "security.two-factor.backup-codes");

        if (element == null || element.isJsonNull()) {
            return codes;
        }

        if (element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                    String code = entry.getAsString().trim();
                    if (!code.isEmpty()) {
                        codes.add(code);
                    }
                }
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            for (String code : element.getAsString().split(",")) {
                String cleanCode = code.trim();
                if (!cleanCode.isEmpty()) {
                    codes.add(cleanCode);
                }
            }
        }

        return codes;
    }

    private void saveBackupCodes(UUID uuid, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            playerDataManager.remove(uuid, "security.two-factor.backup-codes");
            return;
        }

        JsonArray array = new JsonArray();
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                array.add(new JsonPrimitive(code));
            }
        }

        if (array.isEmpty()) {
            playerDataManager.remove(uuid, "security.two-factor.backup-codes");
            return;
        }

        playerDataManager.set(uuid, "security.two-factor.backup-codes", array);
    }

    // --------------------------------------------------
    // Result helpers
    // --------------------------------------------------

    private void dispatchBoolean(Consumer<Boolean> callback, boolean value) {
        if (callback == null) {
            return;
        }
        plugin.getSchedulerManager().runSync(() -> callback.accept(value));
    }

    private void dispatchBackupResult(Consumer<PasswordHasher.VerificationResult> callback, PasswordHasher.VerificationResult result) {
        if (callback == null) {
            return;
        }
        plugin.getSchedulerManager().runSync(() -> callback.accept(result));
    }

    private PasswordHasher.VerificationResult invalidPasswordResult() {
        return new PasswordHasher.VerificationResult(PasswordHasher.HashStatus.INVALID_PASSWORD, false, null);
    }

    private PasswordHasher.VerificationResult successResult() {
        return new PasswordHasher.VerificationResult(PasswordHasher.HashStatus.SUCCESS, false, null);
    }
}