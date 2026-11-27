package pl.dawcou.astralogin.auth.security.twofactor;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.twofactor.method.EmailTwoFactorManager;
import pl.dawcou.astralogin.auth.security.twofactor.method.TotpManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.UUID;

public final class TwoFactorManager {

    private final AstraLogin plugin;
    private final TotpManager totpManager;
    private final EmailTwoFactorManager emailManager;

    private volatile long setupTimeoutMillis;
    private volatile long codeLifetimeMillis;

    public TotpManager getTotpManager() {
        return totpManager;
    }

    public EmailTwoFactorManager getEmailManager() {
        return emailManager;
    }

    public TwoFactorManager(AstraLogin plugin) {
        this.plugin = plugin;
        this.setupTimeoutMillis = parseSetupTimeout();
        this.codeLifetimeMillis = parseEmailCodeLifetime();
        this.totpManager = new TotpManager(plugin, plugin.getPlayerDataManager(), setupTimeoutMillis);
        this.emailManager = new EmailTwoFactorManager(plugin, plugin.getEmailSender(), codeLifetimeMillis);

        applyEmailConfig();
    }

    // --------------------------------------------------
    // Configuration and lifecycle
    // --------------------------------------------------

    public void reload() {
        this.setupTimeoutMillis = parseSetupTimeout();
        this.codeLifetimeMillis = parseEmailCodeLifetime();
        totpManager.updateSetupTimeout(setupTimeoutMillis);
        emailManager.updateCodeLifetime(codeLifetimeMillis);
        applyEmailConfig();
    }

    public long getSetupTimeoutMillis() {
        return setupTimeoutMillis;
    }

    private void applyEmailConfig() {
        plugin.getEmailSender().updateConfig(
                plugin.getConfig().getString("security.2fa.mail.smtp.host", ""),
                plugin.getConfig().getInt("security.2fa.mail.smtp.port", 587),
                plugin.getConfig().getString("security.2fa.mail.smtp.username", ""),
                plugin.getConfig().getString("security.2fa.mail.smtp.password", ""),
                plugin.getConfig().getBoolean("security.2fa.mail.smtp.starttls", true),
                plugin.getConfig().getString("security.2fa.mail.smtp.from", ""),
                plugin.getConfig().getInt("security.2fa.mail.smtp.connection-timeout", 10000),
                plugin.getConfig().getInt("security.2fa.mail.smtp.timeout", 10000),
                plugin.getConfig().getInt("security.2fa.mail.smtp.write-timeout", 10000)
        );
    }

    private long parseSetupTimeout() {
        String setupTimeout = plugin.getConfig().getString("security.2fa.setup-timeout", "2 minutes");
        return Math.max(1L, TimeUtils.parseTime(setupTimeout, 120000L));
    }

    private long parseEmailCodeLifetime() {
        String rawLifetime = plugin.getConfig().getString("security.2fa.mail.code-lifetime", "2 minutes");
        return Math.max(1L, TimeUtils.parseTime(rawLifetime, setupTimeoutMillis));
    }

    // --------------------------------------------------
    // 2FA account state
    // --------------------------------------------------

    public boolean has2FA(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        if (!plugin.getPlayerDataManager().getBoolean(uuid, "security.two-factor.enabled", false)) {
            return false;
        }

        return getMethod(uuid) != null;
    }

    public TwoFactorMethod getMethod(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        String configuredMethod = plugin.getPlayerDataManager().getString(uuid, "security.two-factor.method");
        if (configuredMethod == null || configuredMethod.isBlank()) {
            // Backwards compatibility for older TOTP accounts without explicit method field.
            String savedSecret = plugin.getPlayerDataManager().getString(uuid, "security.two-factor.secret");
            if (savedSecret != null && !savedSecret.isBlank()) {
                return TwoFactorMethod.TOTP;
            }
            return null;
        }

        return TwoFactorMethod.fromConfig(configuredMethod);
    }

    public void delete2FA(UUID uuid) {
        if (uuid == null) {
            return;
        }

        invalidateSetup(uuid);
        plugin.getPlayerDataManager().remove(uuid, "security.two-factor");
    }

    // --------------------------------------------------
    // Setup state
    // --------------------------------------------------

    public boolean isSetupActive(UUID uuid) {
        return uuid != null && (totpManager.isSetupActive(uuid) || emailManager.hasPendingCode(uuid));
    }

    public void invalidateSetup(UUID uuid) {
        if (uuid == null) {
            return;
        }

        totpManager.invalidateSetup(uuid);
        emailManager.invalidateCode(uuid);
    }
}