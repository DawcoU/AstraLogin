package pl.dawcou.astralogin;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatColor;

public class AstraLogin extends JavaPlugin implements Listener {

    public static final String PREFIX = ChatColor.of("#0088FF") + "[" + ChatColor.of("#00D5FF") + "AstraLogin" + ChatColor.of("#0088FF") + "]";

    public static final String PREFIX2 = ("§9[§bAstraLogin§9]");

    private InventoryStorage inventoryStorage;

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
        // 1. Tworzymy menedżery
        PasswordManager passwordManager = new PasswordManager(this);
        IPManager ipManager = new IPManager(this);

        // 1. Najpierw tworzysz bazę (InventoryStorage)
        this.inventoryStorage = new InventoryStorage(this);

        LoginSystem loginSystem = new LoginSystem(this, passwordManager, this.inventoryStorage);

        getServer().getPluginManager().registerEvents(new LoginBlocks(this, loginSystem, this.inventoryStorage), this);

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
        getLogger().info("§7------------ " + PREFIX2 + " §7------------");
        getLogger().info("§6   AstraLogin §ev" + getDescription().getVersion());
        getLogger().info("§6   Status: §aEnabled");
        getLogger().info("§6   Author: §eDawcoU");
        getLogger().info("§7----------------------------------------------");
        getLogger().info("");

        // --- 6. SPRAWDZANIE NOWEJ WERSJI:
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (getConfig().getBoolean("check-updates", true)) {
                new UpdateChecker(this).getVersion(version -> {
                    String currentVersion = this.getDescription().getVersion();

                    if (currentVersion.equals(version)) {
                        getLogger().info("");
                        getLogger().info("§aAstraLogin jest aktualny §f(§e" + version + "§f)");
                        getLogger().info("");
                    }
                    // Sprawdzamy, czy masz wersję wyższą niż na Modrinth (np. Twoje 2.2.0 vs 2.1.0 na stronie)
                    else if (currentVersion.compareTo(version) > 0) {
                        getLogger().info("");
                        getLogger().info("§7------------ " + PREFIX2 + " §7------------");
                        getLogger().info("§bUżywasz wersji testowej (Development): §f§nv" + currentVersion);
                        getLogger().info("§eNa Modrinth najnowsza stabilna to: §fv" + version);
                        getLogger().info("§bUważaj na błędy, kod jest w fazie rozwoju!");
                        getLogger().info("§7----------------------------------------------");
                        getLogger().info("");
                    }
                    // Standardowa informacja o aktualizacji (Twój plugin jest starszy)
                    else {
                        getLogger().info("");
                        getLogger().info("§7------------ " + PREFIX2 + " §7------------");
                        getLogger().info("§eDostępna jest nowa wersja AstraLogin: §fv" + version);
                        getLogger().info("§aPobierz: §f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
                        getLogger().info("§7----------------------------------------------");
                        getLogger().info("");
                    }
                });
            }
        });
    }

    @Override
    public void onDisable() {

        getLogger().info("");
        getLogger().info("§7------------ §4[§cAstraLogin§4] §7---------");
        getLogger().info("§6   Status: §cDisabled §7- §eSee you! :D");
        getLogger().info("§7----------------------------------------------");
        getLogger().info("");
    }
}