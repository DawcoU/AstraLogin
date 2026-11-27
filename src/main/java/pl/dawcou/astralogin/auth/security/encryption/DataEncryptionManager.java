package pl.dawcou.astralogin.auth.security.encryption;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public final class DataEncryptionManager {

    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private static final String FORMAT_VERSION = "v1";

    private static final int NONCE_LENGTH = 12;

    private static final int TAG_LENGTH = 128;

    private static final int TAG_LENGTH_BYTES = TAG_LENGTH / 8;

    private final SecretKey key;

    private final SecureRandom random;

    public DataEncryptionManager(SecretKey key) {

        if (key == null) {
            throw new IllegalArgumentException(
                    "Encryption key cannot be null!"
            );
        }

        this.key = key;
        this.random = new SecureRandom();
    }

    /**
     * Encrypts data using AES-256-GCM.
     *
     * @param data plaintext
     * @param playerId player UUID
     * @param dataType encrypted data type
     * @return versioned Base64 representation
     */
    public String encrypt(
            String data,
            UUID playerId,
            EncryptedDataType dataType
    ) throws EncryptionException {

        validateArguments(data, playerId, dataType);

        byte[] nonce = new byte[NONCE_LENGTH];

        random.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH, nonce)
            );

            cipher.updateAAD(createAdditionalData(playerId, dataType));

            byte[] encrypted = cipher.doFinal(
                    data.getBytes(StandardCharsets.UTF_8)
            );

            String encodedNonce = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(nonce);

            String encodedData = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(encrypted);

            return FORMAT_VERSION + "."
                    + encodedNonce + "."
                    + encodedData;

        } catch (GeneralSecurityException exception) {
            throw new EncryptionException(
                    "Failed to encrypt data!",
                    exception
            );
        }
    }

    /**
     * Decrypts AES-256-GCM encrypted data.
     *
     * @param encryptedData versioned encrypted representation
     * @param playerId player UUID
     * @param dataType encrypted data type
     * @return original plaintext
     */
    public String decrypt(
            String encryptedData,
            UUID playerId,
            EncryptedDataType dataType
    ) throws EncryptionException {

        validateArguments(encryptedData, playerId, dataType);

        String[] parts = encryptedData.split("\\.", -1);

        if (parts.length != 3) {
            throw new EncryptionException(
                    "Invalid encrypted data format!"
            );
        }

        if (!FORMAT_VERSION.equals(parts[0])) {
            throw new EncryptionException(
                    "Unsupported encryption format version: " + parts[0]
            );
        }

        try {
            byte[] nonce = Base64.getUrlDecoder().decode(parts[1]);

            byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);

            if (nonce.length != NONCE_LENGTH) {
                throw new EncryptionException(
                        "Invalid encryption nonce length!"
                );
            }

            if (encrypted.length < TAG_LENGTH_BYTES) {
                throw new EncryptionException(
                        "Encrypted data is too short!"
                );
            }

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH, nonce)
            );

            cipher.updateAAD(createAdditionalData(playerId, dataType));

            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );

        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new EncryptionException(
                    "Failed to decrypt data! Data may be corrupted, modified or encrypted with another key.",
                    exception
            );
        }
    }

    private byte[] createAdditionalData(
            UUID playerId,
            EncryptedDataType dataType
    ) {

        String additionalData =
                "AstraLogin|"
                        + FORMAT_VERSION + "|"
                        + playerId + "|"
                        + dataType.name();

        return additionalData.getBytes(StandardCharsets.UTF_8);
    }

    private void validateArguments(
            String data,
            UUID playerId,
            EncryptedDataType dataType
    ) {

        if (data == null) {
            throw new IllegalArgumentException(
                    "Data cannot be null!"
            );
        }

        if (playerId == null) {
            throw new IllegalArgumentException(
                    "Player UUID cannot be null!"
            );
        }

        if (dataType == null) {
            throw new IllegalArgumentException(
                    "Encrypted data type cannot be null!"
            );
        }
    }

}