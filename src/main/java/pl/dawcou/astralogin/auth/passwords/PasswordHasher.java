package pl.dawcou.astralogin.auth.passwords;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.mindrot.jbcrypt.BCrypt;
import pl.dawcou.astralogin.AstraLogin;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class PasswordHasher {

    private final AstraLogin plugin;
    private final boolean argon2Available = true;
    private final SecureRandom secureRandom = new SecureRandom();

    // Globalny czas ostatniego przeliczenia hasła na serwerze (w ms)
    private final Semaphore hashingSemaphore;
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    public PasswordHasher(AstraLogin plugin) {
        this.plugin = plugin;

        int rawMaxConcurrent = plugin.getConfig().getInt("security.hashing.rate-limit.max-concurrent-hashings", 2);
        // Gwarantuje przedział od 1 do liczby rdzeni procesora
        int safeMaxConcurrent = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), rawMaxConcurrent));

        this.hashingSemaphore = new Semaphore(safeMaxConcurrent);
    }

    public enum HashAlgorithm {
        ARGON2ID,
        BCRYPT
    }

    /*-----------------------------------------------------------------------------------
     * Statusy weryfikacji uwzględniające limiter prędkości.
     -----------------------------------------------------------------------------------*/
    public enum HashStatus {
        SUCCESS,
        INVALID_PASSWORD,
        RATE_LIMITED_PLAYER,   // Gracz zbyt szybko próbuje ponownego logowania
        RATE_LIMITED_SERVER,   // Serwer przetwarza maksymalną liczbę haseł w locie (max-concurrent-hashings)
        ERROR
    }

    public record VerificationResult(HashStatus status, boolean rehashNeeded, String newHash, long remainingSeconds) {
        // Konstruktor pomocniczy dla zwykłych statusów (bez czasu)
            public VerificationResult(HashStatus status, boolean success, String newHash) {
                this(status, success, newHash, 0L);
            }

            public boolean isSuccess() {
                return status == HashStatus.SUCCESS;
            }
        }

    // --- GENEROWANIE HASHA ---
    public String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            return null;
        }

        String algorithmStr = plugin.getConfig().getString("security.hashing.algorithm", "ARGON2ID");
        HashAlgorithm algorithm;

        try {
            algorithm = HashAlgorithm.valueOf(algorithmStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            algorithm = HashAlgorithm.ARGON2ID;
        }

        if (algorithm == HashAlgorithm.ARGON2ID && !argon2Available) {
            algorithm = HashAlgorithm.BCRYPT;
        }

        if (algorithm == HashAlgorithm.BCRYPT) {
            return hashBCrypt(password);
        } else {
            return hashArgon2(password);
        }
    }

    private String hashBCrypt(String password) {
        int cost = plugin.getConfig().getInt("security.hashing.bcrypt.cost", 12);
        cost = Math.max(8, Math.min(16, cost));

        try {
            return BCrypt.hashpw(password, BCrypt.gensalt(cost));
        } catch (Exception e) {
            plugin.getLogger().severe("Error while hashing password with BCrypt: " + e.getMessage());
            return null;
        }
    }

    private String hashArgon2(String password) {
        if (!argon2Available) {
            return hashBCrypt(password);
        }

        int iterations = plugin.getConfig().getInt("security.hashing.argon2.iterations", 3);
        iterations = Math.max(1, Math.min(10, iterations));

        int memoryKb = plugin.getConfig().getInt("security.hashing.argon2.memory-kb", 32768);
        memoryKb = Math.max(4096, Math.min(131072, memoryKb));

        int parallelism = plugin.getConfig().getInt("security.hashing.argon2.parallelism", 1);
        parallelism = Math.max(1, Math.min(4, parallelism));

        char[] passArray = password.toCharArray();
        try {
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);

            byte[] hash = generateArgon2idBytes(passArray, salt, iterations, memoryKb, parallelism, 32);

            Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
            return String.format("$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s",
                    memoryKb, iterations, parallelism,
                    encoder.encodeToString(salt),
                    encoder.encodeToString(hash));
        } catch (Throwable t) {
            plugin.getLogger().severe("Error while hashing password with Argon2: " + t.getMessage());
            return null;
        } finally {
            wipeArray(passArray);
        }
    }

    // --- WERYFIKACJA ORAZ MIGRACJA ---
    public VerificationResult verifyPassword(UUID playerUuid, String rawPassword, String storedHash) {
        if (storedHash == null || rawPassword == null || rawPassword.isEmpty() || storedHash.isEmpty()) {
            return new VerificationResult(HashStatus.INVALID_PASSWORD, false, null);
        }

        long now = System.currentTimeMillis();
        long rawCooldownMs = plugin.getConfig().getLong("security.hashing.rate-limit.player-cooldown-ms", 2000L);
        long safeCooldownMs = Math.max(100L, Math.min(60000L, rawCooldownMs));

        if (playerUuid != null) {
            long lastPlayerTry = playerCooldowns.getOrDefault(playerUuid, 0L);
            long timePassed = now - lastPlayerTry;

            if (timePassed < safeCooldownMs) {
                long remainingMs = safeCooldownMs - timePassed;
                // Zaokrąglenie w górę do pełnych sekund (np. 1500 ms -> 2 sekundy)
                long remainingSeconds = (long) Math.ceil(remainingMs / 1000.0);
                return new VerificationResult(HashStatus.RATE_LIMITED_PLAYER, false, null, remainingSeconds);
            }
            playerCooldowns.put(playerUuid, now);
        }

        // 2. Rezerwacja wolnego slotu CPU
        if (!hashingSemaphore.tryAcquire()) {
            return new VerificationResult(HashStatus.RATE_LIMITED_SERVER, false, null);
        }

        try {
            boolean matches = false;
            boolean isArgonHash = storedHash.startsWith("$argon2id$");
            boolean isBCryptHash = storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$");

            char[] passArray = rawPassword.toCharArray();

            try {
                if (isArgonHash) {
                    if (argon2Available) {
                        matches = verifyArgon2(passArray, storedHash);
                    } else {
                        plugin.getLogger().warning("Cannot verify Argon2 password because native library is not available!");
                        return new VerificationResult(HashStatus.ERROR, false, null);
                    }
                } else if (isBCryptHash) {
                    matches = BCrypt.checkpw(rawPassword, storedHash);
                }
            } finally {
                wipeArray(passArray);
            }

            if (!matches) {
                return new VerificationResult(HashStatus.INVALID_PASSWORD, false, null);
            }

            String preferredAlgo = plugin.getConfig().getString("security.hashing.algorithm", "ARGON2ID").toUpperCase();
            if (preferredAlgo.equals("ARGON2ID") && !argon2Available) {
                preferredAlgo = "BCRYPT";
            }

            boolean needsRehash = false;
            if (preferredAlgo.equals("ARGON2ID") && !isArgonHash) {
                needsRehash = true;
            } else if (preferredAlgo.equals("BCRYPT") && !isBCryptHash) {
                needsRehash = true;
            }

            String newHash = null;
            if (needsRehash) {
                newHash = hashPassword(rawPassword);
            }

            return new VerificationResult(HashStatus.SUCCESS, needsRehash, newHash);

        } catch (Throwable t) {
            plugin.getLogger().severe("Error while verifying password: " + t.getMessage());
            return new VerificationResult(HashStatus.ERROR, false, null);
        } finally {
            hashingSemaphore.release(); // Zwalnia slot semafora
        }
    }

    // --- METODY POMOCNICZE BOUNCYCASTLE ARGON2 ---
    private byte[] generateArgon2idBytes(char[] password, byte[] salt, int iterations, int memoryKb, int parallelism, int outputLength) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKb)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] result = new byte[outputLength];
        generator.generateBytes(password, result, 0, result.length);
        return result;
    }

    private boolean verifyArgon2(char[] password, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length < 6) {
            return false;
        }

        // Standard PHC format: $argon2id$v=19$m=32768,t=3,p=1$salt$hash
        int memoryKb = 32768;
        int iterations = 3;
        int parallelism = 1;

        String[] params = parts[3].split(",");
        for (String param : params) {
            String[] kv = param.split("=");
            if (kv.length == 2) {
                switch (kv[0]) {
                    case "m" -> memoryKb = Integer.parseInt(kv[1]);
                    case "t" -> iterations = Integer.parseInt(kv[1]);
                    case "p" -> parallelism = Integer.parseInt(kv[1]);
                }
            }
        }

        Base64.Decoder decoder = Base64.getDecoder();
        byte[] salt = decoder.decode(parts[4]);
        byte[] expectedHash = decoder.decode(parts[5]);

        byte[] actualHash = generateArgon2idBytes(password, salt, iterations, memoryKb, parallelism, expectedHash.length);

        if (actualHash.length != expectedHash.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < actualHash.length; i++) {
            result |= actualHash[i] ^ expectedHash[i];
        }
        return result == 0;
    }

    private void wipeArray(char[] array) {
        if (array != null) {
            Arrays.fill(array, '\0');
        }
    }

    public void cleanupPlayer(UUID playerUuid) {
        if (playerUuid != null) {
            playerCooldowns.remove(playerUuid);
        }
    }
}