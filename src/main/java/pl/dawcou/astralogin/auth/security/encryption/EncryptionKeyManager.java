package pl.dawcou.astralogin.auth.security.encryption;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.util.Base64;

public record EncryptionKeyManager(Path keyPath) {

    private static final int KEY_SIZE = 256;

    private static final String KEY_ALGORITHM = "AES";

    public EncryptionKeyManager(Path keyPath) {
        this.keyPath = keyPath
                .resolve("security")
                .resolve("encryption.key");
    }

    public SecretKey loadOrCreateKey()
            throws IOException, GeneralSecurityException {

        Files.createDirectories(keyPath.getParent());

        if (Files.exists(keyPath)) {
            return loadKey();
        }

        SecretKey key = generateKey();

        try {
            createKeyFile(key);
        } catch (FileAlreadyExistsException ignored) {
            // Inny proces utworzył klucz.
        }

        return loadKey();
    }

    private SecretKey generateKey()
            throws GeneralSecurityException {

        KeyGenerator generator =
                KeyGenerator.getInstance(KEY_ALGORITHM);

        generator.init(KEY_SIZE);

        return generator.generateKey();
    }

    private void createKeyFile(SecretKey key) throws IOException {

        byte[] encoded = Base64.getEncoder()
                .encode(key.getEncoded());

        Path temporaryFile = null;

        try {
            if (Files.getFileStore(keyPath.getParent())
                    .supportsFileAttributeView("posix")) {

                temporaryFile = Files.createTempFile(
                        keyPath.getParent(),
                        "encryption-",
                        ".tmp",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rw-------")
                        )
                );

            } else {
                temporaryFile = Files.createTempFile(
                        keyPath.getParent(),
                        "encryption-",
                        ".tmp"
                );
            }

            Files.write(
                    temporaryFile,
                    encoded,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            // Nie nadpisujemy istniejącego klucza.
            Files.move(temporaryFile, keyPath);

            temporaryFile = null;

        } finally {
            if (temporaryFile != null) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    private SecretKey loadKey() throws IOException {

        try {
            if (Files.getFileStore(keyPath)
                    .supportsFileAttributeView("posix")) {

                Files.setPosixFilePermissions(
                        keyPath,
                        PosixFilePermissions.fromString("rw-------")
                );
            }

        } catch (UnsupportedOperationException ignored) {
            // System nie obsługuje POSIX.
        }

        byte[] encoded = Files.readAllBytes(keyPath);

        byte[] decoded;

        try {
            decoded = Base64.getDecoder().decode(
                    new String(encoded, StandardCharsets.US_ASCII).trim()
            );

        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "Encryption key file contains invalid Base64 data!",
                    exception
            );
        }

        if (decoded.length != KEY_SIZE / 8) {
            throw new IOException(
                    "Invalid AES-256 encryption key! Expected 32 bytes."
            );
        }

        return new SecretKeySpec(decoded, KEY_ALGORITHM);
    }

}