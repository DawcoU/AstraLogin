package pl.dawcou.astralogin.auth.security.passwords;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PINManager implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<String, String> PINCache = new HashMap<>();

    public PINManager(AstraLogin plugin) {
        this.plugin = plugin;

        File dataDir = new File(plugin.getDataFolder(), "data/players");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        file = new File(dataDir, "passwords.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        reload();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("zresetujpin") || command.getName().equalsIgnoreCase("resetpin")) {
            if (!sender.hasPermission("astralogin.resetpin")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-pin.usage"));
                return true;
            }

            String targetName = args[0];
            UUID targetUUID = null;
            FileConfiguration accountsConfig = plugin.getAccountDataManager().getConfig();

            // 1. Szukamy UUID w historii kont, żeby nie lagować serwera przez getOfflinePlayer
            if (accountsConfig.getConfigurationSection("accounts") != null) {
                for (String uuidKey : accountsConfig.getConfigurationSection("accounts").getKeys(false)) {
                    String knownName = accountsConfig.getString("accounts." + uuidKey + ".last-known-name");
                    if (knownName != null && knownName.equalsIgnoreCase(targetName)) {
                        targetUUID = UUID.fromString(uuidKey);
                        targetName = knownName;
                        break;
                    }
                }
            }

            // 2. Jeśli nie grali u nas, to sprawdzamy tradycyjnie przez Bukkit na wszelki wypadek
            if (targetUUID == null) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                targetUUID = target.getUniqueId();
            }

            String uuidString = targetUUID.toString();

            // 3. Sprawdzamy PIN i usuwamy dane
            if (!hasPIN(uuidString)) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-pin.no-has-pin"));
                return true;
            }

            deletePIN(uuidString);

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-pin.admin-success")
                    .replace("%player%", targetName));

            String adminName = sender.getName();
            plugin.getLogManager().log("Admin " + adminName + " reset PIN for player " + targetName);

            // Wysyłanie graczowi info o zresetowaniu PIN'u
            Player targetPlayer = Bukkit.getPlayer(targetUUID);

            if (targetPlayer != null) {
                targetPlayer.sendMessage(
                        plugin.getLanguageManager().getWithPrefix("reset-pin.player-message")
                );
            }

            return true;
        }

        if (command.getName().equalsIgnoreCase("pin")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            if (args.length < 1) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.usage"));
                return true;
            }

            String op = args[0];

            if (op.equalsIgnoreCase("set")) {
                String method = plugin.getConfig().getString("features.pin.method", "RANDOM");

                if ("RANDOM".equalsIgnoreCase(method)) {
                    if (args.length != 1) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.usage"));
                        return true;
                    }

                    if (hasPIN(p.getUniqueId().toString())) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.already-set"));
                        return true;
                    }

                    int length = plugin.getConfig().getInt("features.pin.length", 6);
                    length = Math.max(4, length);
                    length = Math.min(9, length);

                    String generatedPIN = generatePIN(length);

                    plugin.getSchedulerManager().runAsync(() -> {
                        String hashedPIN = PasswordManager.hashPassword(plugin, generatedPIN);

                        savePIN(p.getUniqueId().toString(), hashedPIN);

                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.set-success")
                                    .replace("%pin%", generatedPIN));

                            plugin.getLogManager().log("Player " + p.getName() + " has registered his PIN code");
                        });
                    });
                } else if ("TYPING".equalsIgnoreCase(method)) {
                    if (args.length != 2) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.set-usage"));
                        return true;
                    }

                    String PIN = args[1];

                    if (hasPIN(p.getUniqueId().toString())) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.already-set"));
                        return true;
                    }

                    int length = plugin.getConfig().getInt("features.pin.length", 6);
                    length = Math.max(4, length);
                    length = Math.min(9, length);

                    if (!PIN.matches("\\d+")) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.contains-letters"));
                        return true;
                    }

                    if (PIN.length() != length) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.invalid-length")
                                .replace("%length%", String.valueOf(length)));
                        return true;
                    }

                    plugin.getSchedulerManager().runAsync(() -> {
                        String hashedPIN = PasswordManager.hashPassword(plugin, PIN);

                        savePIN(p.getUniqueId().toString(), hashedPIN);

                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.typed-success"));

                            plugin.getLogManager().log("Player " + p.getName() + " has registered his PIN code");
                        });
                    });
                }
            }
            return true;
        }
        return false;
    }

    public String generatePIN(int length) {
        // Obliczamy maksymalną wartość: np. dla length = 6 daje 10^6 = 1 000 000
        int bound = (int) Math.pow(10, length);

        // Losujemy liczbę z zakresu od 0 do bound - 1
        int pinNumber = ThreadLocalRandom.current().nextInt(bound);

        // Formatujemy liczbę z dynamiczną ilością wiodących zer (%04d, %06d itd.)
        return String.format("%0" + length + "d", pinNumber);
    }

    public void savePIN(String uuid, String PIN) {
        PINCache.put(uuid, PIN);
        config.set("passwords." + uuid + ".password", PIN);
        save();
    }

    public String getPIN(String uuid) {
        return PINCache.get(uuid);
    }

    public boolean hasPIN(String uuid) {
        return PINCache.containsKey(uuid);
    }

    public void deletePIN(String uuid) {
        PINCache.remove(uuid);
        config.set("passwords." + uuid + ".password", null);
        save();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
        PINCache.clear();

        if (config.getConfigurationSection("passwords") != null) {
            for (String key : config.getConfigurationSection("passwords").getKeys(false)) {
                String pin = config.getString("passwords." + key + ".pin");

                if (pin != null) {
                    PINCache.put(key, pin);
                }
            }
        }
    }

    private void save() {
        synchronized (config) {
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new java.util.ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("pin")) {
            if (args.length == 1) {
                hints.add("<set>");
            }

        } else if (cmd.equalsIgnoreCase("niepamietamhasla")) {
            if (args.length == 1) {
                hints.add("<PIN>");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(java.util.stream.Collectors.toList());
    }
}