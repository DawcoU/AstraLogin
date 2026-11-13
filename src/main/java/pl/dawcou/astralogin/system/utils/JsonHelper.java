package pl.dawcou.astralogin.system.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonHelper {
    //----------------------------------------------------------------
    // Helper methods for handling dotted paths
    //----------------------------------------------------------------

    private static final Pattern LINE_COL_PATTERN =
            Pattern.compile("line (\\d+) column (\\d+)");

    /*
     * Returns an object for the full path (e.g. "location" -> returns a location object).
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

    public static JsonElement getElementByPath(JsonObject root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return null;
        }

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

    public static String getString(JsonObject root, String path) {
        JsonElement element = getElementByPath(root, path);

        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
            return element.getAsString();
        }

        return null;
    }

    public static boolean getBoolean(JsonObject root, String path, boolean defaultValue) {
        JsonElement element = getElementByPath(root, path);

        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
            return element.getAsBoolean();
        }

        return defaultValue;
    }

    public static int getInt(JsonObject root, String path, int defaultValue) {
        JsonElement element = getElementByPath(root, path);

        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
            return element.getAsInt();
        }

        return defaultValue;
    }

    public static long getLong(JsonObject root, String path, long defaultValue) {
        JsonElement element = getElementByPath(root, path);

        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
            return element.getAsLong();
        }

        return defaultValue;
    }

    public static boolean has(JsonObject root, String path) {
        JsonElement element = getElementByPath(root, path);
        return element != null && !element.isJsonNull();
    }

    //----------------------------------------------------------------
    // Helper methods for handling paths without splitting
    //----------------------------------------------------------------
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
        } else {
            return false;
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

    /**
     * Logs a formatted JSON parsing error.
     *
     * @param plugin    Plugin instance used for logging.
     * @param fileName  Exact name of the JSON file.
     * @param exception JSON parsing exception.
     */
    public static void logFormattedJsonError(
            JavaPlugin plugin,
            String fileName,
            JsonParseException exception
    ) {
        String message = exception.getMessage();

        int line = -1;
        int column = -1;

        if (message != null) {
            Matcher matcher = LINE_COL_PATTERN.matcher(message);

            if (matcher.find()) {
                line = Integer.parseInt(matcher.group(1));
                column = Integer.parseInt(matcher.group(2));
            }
        }

        Throwable cause = exception.getCause();

        if ((line == -1 || column == -1)
                && cause instanceof MalformedJsonException) {

            String causeMessage = cause.getMessage();

            if (causeMessage != null) {
                Matcher matcher = LINE_COL_PATTERN.matcher(causeMessage);

                if (matcher.find()) {
                    line = Integer.parseInt(matcher.group(1));
                    column = Integer.parseInt(matcher.group(2));
                }
            }
        }

        String details = extractErrorDetails(
                cause != null ? cause.getMessage() : message
        );

        plugin.getLogger().severe("==================================================");
        plugin.getLogger().severe("Failed to parse '" + fileName + "'!");

        if (line != -1 && column != -1) {
            plugin.getLogger().severe(
                    "  -> Syntax error at Line: " + line
                            + ", Column: " + column
            );
        } else {
            plugin.getLogger().severe(
                    "  -> Syntax error location could not be calculated precisely."
            );
        }

        plugin.getLogger().severe("  -> Issue details: " + details);
        plugin.getLogger().severe(
                "  -> File saving SHOULD BE BLOCKED to prevent wiping data!"
        );
        plugin.getLogger().severe("==================================================");
    }

    /**
     * Attempts to identify the JSON syntax error.
     *
     *
     */
    private static String extractErrorDetails(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return "Malformed JSON syntax or invalid structure.";
        }

        if (rawMessage.contains("setStrictness")
                || rawMessage.contains("LENIENT")) {
            return "Invalid JSON header structure. Missing opening '{' or malformed root key.";
        } else if (rawMessage.contains("Expected name")) {
            return "Missing key or quote in JSON object structure.";
        } else if (rawMessage.contains("Unterminated object")) {
            return "Missing closing curly bracket '}' at end of object.";
        } else if (rawMessage.contains("Unterminated array")) {
            return "Missing closing square bracket ']' at end of array.";
        } else if (rawMessage.contains("Expected ':'")) {
            return "Missing colon ':' between key and value.";
        } else if (rawMessage.contains("Unterminated string")) {
            return "String quote '\"' was left unclosed.";
        } else if (rawMessage.contains("Expected value")) {
            return "Missing value or trailing comma ',' found.";
        }

        int atIndex = rawMessage.indexOf(" at line");

        if (atIndex != -1) {
            return rawMessage.substring(0, atIndex).trim();
        }

        return rawMessage;
    }

}