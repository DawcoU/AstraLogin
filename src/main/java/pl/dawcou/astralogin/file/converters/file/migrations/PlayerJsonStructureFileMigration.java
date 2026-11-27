package pl.dawcou.astralogin.file.converters.file.migrations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.file.FileMigration;
import pl.dawcou.astralogin.file.converters.file.MigrationUtils;

import java.io.File;
import java.util.Locale;

public class PlayerJsonStructureFileMigration implements FileMigration {

    private final AstraLogin plugin;
    private final File playersFolder;
    private final Gson gson;

    private static final String[][] PATHS = {
            {"captcha.timestamp", "security.captcha.last-completed-at"},

            {"account.register-date", "account.registered-at"},
            {"account.last-login-date", "account.last-login-at"},
            {"account.last-security-reminder-timestamp", "account.last-security-reminder"},

            {"2fa.enabled", "security.two-factor.enabled"},
            {"2fa.secret", "security.two-factor.secret"},
            {"2fa.backup-codes", "security.two-factor.backup-codes"},

            {"sessions.timestamp", "security.sessions.login.created-at"},
            {"sessions.ip", "security.sessions.login.ip"},
            {"sessions.2fa.timestamp", "security.sessions.two-factor.created-at"},
            {"sessions.2fa.ip", "security.sessions.two-factor.ip"},

            {"location.world", "player.location.world"},
            {"location.x", "player.location.x"},
            {"location.y", "player.location.y"},
            {"location.z", "player.location.z"},
            {"location.yaw", "player.location.yaw"},
            {"location.pitch", "player.location.pitch"},

            {"inventory.contents", "player.inventory.contents"},
            {"inventory.armor", "player.inventory.armor"},

            {"inventory.gamemode", "player.gamemode"}
    };

