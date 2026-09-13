package pl.dawcou.astralogin.auth.security.premium.session;

import io.netty.channel.Channel;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PremiumSessionManager {

    private final SecureRandom random = new SecureRandom();
    private final Map<Channel, PremiumSession> sessions = new ConcurrentHashMap<>();

    public void createSession(Channel channel, String username) {
        byte[] token = new byte[4];
        random.nextBytes(token);

        sessions.put(channel, new PremiumSession(username, token));

        // Automatyczne sprzątanie po rozłączeniu
        channel.closeFuture().addListener(future -> sessions.remove(channel));
    }

    public String getUsername(Channel channel) {
        PremiumSession session = sessions.get(channel);
        return session != null ? session.username() : null;
    }

    public byte[] getVerifyToken(Channel channel) {
        PremiumSession session = sessions.get(channel);
        return session != null ? session.verifyToken() : null;
    }

    public void removeSession(Channel channel) {
        sessions.remove(channel);
    }

    public boolean hasSession(Channel channel) {
        return sessions.containsKey(channel);
    }

    private record PremiumSession(String username, byte[] verifyToken) {

    }
}