package pl.dawcou.astralogin.system;

public class LoginUtils {

    public static long parseTime(String input, long defaultValue) {
        if (input == null || input.isBlank()) return defaultValue;

        try {
            String[] parts = input.toLowerCase().trim().split("\\s+");
            long value = Long.parseLong(parts[0]);

            if (parts.length < 2) return value * 60000L; // Domyślnie minuty

            String unit = parts[1];

            // Obsługa różnych końcówek (np. "seconds" i "second")
            if (unit.startsWith("sec")) return value * 1000L;
            if (unit.startsWith("min")) return value * 60000L;
            if (unit.startsWith("hour")) return value * 3600000L;
            if (unit.startsWith("day")) return value * 86400000L;

            return value * 60000L; // Domyślny fallback
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "m " + seconds + "s";
    }
}