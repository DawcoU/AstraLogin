
package pl.dawcou.astralogin.auth.security.twofactor;

import java.util.Locale;

public enum TwoFactorMethod {

    MAIL("MAIL"),
    TOTP("TOTP");

    private final String configValue;

    TwoFactorMethod(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigValue() {
        return configValue;
    }

    public static TwoFactorMethod fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "MAIL", "EMAIL" -> MAIL;
            case "TOTP", "AUTH", "AUTHENTICATOR" -> TOTP;
            default -> null;
        };
    }

    @Override
    public String toString() {
        return configValue;
    }
}
