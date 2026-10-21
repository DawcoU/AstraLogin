package pl.dawcou.astralogin.api;

import pl.dawcou.astralogin.AstraLogin;
import java.util.UUID;

public class AstraLoginAPIImpl implements AstraLoginAPI {

    private final AstraLogin plugin;

    public AstraLoginAPIImpl(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isRegistered(UUID uuid) {
        return plugin.getPasswordManager() != null && plugin.getPasswordManager().isRegistered(uuid);
    }

    @Override
    public boolean isLogged(UUID uuid) {
        return plugin.getLoginSystem() != null && plugin.getLoginSystem().getLoggedIn().contains(uuid);
    }
}