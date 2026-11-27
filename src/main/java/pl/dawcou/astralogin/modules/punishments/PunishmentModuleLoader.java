package pl.dawcou.astralogin.modules.punishments;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import pl.dawcou.astralogin.AstraLogin;

import java.util.Set;

public class PunishmentModuleLoader {

    private final AstraLogin plugin;
    private boolean initialized = false;

    private static final Set<String> CONFLICT_KEYWORDS = Set.of(
            "ban",
            "punish",
            "mute",
            "kick",
            "warn",
            "jail",
            "mod"
    );

    public PunishmentModuleLoader(AstraLogin plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------
    // Module Initialization & Logic
    // --------------------------------------------------

    public void initialize() {
        if (initialized) {
            return;
        }

        if (!plugin.getConfig().getBoolean("modules.punishments.enabled", false)) {
            return;
        }

        boolean conflictCheckEnabled = plugin.getConfig().getBoolean("modules.punishments.check-conflicts", true);
        boolean banPluginDetected = conflictCheckEnabled && hasConflictingPlugin();

        CommandProvider provider = new CommandProvider(plugin, banPluginDetected);
        boolean registered = provider.registerCommands();

        if (!registered) {
            return;
        }

        this.initialized = true;
        plugin.getLogger().info("Punishments module enabled successfully.");
    }

    // --------------------------------------------------
    // Reload Logic
    // --------------------------------------------------

    public void reload() {
        if (!initialized) {
            initialize();
        }
    }

    // --------------------------------------------------
    // Conflict Helper
    // --------------------------------------------------

    private boolean hasConflictingPlugin() {
        for (Plugin installedPlugin : Bukkit.getPluginManager().getPlugins()) {
            if (installedPlugin.equals(plugin)) {
                continue;
            }
            String pluginName = installedPlugin.getName().toLowerCase();
            for (String keyword : CONFLICT_KEYWORDS) {
                if (pluginName.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isInitialized() {
        return initialized;
    }
}