package pl.dawcou.astralogin.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonHelper {
    //--------------------------------------------------
    // Pomocnicze metody do obsługi ścieżek z kropkami
    //--------------------------------------------------

    /*
     * Zwraca obiekt dla pełnej ścieżki (np. "location" -> zwraca obiekt location).
     */
    public static JsonObject getTargetObject(JsonObject root, String path, boolean createIfMissing) {
        if (root == null || path == null || path.isEmpty()) return root;

        String[] keys = path.split("\\.");
        JsonObject current = root;

        for (String key : keys) {
            if (!current.has(key) || !current.get(key).isJsonObject()) {
                if (!createIfMissing) {
                    return null;
                }
                JsonObject child = new JsonObject();
                current.add(key, child);
                current = child;
            } else {
                current = current.getAsJsonObject(key);
            }
        }
        return current;
    }

    public static String getFinalKey(String path) {
        if (!path.contains(".")) return path;
        String[] keys = path.split("\\.");
        return keys[keys.length - 1];
    }

    public static JsonElement getElementByPath(JsonObject root, String path) {
        String[] keys = path.split("\\.");
        JsonObject current = root;

        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            if (!current.has(key) || !current.get(key).isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject(key);
        }

        String finalKey = keys[keys.length - 1];
        return current.get(finalKey);
    }

    //--------------------------------------------------
    // Pomocnicze metody do obsługi ścieżek bez splitowania
    //--------------------------------------------------
    public static JsonElement getElementExplicit(JsonObject root, String... keys) {
        if (keys == null || keys.length == 0 || root == null) return null;
        JsonObject current = root;

        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            if (!current.has(key) || !current.get(key).isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject(key);
        }

        return current.get(keys[keys.length - 1]);
    }

    public static boolean setExplicit(JsonObject root, Object value, String... keys) {
        if (keys == null || keys.length == 0 || root == null) return false;

        if (value == null) {
            return removeExplicit(root, keys);
        }

        JsonObject current = root;
        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            if (!current.has(key) || !current.get(key).isJsonObject()) {
                JsonObject child = new JsonObject();
                current.add(key, child);
                current = child;
            } else {
                current = current.getAsJsonObject(key);
            }
        }

        String finalKey = keys[keys.length - 1];

        if (value instanceof Number) {
            current.addProperty(finalKey, (Number) value);
        } else if (value instanceof Boolean) {
            current.addProperty(finalKey, (Boolean) value);
        } else if (value instanceof String) {
            current.addProperty(finalKey, (String) value);
        } else if (value instanceof JsonElement) {
            current.add(finalKey, (JsonElement) value);
        }

        return true;
    }

    public static boolean removeExplicit(JsonObject root, String... keys) {
        if (keys == null || keys.length == 0 || root == null) return false;
        JsonObject current = root;

        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            if (!current.has(key) || !current.get(key).isJsonObject()) {
                return false;
            }
            current = current.getAsJsonObject(key);
        }

        current.remove(keys[keys.length - 1]);
        return true;
    }
}