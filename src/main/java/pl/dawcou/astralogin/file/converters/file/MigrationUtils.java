package pl.dawcou.astralogin.file.converters.file;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.FileConfiguration;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class MigrationUtils {

    private MigrationUtils() {
    }

    public static boolean ensureDirectory(AstraLogin plugin, File directory) {
        if (directory.exists()) {
            return !directory.isDirectory();
        }

        if (directory.mkdirs()) {
            return false;
        }

        plugin.getLogger().severe(
                "Failed to create directory: " + directory.getAbsolutePath()
        );
        return true;
    }

    public static boolean saveConfig(
            AstraLogin plugin,
            FileConfiguration config,
            File file
    ) {
        synchronized (config) {
            try {
                File parent = file.getParentFile();

                if (parent != null && ensureDirectory(plugin, parent)) {
                    return false;
                }

                config.save(file);
                return true;

            } catch (IOException e) {
                plugin.getNoticeManager().sendErrorMigrationNotice(
                        "save " + file.getName()
                );
                plugin.getLogger().severe(
                        "Failed to save file: " + file.getAbsolutePath()
                );
                e.printStackTrace();
                return false;
            }
        }
    }

    public static boolean writeJson(
            AstraLogin plugin,
            Gson gson,
            JsonObject json,
            File target
    ) {
        File parent = target.getParentFile();

        if (parent != null && ensureDirectory(plugin, parent)) {
            return false;
        }

        File temp = new File(target.getParentFile(), target.getName() + ".tmp");

        try {
            try (Writer writer = Files.newBufferedWriter(
                    temp.toPath(),
                    StandardCharsets.UTF_8
            )) {
                gson.toJson(json, writer);
            }

            try {
                Files.move(
                        temp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        temp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return true;

        } catch (IOException e) {
            plugin.getLogger().severe(
                    "Failed to write JSON file: " + target.getAbsolutePath()
            );
            e.printStackTrace();
            return false;

        } finally {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException ignored) {
            }
        }
    }

    public static JsonObject loadJsonObject(File file) throws IOException {
        try (Reader reader = Files.newBufferedReader(
                file.toPath(),
                StandardCharsets.UTF_8
        )) {
            JsonElement parsed = JsonParser.parseReader(reader);

            if (parsed == null || !parsed.isJsonObject()) {
                throw new IOException("JSON root is not an object: " + file.getName());
            }

            return parsed.getAsJsonObject();
        }
    }

    public static JsonObject getOrCreateSubObject(
            JsonObject parent,
            String key
    ) {
        JsonElement element = parent.get(key);

        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }

        JsonObject child = new JsonObject();
        parent.add(key, child);
        return child;
    }

    public static void deleteEmptyDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();

        if (files != null && files.length == 0) {
            directory.delete();
        }
    }

    public static boolean deleteFile(AstraLogin plugin, File file) {
        if (!file.exists()) {
            return true;
        }

        try {
            Files.delete(file.toPath());
            return true;
        } catch (IOException e) {
            plugin.getNoticeManager().sendErrorMigrationNotice(
                    "delete " + file.getName()
            );
            plugin.getLogger().severe(
                    "Failed to delete migrated file: " + file.getAbsolutePath()
            );
            e.printStackTrace();
            return false;
        }
    }

    public static boolean hasJsonPath(JsonObject root, String path) {
        JsonElement current = root;

        for (String part : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) {
                return false;
            }

            current = current.getAsJsonObject().get(part);
        }

        return current != null && !current.isJsonNull();
    }

    public static JsonElement getJsonPath(JsonObject root, String path) {
        JsonElement current = root;

        for (String part : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }

            current = current.getAsJsonObject().get(part);
        }

        return current;
    }

    public static boolean setJsonPathIfAbsent(
            JsonObject root,
            String path,
            JsonElement value
    ) {
        String[] parts = path.split("\\.");
        JsonObject parent = root;

        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement section = parent.get(parts[i]);

            if (section == null || section.isJsonNull()) {
                JsonObject created = new JsonObject();
                parent.add(parts[i], created);
                parent = created;
            } else if (section.isJsonObject()) {
                parent = section.getAsJsonObject();
            } else {
                return false;
            }
        }

        String key = parts[parts.length - 1];

        if (parent.has(key) && !parent.get(key).isJsonNull()) {
            return false;
        }

        parent.add(key, value.deepCopy());
        return true;
    }
}