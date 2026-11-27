package pl.dawcou.astralogin.file.converters.file.migrations;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.file.FileMigration;
import pl.dawcou.astralogin.file.converters.file.MigrationUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class DataFoldersFileMigration implements FileMigration {

    private final AstraLogin plugin;
    private final File oldGlobalFolder;
    private final File oldPlayersFolder;
    private final File newGlobalFolder;
    private final File newPlayersFolder;
    private final File dataFolder;

    public DataFoldersFileMigration(AstraLogin plugin) {
        this.plugin = plugin;

        this.oldGlobalFolder = new File(plugin.getDataFolder(), "global_data");
        this.oldPlayersFolder = new File(plugin.getDataFolder(), "player_data");

        this.dataFolder = new File(plugin.getDataFolder(), "data");
        this.newGlobalFolder = new File(dataFolder, "global");
        this.newPlayersFolder = new File(dataFolder, "players");
    }

    @Override
    public boolean needsMigration() {
        return oldGlobalFolder.exists() || oldPlayersFolder.exists();
    }

    @Override
    public void migrate() {
        if (MigrationUtils.ensureDirectory(plugin, dataFolder)) {
            return;
        }

        if (oldGlobalFolder.exists()) {
            plugin.getNoticeManager().sendMigrationNotice(
                    "global_data",
                    "data/global"
            );

            if (migrateFolderContents(oldGlobalFolder, newGlobalFolder)
                    && deleteEmptyTree(oldGlobalFolder)) {
                plugin.getNoticeManager().sendSuccessMigrationNotice(
                        "global_data"
                );
            } else {
                plugin.getLogger().warning(
                        "Global data migration incomplete. Original files were preserved."
                );
            }
        }

        if (oldPlayersFolder.exists()) {
            plugin.getNoticeManager().sendMigrationNotice(
                    "player_data",
                    "data/players"
            );

            if (migrateFolderContents(oldPlayersFolder, newPlayersFolder)
                    && deleteEmptyTree(oldPlayersFolder)) {
                plugin.getNoticeManager().sendSuccessMigrationNotice(
                        "player_data"
                );
            } else {
                plugin.getLogger().warning(
                        "Player data migration incomplete. Original files were preserved."
                );
            }
        }
    }

    private boolean migrateFolderContents(File oldFolder, File newFolder) {
        if (!oldFolder.exists()) {
            return true;
        }

        if (!oldFolder.isDirectory()) {
            return false;
        }

        if (MigrationUtils.ensureDirectory(plugin, newFolder)) {
            return false;
        }

        File[] files = oldFolder.listFiles();

        if (files == null) {
            plugin.getLogger().severe(
                    "Cannot list directory: " + oldFolder.getAbsolutePath()
            );
            return false;
        }

        boolean success = true;

        for (File file : files) {
            File target = new File(newFolder, file.getName());

            if (file.isDirectory()) {
                if (!migrateFolderContents(file, target)) {
                    success = false;
                }
                continue;
            }

            if (target.exists()) {
                plugin.getLogger().warning(
                        "Folder migration conflict. Source preserved: "
                                + file.getAbsolutePath()
                );
                success = false;
                continue;
            }

            try {
                Files.move(file.toPath(), target.toPath());
            } catch (IOException e) {
                plugin.getNoticeManager().sendErrorMigrationNotice(
                        "move " + file.getName()
                );
                plugin.getLogger().severe(
                        "Failed to migrate file: " + file.getAbsolutePath()
                );
                e.printStackTrace();
                success = false;
            }
        }

        return success;
    }

    private boolean deleteEmptyTree(File folder) {
        if (!folder.exists()) {
            return true;
        }

        if (!folder.isDirectory()) {
            return false;
        }

        File[] files = folder.listFiles();

        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                if (!deleteEmptyTree(file)) {
                    return false;
                }
            } else {
                return false;
            }
        }

        try {
            Files.delete(folder.toPath());
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning(
                    "Failed to delete empty directory: " + folder.getAbsolutePath()
            );
            return false;
        }
    }
}