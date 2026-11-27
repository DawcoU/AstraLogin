package pl.dawcou.astralogin.file.converters.config.migrations;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.config.ConfigMigrationStep;

public class IpSecurityMigration implements ConfigMigrationStep {

    private final AstraLogin plugin;

    public IpSecurityMigration(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean needsMigration(FileConfiguration config) {
        ConfigurationSection ipSecurity =
                config.getConfigurationSection("ip-security");

        return ipSecurity != null
                && ipSecurity.contains("ip-check-octets");
    }

    @Override
    public boolean migrate(FileConfiguration config) {
        ConfigurationSection ipSecurity =
                config.getConfigurationSection("ip-security");

        if (ipSecurity == null
                || !ipSecurity.contains("ip-check-octets")) {
            return false;
        }

        ConfigurationSection verification =
                ipSecurity.getConfigurationSection("verification");

        if (verification == null) {
            if (ipSecurity.contains("verification")) {
                plugin.getLogger().warning(
                        "Config migration conflict: ip-security.verification "
                                + "is not a section. Legacy setting was preserved."
                );
                return false;
            }

            verification = ipSecurity.createSection("verification");
        }

        if (verification.contains("ipv4-check-octets")) {
            plugin.getLogger().warning(
                    "Config migration conflict: "
                            + "ip-security.verification.ipv4-check-octets "
                            + "already exists. Legacy setting was preserved."
            );
            return false;
        }

        plugin.getNoticeManager().sendMigrationNotice(
                "ip-security.ip-check-octets",
                "ip-security.verification.ipv4-check-octets"
        );

        verification.set(
                "ipv4-check-octets",
                ipSecurity.getInt("ip-check-octets", 2)
        );

        if (!verification.contains("ipv6-check-blocks")) {
            verification.set("ipv6-check-blocks", 4);
        }

        ipSecurity.set("ip-check-octets", null);

        plugin.getNoticeManager().sendSuccessMigrationNotice(
                "config.yml (ip-security section)"
        );

        return true;
    }
}