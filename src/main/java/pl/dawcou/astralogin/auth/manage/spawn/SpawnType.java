package pl.dawcou.astralogin.auth.manage.spawn;

public enum SpawnType {
    BEFORE_LOGIN("before_login"),
    AFTER_LOGIN("after_login");

    private final String key;

    SpawnType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static SpawnType parse(String input) {
        if (input == null) return null;
        for (SpawnType type : values()) {
            if (type.key.equalsIgnoreCase(input) || type.name().equalsIgnoreCase(input)) {
                return type;
            }
        }
        return null;
    }
}