package pl.dawcou.astralogin;

import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatColor;

public class AstraLogin extends JavaPlugin {

    public static final String PREFIX = ChatColor.of("#0088FF") + "[" + ChatColor.of("#00D5FF") + "AstraLogin" + ChatColor.of("#0088FF") + "]";

    public static final String PREFIX2 = ("§9[§bAstraLogin§9]");

    @Override
    public void onEnable() {
        try {
            // Pobieramy RootLogger z Log4j2
            org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();

            // Rejestrujemy Twój filtr, przekazując instancję pluginu (this)
            rootLogger.addFilter(new LogFilter(this));

            getLogger().info("§aLogger został pomyślnie zarejestrowany w AstraLogin!");
        } catch (Exception e) {
            getLogger().severe("§cNie udało się zarejestrować filtra: " + e.getMessage());
            e.printStackTrace(); // Warto mieć pełny ślad, jakby Log4j2 świrował
        }

        // --- 2. CONFIG ---
        saveDefaultConfig();
        new ConfigUpdater(this).check();

        // --- 3. LOGIKA ---
        // Najpierw tworzymy menedżerów (tylko raz!)
        PasswordManager passwordManager = new PasswordManager(this);
        IPManager ipManager = new IPManager(this);

        // Teraz podajemy gotowy passwordManager do LoginSystem
        LoginSystem loginSystem = new LoginSystem(passwordManager, this);

        // --- 4. REJESTRACJA KOMEND I EVENTÓW ---
        getCommand("zarejestruj").setExecutor(loginSystem);
        getCommand("zaloguj").setExecutor(loginSystem);
        getCommand("astralogin").setExecutor(loginSystem);
        getCommand("astralogin").setTabCompleter(loginSystem);
        getCommand("zresetujhaslo").setExecutor(loginSystem);
        getCommand("zmienhaslo").setExecutor(loginSystem);
        getCommand("zresetujip").setExecutor(new ResetIPCommand(this, ipManager));
        getServer().getPluginManager().registerEvents(loginSystem, this);

        // --- 5. LOGI STARTOWE ---
        getLogger().info("");
        getLogger().info("§8------------ " + PREFIX2 + " §8------------");
        getLogger().info("§6   AstraLogin §ev" + getDescription().getVersion());
        getLogger().info("§6   Status: §aEnabled");
        getLogger().info("§6   Author: §eDawcoU");
        getLogger().info("§8----------------------------------------------");
        getLogger().info("");

        // --- 6. SPRAWDZANIE NOWEJ WERSJI:
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            // Sprawdzamy czy opcja jest włączona w configu
            if (getConfig().getBoolean("check-updates", true)) {
                new UpdateChecker(this).getVersion(version -> {
                    if (this.getDescription().getVersion().equals(version)) {
                        getLogger().info("§aAstraLogin jest aktualny! (§f" + version + "§a)");
                    } else {
                        getLogger().info("");
                        getLogger().info("§8------------ " + PREFIX2 + " §8------------");
                        getLogger().info("§eDostępna jest nowa wersja AstraLogin: §fv" + version);
                        getLogger().info("§aPobierz: §f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
                        getLogger().info("§8----------------------------------------------");
                        getLogger().info("");
                    }
                });
            }
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("");
        getLogger().info("§8------------ §4[§cAstraLogin§4] §8---------");
        getLogger().info("§6   Status: §cDisabled §7- §eSee you! :D");
        getLogger().info("§8----------------------------------------------");
        getLogger().info("");
    }
}