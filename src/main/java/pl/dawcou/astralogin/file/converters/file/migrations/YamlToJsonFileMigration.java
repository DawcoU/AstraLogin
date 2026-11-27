package pl.dawcou.astralogin.file.converters.file.migrations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.file.FileMigration;
import pl.dawcou.astralogin.file.converters.file.MigrationUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class YamlToJsonFileMigration implements FileMigration {

    private final AstraLogin plugin;
    private final Gson gson;

    private final File dataFolder;
    private final File playersFolder;
    private final File globalFolder;

    private final File passwordsYml;
    private final File ipsYml;
    private final File locationsYml;

    private final File accountsYml;
    private final File ipBansYml;
    private final File ipTrustYml;
    private final File spawnsYml;

    public YamlToJsonFileMigration(AstraLogin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();

        this.dataFolder = new File(plugin.getDataFolder(), "data");
        this.playersFolder = new File(dataFolder, "players");
        this.globalFolder = new File(dataFolder, "global");

        this.passwordsYml = new File(playersFolder, "passwords.yml");
        this.ipsYml = new File(playersFolder, "ips.yml");
        this.locationsYml = new File(playersFolder, "locations_data.yml");

        this.accountsYml = new File(globalFolder, "accounts.yml");
        this.ipBansYml = new File(globalFolder, "ip_bans.yml");

        // Obsługujemy obie nazwy spotykane w starszych wersjach.
        this.ipTrustYml = new File(globalFolder, "ip-trust.yml");
        this.spawnsYml = new File(globalFolder, "spawns.yml");
    }

    @Override
    public boolean needsMigration() {
        return passwordsYml.exists()
                || ipsYml.exists()
                || locationsYml.exists()
                || accountsYml.exists()
                || ipBansYml.exists()
                || ipTrustYml.exists()
                || new File(globalFolder, "ip_trust.yml").exists()
                || spawnsYml.exists();
    }

    @Override
    public void migrate() {
        if (!needsMigration()) {
            return;
        }

        plugin.getNoticeManager().sendMigrationNotice(
                "YAML files",
                "JSON format (data/players/<UUID>.json & data/global.json)"
        );

        if (MigrationUtils.ensureDirectory(plugin, playersFolder)
                || MigrationUtils.ensureDirectory(plugin, dataFolder)) {
            return;
        }

        Map<String, JsonObject> playerJsonMap = new ConcurrentHashMap<>();

        try {
            migratePasswords(playerJsonMap);
            migrateIps(playerJsonMap);
            migrateLocations(playerJsonMap);
            migrateAccounts(playerJsonMap);

        } catch (Exception e) {
            plugin.getLogger().severe(
                    "Failed to read YAML player data during migration."
            );
            e.printStackTrace();
            return;
        }

        // Najpierw zapisujemy wszystkie pliki graczy.
        for (Map.Entry<String, JsonObject> entry : playerJsonMap.entrySet()) {
            File target = new File(
                    playersFolder,
                    entry.getKey() + ".json"
            );

            if (!MigrationUtils.writeJson(
                    plugin,
                    gson,
                    entry.getValue(),
                    target
            )) {
                plugin.getLogger().severe(
                        "YAML -> JSON migration aborted because player data could not be saved."
                );
                return;
            }
        }

        // Następnie ładujemy global.json. Nie wolno nadpisać go pustym obiektem,
        // jeśli plik istnieje, ale jest uszkodzony.
        File globalJsonFile = new File(dataFolder, "global.json");
        JsonObject globalRoot;

        try {
            globalRoot = globalJsonFile.exists()
                    ? MigrationUtils.loadJsonObject(globalJsonFile)
                    : new JsonObject();

        } catch (IOException e) {
            plugin.getLogger().severe(
                    "Cannot load existing global.json. YAML sources were preserved."
            );
            e.printStackTrace();
            return;
        }

        try {
            migrateSpawns(globalRoot);
            migrateIpBans(globalRoot);
            migrateIpTrust(globalRoot);

        } catch (Exception e) {
            plugin.getLogger().severe(
                    "Failed to read YAML global data during migration."
            );
            e.printStackTrace();
            return;
        }

        if (!MigrationUtils.writeJson(
                plugin,
                gson,
                globalRoot,
                globalJsonFile
        )) {
            return;
        }

        // Dopiero po udanym zapisie graczy i global.json usuwamy źródła.
        File[] migratedFiles = {
                passwordsYml,
                ipsYml,
                locationsYml,
                accountsYml,
                ipBansYml,
                ipTrustYml,
                spawnsYml,
                new File(globalFolder, "ip_trust.yml")
        };

        boolean allDeleted = true;

        for (File file : migratedFiles) {
            if (file.exists()
                    && !MigrationUtils.deleteFile(plugin, file)) {
                allDeleted = false;
            }
        }

        MigrationUtils.deleteEmptyDirectory(globalFolder);
        MigrationUtils.deleteEmptyDirectory(playersFolder);

        if (allDeleted) {
            plugin.getNoticeManager().sendSuccessMigrationNotice(
                    "YAML -> JSON Conversion"
            );
        } else {
            plugin.getLogger().warning(
                    "YAML -> JSON conversion completed, but some old YAML files could not be deleted."
            );
        }
    }

    private void migratePasswords(
            Map<String, JsonObject> playerJsonMap
    ) {
        if (!passwordsYml.exists()) {
            return;
        }

        FileConfiguration config =
                YamlConfiguration.loadConfiguration(passwordsYml);

        ConfigurationSection passwords =
                config.getConfigurationSection("passwords");

        if (passwords == null) {
            return;
        }

        for (String uuid : passwords.getKeys(false)) {
            String pass = passwords.getString(uuid + ".password");

            if (pass == null && passwords.isString(uuid)) {
                pass = passwords.getString(uuid);
            }

            if (pass == null) {
                continue;
            }

            JsonObject playerJson = getOrCreatePlayerJson(
                    playerJsonMap,
                    uuid
            );

            JsonObject auth = MigrationUtils.getOrCreateSubObject(
                    playerJson,
                    "auth"
            );

            addPropertyIfAbsent(auth, "password", pass);
        }
    }

    private void migrateIps(
            Map<String, JsonObject> playerJsonMap
    ) {
        if (!ipsYml.exists()) {
            return;
        }

        FileConfiguration config =
                YamlConfiguration.loadConfiguration(ipsYml);

        ConfigurationSection ips = config.getConfigurationSection("ips");

        if (ips == null) {
            return;
        }

        for (String uuid : ips.getKeys(false)) {
            String ip = ips.getString(uuid);

            if (ip == null) {
                continue;
            }

            JsonObject playerJson = getOrCreatePlayerJson(
                    playerJsonMap,
                    uuid
            );

            JsonObject auth = MigrationUtils.getOrCreateSubObject(
                    playerJson,
                    "auth"
            );

            addPropertyIfAbsent(auth, "last-ip", ip);
        }
    }

    private void migrateLocations(
            Map<String, JsonObject> playerJsonMap
    ) {
        if (!locationsYml.exists()) {
            return;
        }

        FileConfiguration config =
                YamlConfiguration.loadConfiguration(locationsYml);

        ConfigurationSection locations =
                config.getConfigurationSection("last_locations");

        if (locations == null) {
            return;
        }

        for (String uuid : locations.getKeys(false)) {
            ConfigurationSection locSec =
                    locations.getConfigurationSection(uuid);

            if (locSec == null) {
                continue;
            }

            JsonObject playerJson = getOrCreatePlayerJson(
                    playerJsonMap,
                    uuid
            );

            if (playerJson.has("location")
                    && !playerJson.get("location").isJsonNull()) {
                continue;
            }

            JsonObject locObj = new JsonObject();
            locObj.addProperty("world", locSec.getString("world"));
            locObj.addProperty("x", locSec.getDouble("x"));
            locObj.addProperty("y", locSec.getDouble("y"));
            locObj.addProperty("z", locSec.getDouble("z"));
            locObj.addProperty("yaw", (float) locSec.getDouble("yaw"));
            locObj.addProperty("pitch", (float) locSec.getDouble("pitch"));

            playerJson.add("location", locObj);
        }
    }

    private void migrateAccounts(
            Map<String, JsonObject> playerJsonMap
    ) {
        if (!accountsYml.exists()) {
            return;
        }

        FileConfiguration config =
                YamlConfiguration.loadConfiguration(accountsYml);

        ConfigurationSection accounts =
                config.getConfigurationSection("accounts");

        if (accounts == null) {
            return;
        }

        for (String uuid : accounts.getKeys(false)) {
            ConfigurationSection accSec =
                    accounts.getConfigurationSection(uuid);

            if (accSec == null) {
                continue;
            }

            JsonObject playerJson = getOrCreatePlayerJson(
                    playerJsonMap,
                    uuid
            );

            JsonObject account = MigrationUtils.getOrCreateSubObject(
                    playerJson,
                    "account"
            );

            if (accSec.contains("last-known-name")) {
                addPropertyIfAbsent(
                        account,
                        "name",
                        accSec.getString("last-known-name")
                );
            }

            if (accSec.contains("register-date")) {
                addPropertyIfAbsent(
                        account,
                        "register-date",
                        accSec.getString("register-date")
                );
            }

            if (accSec.contains("last-login-date")) {
                addPropertyIfAbsent(
                        account,
                        "last-login-date",
                        accSec.getString("last-login-date")
                );
            }

            if (accSec.contains("last-security-reminder-timestamp")) {
                addPropertyIfAbsent(
                        account,
                        "last-security-reminder-timestamp",
                        accSec.getLong("last-security-reminder-timestamp")
                );
            }
        }
    }

    private void migrateSpawns(JsonObject globalRoot) {
        if (!spawnsYml.exists()) {
            return;
        }

        FileConfiguration config =
                YamlConfiguration.loadConfiguration(spawnsYml);

        ConfigurationSection spawnsSec =
                config.getConfigurationSection("spawns");

        if (spawnsSec == null || globalRoot.has("spawns")) {
            return;
        }

        JsonObject spawnsObj = new JsonObject();

        for (String key : spawnsSec.getKeys(false)) {
            ConfigurationSection section =
                    spawnsSec.getConfigurationSection(key);

            if (section == null) {
                continue;
            }

            JsonObject spawn = new JsonObject();
            spawn.addProperty("world", section.getString("world"));
            spawn.addProperty("x", section.getDouble("x"));
            spawn.addProperty("y", section.getDouble("y"));
            spawn.addProperty("z", section.getDouble("z"));
            spawn.addProperty("yaw", (float) section.getDouble("yaw"));
            spawn.addProperty("pitch", (float) section.getDouble("pitch"));

            spawnsObj.add(key, spawn);
        }

        globalRoot.add("spawns", spawnsObj);
    }

    private void migrateIpBans(JsonObject globalRoot) {
        if (!ipBansYml.exists() || globalRoot.has("ip_bans")) {
            return;
        }

        FileConfiguration config =
                YamlConfiguration.loadConfiguration(ipBansYml);

        JsonObject bansObj = new JsonObject();

        if (config.isList("bypasses")) {
            JsonArray bypassesArray = new JsonArray();

            for (String bypass : config.getStringList("bypasses")) {
                bypassesArray.add(bypass);
            }

            bansObj.add("bypasses", bypassesArray);
        }

        ConfigurationSection bansSec =
                config.getConfigurationSection("bans");

        if (bansSec != null) {
            JsonObject bansList = new JsonObject();

            for (String key : bansSec.getKeys(false)) {
                ConfigurationSection banItem =
                        bansSec.getConfigurationSection(key);

                if (banItem == null) {
                    continue;
                }

                String rawIp = banItem.getString("ip");
                String cleanIp = rawIp != null
                        ? rawIp
                        : key.replace("_", ".");

                long expire = banItem.getLong("expire");

                // Zachowujemy dotychczasową regułę pomijania wygasłych banów.
                if (expire <= System.currentTimeMillis()) {
                    continue;
                }

                JsonObject ban = new JsonObject();
                ban.addProperty("ip", cleanIp);
                ban.addProperty("expire", expire);
                ban.addProperty(
                        "reason",
                        banItem.getString("reason", "UNKNOWN")
                );
                ban.addProperty(
                        "uuid",
                        banItem.getString("uuid", "UNKNOWN")
                );

                bansList.add(cleanIp, ban);
            }

            bansObj.add("bans", bansList);
        }

        globalRoot.add("ip_bans", bansObj);
    }

    private void migrateIpTrust(JsonObject globalRoot) {
        File source = ipTrustYml.exists()
                ? ipTrustYml
                : new File(globalFolder, "ip_trust.yml");

        if (!source.exists() || globalRoot.has("ip_trust")) {
            return;
        }

        FileConfiguration config =
                YamlConfiguration.loadConfiguration(source);

        ConfigurationSection ipsSec =
                config.getConfigurationSection("ips");

        if (ipsSec == null) {
            return;
        }

        JsonObject trustObj = new JsonObject();

        for (String rawIpKey : ipsSec.getKeys(false)) {
            String cleanIpKey = rawIpKey.replace("_", ".");

            ConfigurationSection ipData =
                    ipsSec.getConfigurationSection(rawIpKey);

            if (ipData == null) {
                continue;
            }

            JsonObject scoreObj = new JsonObject();

            if (ipData.contains("score")) {
                scoreObj.addProperty("score", ipData.getInt("score"));
            }

            trustObj.add(cleanIpKey, scoreObj);
        }

        globalRoot.add("ip_trust", trustObj);
    }

    private JsonObject getOrCreatePlayerJson(
            Map<String, JsonObject> map,
            String uuid
    ) {
        JsonObject cached = map.get(uuid);

        if (cached != null) {
            return cached;
        }

        File existingFile = new File(
                playersFolder,
                uuid + ".json"
        );

        JsonObject result;

        if (existingFile.exists()) {
            try {
                result = MigrationUtils.loadJsonObject(existingFile);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Cannot load existing player JSON: "
                                + existingFile.getName(),
                        e
                );
            }
        } else {
            result = new JsonObject();
        }

        map.put(uuid, result);
        return result;
    }

    private void addPropertyIfAbsent(
            JsonObject object,
            String key,
            String value
    ) {
        if (value != null
                && (!object.has(key) || object.get(key).isJsonNull())) {
            object.addProperty(key, value);
        }
    }

    private void addPropertyIfAbsent(
            JsonObject object,
            String key,
            long value
    ) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            object.addProperty(key, value);
        }
    }
}