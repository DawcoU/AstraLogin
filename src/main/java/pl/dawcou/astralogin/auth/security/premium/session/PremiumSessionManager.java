package pl.dawcou.astralogin.auth.security.premium.session;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PremiumSessionManager {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, byte[]> verifyTokens = new ConcurrentHashMap<>();
    private final Map<String, String> pendingSessions = new ConcurrentHashMap<>();

    public void createSession(String address, String username) {
        pendingSessions.put(address, username);
        byte[] token = new byte[4];
        random.nextBytes(token);
        verifyTokens.put(address, token);
    }

    public String getUsername(String address) {
        return pendingSessions.get(address);
    }

    public byte[] getVerifyToken(String address) {
        return verifyTokens.get(address);
    }

    public void removeSession(String address) {
        pendingSessions.remove(address);
        verifyTokens.remove(address);
    }

    public boolean hasSession(String address) {
        return pendingSessions.containsKey(address) && verifyTokens.containsKey(address);
    }
}