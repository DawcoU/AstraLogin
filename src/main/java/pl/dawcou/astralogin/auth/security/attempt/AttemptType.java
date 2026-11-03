package pl.dawcou.astralogin.auth.security.attempt;

public enum AttemptType {
    PASSWORD("PASSWORD"),
    PIN("PIN"),
    TWOFACTOR("2FA"),
    SPAM("SPAM"),
    CAPTCHA_GUI("CAPTCHA GUI"),
    CAPTCHA_MAP("CAPTCHA MAP"),
    CAPTCHA_CHAT("CAPTCHA CHAT"),
    UNKNOWN("UNKNOWN");

    private final String key;

    AttemptType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}