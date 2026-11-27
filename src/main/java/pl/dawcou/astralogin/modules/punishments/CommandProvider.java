package pl.dawcou.astralogin.modules.punishments;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.modules.punishments.commands.BanCommand;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class CommandProvider {

    private final AstraLogin plugin;
    private final boolean banPluginDetected;
    private final List<Command> customCommands = new ArrayList<>();

    public CommandProvider(AstraLogin plugin, boolean banPluginDetected) {
        this.plugin = plugin;
        this.banPluginDetected = banPluginDetected;
        registerDefaultCommands();
    }

    // --------------------------------------------------
    // Command List Setup
    // --------------------------------------------------

    private void registerDefaultCommands() {
        customCommands.add(new BanCommand(plugin));
    }

    public void addCommand(Command command) {
        this.customCommands.add(command);
    }

    // --------------------------------------------------
    // CommandMap Registration Handling
    // --------------------------------------------------

    public boolean registerCommands() {
        CommandMap commandMap = resolveCommandMap();
        if (commandMap == null) {
            plugin.getLogger().severe("Punishments module failed to load: Could not access server CommandMap!");
            return false;
        }

        for (Command command : customCommands) {
            boolean commandExists = commandMap.getCommand(command.getName()) != null;

            if (commandExists && banPluginDetected) {
                plugin.getLogger().warning("Command /" + command.getName() + " already exists and a ban plugin was detected. Skipping.");
                continue;
            }

            commandMap.register(plugin.getName().toLowerCase(), command);
        }

        return true;
    }

    private CommandMap resolveCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            return (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().severe("Failed to resolve CommandMap via reflection: " + e.getMessage());
            return null;
        }
    }
}