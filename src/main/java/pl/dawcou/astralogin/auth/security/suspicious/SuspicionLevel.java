package pl.dawcou.astralogin.auth.security.suspicious;

public enum SuspicionLevel {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int level;

    SuspicionLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static SuspicionLevel fromLevel(int level) {
        for (SuspicionLevel value : values()) {
            if (value.getLevel() == level) {
                return value;
            }
        }
        return NONE;
    }
}