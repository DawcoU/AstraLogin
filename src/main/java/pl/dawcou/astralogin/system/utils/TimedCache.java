package pl.dawcou.astralogin.system.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Proste narzędzie do zarządzania mapami z czasem życia wpisów (TTL).
 * Działa na Map<K, Long> gdzie wartość = timestamp ostatniej aktualizacji.
 */
public final class TimedCache {

    private TimedCache() {}

    /**
     * Aktualizuje timestamp wpisu (lub dodaje nowy).
     */
    public static <K> void touch(Map<K, Long> map, K key) {
        if (map == null || key == null) return;
        map.put(key, System.currentTimeMillis());
    }

    /**
     * Sprawdza, czy wpis wygasł.
     * @return true jeśli nie istnieje lub jest starszy niż ttlMillis
     */
    public static <K> boolean isExpired(Map<K, Long> map, K key, long ttlMillis) {
        if (map == null || key == null) return true;
        Long time = map.get(key);
        if (time == null) return true;
        return System.currentTimeMillis() - time >= ttlMillis;
    }

    /**
     * Zwraca timestamp lub 0 jeśli brak.
     */
    public static <K> long getTimestamp(Map<K, Long> map, K key) {
        if (map == null || key == null) return 0L;
        return map.getOrDefault(key, 0L);
    }

    /**
     * Usuwa wszystkie wpisy starsze niż podany TTL.
     * @return ile wpisów usunięto
     */
    public static <K> int cleanExpired(Map<K, Long> map, long ttlMillis) {
        if (map == null || map.isEmpty()) return 0;

        long now = System.currentTimeMillis();
        int before = map.size();

        map.entrySet().removeIf(entry -> now - entry.getValue() >= ttlMillis);

        return before - map.size();
    }

    /**
     * Usuwa wpisy według własnego warunku (np. różne TTL dla różnych stanów).
     */
    public static <K> int cleanIf(Map<K, Long> map, BiPredicate<K, Long> shouldRemove) {
        if (map == null || map.isEmpty()) return 0;

        int before = map.size();
        map.entrySet().removeIf(entry -> shouldRemove.test(entry.getKey(), entry.getValue()));
        return before - map.size();
    }

    /**
     * Tworzy nową ConcurrentHashMap gotową do użycia z TimedCache.
     */
    public static <K> Map<K, Long> create() {
        return new ConcurrentHashMap<>();
    }
}