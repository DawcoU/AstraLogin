package pl.dawcou.astralogin.api;

import java.util.UUID;

public interface AstraLoginAPI {
    boolean isRegistered(UUID uuid);
    boolean isLogged(UUID uuid);
}