    public PlayerJsonStructureFileMigration(AstraLogin plugin) {
        this.plugin = plugin;
        this.playersFolder = new File(
                plugin.getDataFolder(),
                "data/players"
        );

        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    @Override
    public boolean needsMigration() {
        File[] files = getPlayerFiles();

        for (File file : files) {
            try {
                JsonObject root = MigrationUtils.loadJsonObject(file);

                for (String[] mapping : PATHS) {
                    if (MigrationUtils.hasJsonPath(root, mapping[0])) {
                        return true;
                    }
                }

            } catch (Exception e) {
                // Uszkodzone pliki nie są automatycznie nadpisywane.
                plugin.getLogger().warning(
                        "Cannot inspect player JSON during migration check: "
                                + file.getName()
                );
            }
        }

        return false;
    }

    @Override
    public void migrate() {
        File[] playerFiles = getPlayerFiles();

        if (playerFiles.length == 0) {
            return;
        }

        int migratedFiles = 0;
        int failedFiles = 0;
        int conflicts = 0;
        boolean migrationNoticeSent = false;

        for (File playerFile : playerFiles) {
            JsonObject root;

            try {
                root = MigrationUtils.loadJsonObject(playerFile);
            } catch (Exception e) {
                plugin.getNoticeManager().sendErrorMigrationNotice(
                        "read " + playerFile.getName()
                );
                plugin.getLogger().severe(
                        "Failed to read player JSON: " + playerFile.getName()
                );
                e.printStackTrace();
                failedFiles++;
                continue;
            }

            boolean changed = false;

            for (String[] mapping : PATHS) {
                MoveResult result = movePath(
                        root,
                        mapping[0],
                        mapping[1],
                        playerFile
                );

                if (result == MoveResult.MOVED) {
                    changed = true;
                } else if (result == MoveResult.CONFLICT) {
                    conflicts++;
                }
            }

            if (!changed) {
                continue;
            }

            if (!migrationNoticeSent) {
                plugin.getNoticeManager().sendMigrationNotice(
                        "Legacy player JSON structure",
                        "New player JSON structure"
                );
                migrationNoticeSent = true;
            }

            if (MigrationUtils.writeJson(
                    plugin,
                    gson,
                    root,
                    playerFile
            )) {
                migratedFiles++;
            } else {
                plugin.getNoticeManager().sendErrorMigrationNotice(
                        "save " + playerFile.getName()
                );
                failedFiles++;
            }
        }

        if (migratedFiles > 0) {
            plugin.getNoticeManager().sendSuccessMigrationNotice(
                    "Player JSON structure (" + migratedFiles + " files)"
            );
        }

        if (failedFiles > 0) {
            plugin.getLogger().warning(
                    "Player JSON migration finished with "
                            + failedFiles + " failed file(s)."
            );
        }

        if (conflicts > 0) {
            plugin.getLogger().warning(
                    "Player JSON migration encountered "
                            + conflicts + " destination conflict(s). "
                            + "Legacy values were preserved."
            );
        }
    }

    private MoveResult movePath(
            JsonObject root,
            String oldPath,
            String newPath,
            File playerFile
    ) {
        if (oldPath.equals(newPath)) {
            return MoveResult.SKIPPED;
        }

        String[] oldParts = oldPath.split("\\.");
        String[] newParts = newPath.split("\\.");

        JsonObject oldParent = root;
        JsonObject[] oldParentChain =
                new JsonObject[oldParts.length];

        oldParentChain[0] = root;

        for (int i = 0; i < oldParts.length - 1; i++) {
            JsonElement section = oldParent.get(oldParts[i]);

            if (section == null || !section.isJsonObject()) {
                return MoveResult.SKIPPED;
            }

            oldParent = section.getAsJsonObject();
            oldParentChain[i + 1] = oldParent;
        }

        String oldKey = oldParts[oldParts.length - 1];

        if (!oldParent.has(oldKey)) {
            return MoveResult.SKIPPED;
        }

        JsonElement value = oldParent.get(oldKey).deepCopy();

        if (oldPath.equals("2fa.backup-codes")
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()) {
            value = convertBackupCodes(value.getAsString());
        }

        JsonObject newParent = root;

        for (int i = 0; i < newParts.length - 1; i++) {
            String sectionName = newParts[i];
            JsonElement section = newParent.get(sectionName);

            if (section == null || section.isJsonNull()) {
                JsonObject created = new JsonObject();
                newParent.add(sectionName, created);
                newParent = created;

            } else if (section.isJsonObject()) {
                newParent = section.getAsJsonObject();

            } else {
                plugin.getLogger().warning(
                        "Cannot migrate " + playerFile.getName()
                                + ": destination section is not an object ("
                                + newPath + "). Legacy value preserved."
                );
                return MoveResult.CONFLICT;
            }
        }

        String newKey = newParts[newParts.length - 1];

        // Nie usuwamy starego pola, jeśli cel ma już inną wartość.
        if (newParent.has(newKey)
                && !newParent.get(newKey).isJsonNull()) {
            plugin.getLogger().warning(
                    "Cannot migrate " + playerFile.getName()
                            + ": destination already exists (" + newPath
                            + "). Legacy value preserved."
            );
            return MoveResult.CONFLICT;
        }

        newParent.add(newKey, value);
        oldParent.remove(oldKey);

        // Czyścimy puste sekcje starej ścieżki.
        for (int i = oldParts.length - 2; i >= 0; i--) {
            if (oldParentChain[i + 1].isEmpty()) {
                oldParentChain[i].remove(oldParts[i]);
            } else {
                break;
            }
        }

        return MoveResult.MOVED;
    }

    private JsonArray convertBackupCodes(String csv) {
        JsonArray backupCodes = new JsonArray();

        if (csv.isEmpty()) {
            return backupCodes;
        }

        for (String code : csv.split(",", -1)) {
            if (!code.isEmpty()) {
                backupCodes.add(code);
            }
        }

        return backupCodes;
    }

    private File[] getPlayerFiles() {
        if (!playersFolder.exists() || !playersFolder.isDirectory()) {
            return new File[0];
        }

        File[] files = playersFolder.listFiles(
                (directory, name) -> name.toLowerCase(Locale.ROOT)
                        .endsWith(".json")
        );

        return files != null ? files : new File[0];
    }

    private enum MoveResult {
        MOVED,
        SKIPPED,
        CONFLICT
    }
}