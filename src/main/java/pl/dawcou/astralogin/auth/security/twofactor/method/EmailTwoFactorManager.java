package pl.dawcou.astralogin.auth.security.twofactor.method;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.EmailSender;
import pl.dawcou.astralogin.auth.security.encryption.EncryptedDataType;
import pl.dawcou.astralogin.auth.security.encryption.EncryptionException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public final class EmailTwoFactorManager {

    private final AstraLogin plugin;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<UUID, PendingCode> pendingCodes = new ConcurrentHashMap<>();

    private volatile long codeLifetimeMillis;

    public EmailTwoFactorManager(AstraLogin plugin, EmailSender emailSender, long codeLifetimeMillis) {
        this.plugin = plugin;
        this.emailSender = emailSender;
        this.codeLifetimeMillis = Math.max(1L, codeLifetimeMillis);
    }

    public void updateCodeLifetime(long codeLifetimeMillis) {
        this.codeLifetimeMillis = Math.max(1L, codeLifetimeMillis);
    }

    public boolean isConfigured() {
        return emailSender.isConfigured();
    }

    public EmailSender getEmailSender() {
        return emailSender;
    }

    // --------------------------------------------------
    // Email address management & encryption
    // --------------------------------------------------

    public String getEmailAddress(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        String encryptedEmail = plugin.getPlayerDataManager().getString(uuid, "account.email");
        if (encryptedEmail == null || encryptedEmail.isBlank()) {
            return null;
        }

        try {
            return plugin.getEncryptionManager().decrypt(
                    encryptedEmail,
                    uuid,
                    EncryptedDataType.EMAIL_ADDRESS
            );
        } catch (EncryptionException exception) {
            plugin.getLogger().severe("Could not decrypt email address for player " + uuid + ": " + exception.getMessage());
            return null;
        }
    }

    public boolean hasEmailAddress(UUID uuid) {
        String email = getEmailAddress(uuid);
        return email != null && !email.isBlank();
    }

    public boolean setEmailAddress(UUID uuid, String email) {
        if (uuid == null || !EmailSender.isValidEmail(email)) {
            return false;
        }

        try {
            String encryptedEmail = plugin.getEncryptionManager().encrypt(
                    email.trim(),
                    uuid,
                    EncryptedDataType.EMAIL_ADDRESS
            );

            plugin.getPlayerDataManager().set(uuid, "account.email", encryptedEmail);
            return true;

        } catch (EncryptionException exception) {
            plugin.getLogger().severe("Could not encrypt email address for player " + uuid + ": " + exception.getMessage());
            return false;
        }
    }

    public void deleteEmailAddress(UUID uuid) {
        if (uuid == null) {
            return;
        }

        invalidateCode(uuid);
        plugin.getPlayerDataManager().remove(uuid, "account.email");
    }

    // --------------------------------------------------
    // Code dispatch and verification
    // --------------------------------------------------

    public void sendVerificationCode(UUID uuid, String recipient, Consumer<Boolean> callback) {
        if (uuid == null || !emailSender.isConfigured() || !EmailSender.isValidEmail(recipient)) {
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }

        long lifetime = codeLifetimeMillis;
        String code = generateCode();
        PendingCode pending = new PendingCode(code);

        // Newer request replaces any previous pending code for this UUID.
        pendingCodes.put(uuid, pending);

        String content = plugin.getNoticeManager().getVerificationEmailContent(code, lifetime);
        emailSender.sendEmail(recipient, "AstraLogin - Verification code", content, sent -> {
            if (!sent) {
                pendingCodes.remove(uuid, pending);

                if (callback != null) {
                    callback.accept(false);
                }

                return;
            }

            boolean accepted;

            synchronized (pending) {
                accepted = pendingCodes.get(uuid) == pending;

                if (accepted) {
                    pending.expiresAt = System.currentTimeMillis() + lifetime;
                    pending.sent = true;
                }
            }

            if (callback != null) {
                callback.accept(accepted);
            }
        });
    }

    public boolean verifyCode(UUID uuid, String inputCode) {
        if (uuid == null || inputCode == null || !inputCode.matches("\\d{6}")) {
            return false;
        }

        PendingCode pending = pendingCodes.get(uuid);
        if (pending == null) {
            return false;
        }

        synchronized (pending) {
            if (pendingCodes.get(uuid) != pending || !pending.sent) {
                return false;
            }

            if (System.currentTimeMillis() >= pending.expiresAt) {
                pendingCodes.remove(uuid, pending);
                return false;
            }

            if (!constantTimeEquals(pending.code, inputCode)) {
                return false;
            }

            pendingCodes.remove(uuid, pending);
            return true;
        }
    }

    public void invalidateCode(UUID uuid) {
        if (uuid != null) {
            pendingCodes.remove(uuid);
        }
    }

    public boolean hasPendingCode(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        PendingCode pending = pendingCodes.get(uuid);
        if (pending == null) {
            return false;
        }

        synchronized (pending) {
            if (pendingCodes.get(uuid) != pending) {
                return false;
            }

            // Code is reserved while SMTP delivery is still in progress.
            if (!pending.sent) {
                return true;
            }

            if (System.currentTimeMillis() >= pending.expiresAt) {
                pendingCodes.remove(uuid, pending);
                return false;
            }

            return true;
        }
    }

    public long getRemainingTime(UUID uuid) {
        if (uuid == null) {
            return 0;
        }

        PendingCode pending = pendingCodes.get(uuid);
        if (pending == null) {
            return 0;
        }

        synchronized (pending) {
            if (pendingCodes.get(uuid) != pending || !pending.sent) {
                return 0;
            }

            long remaining = pending.expiresAt - System.currentTimeMillis();
            if (remaining <= 0) {
                pendingCodes.remove(uuid, pending);
                return 0;
            }

            return remaining / 1000;
        }
    }

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static final class PendingCode {
        private final String code;

        private volatile long expiresAt;
        private volatile boolean sent;

        private PendingCode(String code) {
            this.code = code;
            this.expiresAt = 0;
            this.sent = false;
        }
    }
}