
package pl.dawcou.astralogin.auth;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import pl.dawcou.astralogin.AstraLogin;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

public final class EmailSender {

    private static final String LOG_PREFIX = "[EmailSender] ";

    private final AstraLogin plugin;

    private volatile SmtpConfig smtpConfig;

    public EmailSender(AstraLogin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    // --------------------------------------------------
    // Configuration
    // --------------------------------------------------

    public void updateConfig(
            String host,
            int port,
            String username,
            String password,
            boolean startTls,
            String from,
            int connectionTimeoutMillis,
            int readTimeoutMillis,
            int writeTimeoutMillis
    ) {
        this.smtpConfig = new SmtpConfig(
                normalize(host),
                port,
                normalize(username),
                password,
                startTls,
                normalize(from),
                Math.max(1, connectionTimeoutMillis),
                Math.max(1, readTimeoutMillis),
                Math.max(1, writeTimeoutMillis)
        );
    }

    public boolean isConfigured() {
        return isConfigured(smtpConfig);
    }

    private boolean isConfigured(SmtpConfig config) {
        return config != null
                && config.host() != null
                && !config.host().isBlank()
                && config.username() != null
                && !config.username().isBlank()
                && config.password() != null
                && !config.password().isBlank()
                && config.port() > 0
                && config.port() <= 65535
                && resolveSenderAddress(config) != null;
    }

    // --------------------------------------------------
    // Email validation
    // --------------------------------------------------

    public static boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }

        String normalized = email.trim();

        if (normalized.isEmpty() || normalized.length() > 254) {
            return false;
        }

        try {
            InternetAddress address = new InternetAddress(normalized, true);
            address.validate();

            String parsed = address.getAddress();

            return parsed != null
                    && parsed.indexOf('@') > 0
                    && parsed.indexOf('@') < parsed.length() - 1;

        } catch (AddressException exception) {
            return false;
        }
    }

    // --------------------------------------------------
    // Email sending
    // --------------------------------------------------

    public void sendEmail(String recipient, String subject, String content, Consumer<Boolean> callback) {
        SmtpConfig config = smtpConfig;

        if (!isConfigured(config)
                || !isValidEmail(recipient)
                || subject == null
                || content == null) {

            complete(callback, false);
            return;
        }

        String cleanRecipient = recipient.trim();

        try {
            plugin.getSchedulerManager().runAsync(() -> {
                boolean success = sendInternal(
                        config,
                        cleanRecipient,
                        subject,
                        content
                );

                complete(callback, success);
            });

        } catch (RuntimeException exception) {
            plugin.getLogger().warning(
                    LOG_PREFIX + "Could not schedule email delivery: "
                            + exception.getClass().getSimpleName()
            );

            complete(callback, false);
        }
    }

    private boolean sendInternal(SmtpConfig config, String recipient, String subject, String content) {
        try {
            Properties properties = createProperties(config);

            Session session = Session.getInstance(
                    properties,
                    new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(
                                    config.username(),
                                    config.password()
                            );
                        }
                    }
            );

            MimeMessage message = new MimeMessage(session);

            InternetAddress senderAddress = new InternetAddress(
                    resolveSenderAddress(config),
                    true
            );

            InternetAddress recipientAddress = new InternetAddress(
                    recipient,
                    true
            );

            message.setFrom(senderAddress);

            message.setRecipient(
                    Message.RecipientType.TO,
                    recipientAddress
            );

            message.setSubject(
                    subject,
                    StandardCharsets.UTF_8.name()
            );

            message.setText(
                    content,
                    StandardCharsets.UTF_8.name()
            );

            Transport.send(message);

            return true;

        } catch (MessagingException | RuntimeException exception) {
            plugin.getLogger().warning(
                    LOG_PREFIX + "Email delivery failed ("
                            + exception.getClass().getSimpleName()
                            + "): " + exception.getMessage()
            );

            return false;
        }
    }

    // --------------------------------------------------
    // SMTP properties
    // --------------------------------------------------

    private Properties createProperties(SmtpConfig config) {
        Properties properties = new Properties();

        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.host", config.host());
        properties.put("mail.smtp.port", String.valueOf(config.port()));

        properties.put(
                "mail.smtp.starttls.enable",
                String.valueOf(config.startTls())
        );

        properties.put(
                "mail.smtp.starttls.required",
                String.valueOf(config.startTls())
        );

        properties.put(
                "mail.smtp.ssl.checkserveridentity",
                "true"
        );

        properties.put(
                "mail.smtp.connectiontimeout",
                String.valueOf(config.connectionTimeoutMillis())
        );

        properties.put(
                "mail.smtp.timeout",
                String.valueOf(config.readTimeoutMillis())
        );

        properties.put(
                "mail.smtp.writetimeout",
                String.valueOf(config.writeTimeoutMillis())
        );

        return properties;
    }

    // --------------------------------------------------
    // Sender resolution
    // --------------------------------------------------

    private String resolveSenderAddress(SmtpConfig config) {
        if (config == null) {
            return null;
        }

        if (config.from() != null && !config.from().isBlank()) {
            return isValidEmail(config.from())
                    ? config.from()
                    : null;
        }

        if (isValidEmail(config.username())) {
            return config.username();
        }

        return null;
    }

    // --------------------------------------------------
    // Callback handling
    // --------------------------------------------------

    /**
     * Email callbacks are always dispatched through the main server thread.
     * This allows callers to safely perform Bukkit player operations.
     */
    private void complete(Consumer<Boolean> callback, boolean success) {
        if (callback == null) {
            return;
        }

        try {
            plugin.getSchedulerManager().runSync(() -> {
                try {
                    callback.accept(success);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning(
                            LOG_PREFIX + "Email callback failed: "
                                    + exception.getClass().getSimpleName()
                    );
                }
            });

        } catch (RuntimeException exception) {
            plugin.getLogger().warning(
                    LOG_PREFIX + "Could not schedule email callback: "
                            + exception.getClass().getSimpleName()
            );
        }
    }

    // --------------------------------------------------
    // Utilities
    // --------------------------------------------------

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private record SmtpConfig(
            String host,
            int port,
            String username,
            String password,
            boolean startTls,
            String from,
            int connectionTimeoutMillis,
            int readTimeoutMillis,
            int writeTimeoutMillis
    ) {
    }
}
