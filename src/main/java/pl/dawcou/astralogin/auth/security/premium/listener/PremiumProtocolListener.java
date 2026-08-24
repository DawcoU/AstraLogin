package pl.dawcou.astralogin.auth.security.premium.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import pl.dawcou.astralogin.auth.AstraLogin;
import pl.dawcou.astralogin.auth.security.premium.api.MojangApiService;
import pl.dawcou.astralogin.auth.security.premium.crypto.EncryptionUtil;
import pl.dawcou.astralogin.auth.security.premium.netty.NettyChannelExtractor;
import pl.dawcou.astralogin.auth.security.premium.session.PremiumSessionManager;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PremiumProtocolListener implements Listener {

    private final AstraLogin plugin;
    private final ProtocolManager protocolManager;
    private final KeyPair keyPair;

    private final MojangApiService mojangApi;
    private final NettyChannelExtractor nettyExtractor;
    private final PremiumSessionManager sessionManager;

    public PremiumProtocolListener(AstraLogin plugin, ProtocolManager protocolManager) {
        this.plugin = plugin;
        this.protocolManager = protocolManager;
        this.keyPair = plugin.getKeyPair();

        this.mojangApi = new MojangApiService(plugin);
        this.nettyExtractor = new NettyChannelExtractor(plugin);
        this.sessionManager = new PremiumSessionManager();

        registerListeners();
    }

    private void debug(String message) {
        if (plugin.isDebugMode()) {
            plugin.getLogger().info(message);
        }
    }

    private void debugWarning(String message) {
        if (plugin.isDebugMode()) {
            plugin.getLogger().warning(message);
        }
    }

    private void debugSevere(String message) {
        if (plugin.isDebugMode()) {
            plugin.getLogger().severe(message);
        }
    }

    private void registerListeners() {
        // --- 1. ODBIERANIE PACKET START & WYSYŁANIE ENCRYPTION REQUEST ---
        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Login.Client.START) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                debug("Odebrano Pakiet START!");

                boolean premiumAllowed = PremiumProtocolListener.this.plugin.getPremiumManager().premiumLoginRequirements();
                if (!premiumAllowed) return;

                Player player = event.getPlayer();
                String username;

                try {
                    username = event.getPacket().getStrings().read(0);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                // Wyciągamy adres i Channel OD RAZU na głównym wątku!
                Channel channel = nettyExtractor.extractChannelFromEvent(event);

                String addr = "UNKNOWN";
                if (player != null && player.getAddress() != null) {
                    addr = player.getAddress().getAddress().getHostAddress();
                } else if (channel != null && channel.remoteAddress() != null) {
                    addr = channel.remoteAddress().toString().replace("/", "");
                }

                final String clientAddress = addr;
                String offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();

                if (!PremiumProtocolListener.this.plugin.getPasswordManager().isRegistered(offlineUuid)) {
                    return;
                }

                event.setCancelled(true);
                final PacketContainer startPacket = event.getPacket();

                PremiumProtocolListener.this.plugin.getSchedulerManager().runAsync(() -> {
                    boolean isAccountPremium = PremiumProtocolListener.this.plugin.getPremiumManager().isUsernamePremium(username);

                    if (!isAccountPremium) {
                        try {
                            protocolManager.receiveClientPacket(player, startPacket, false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return;
                    }

                    PremiumProtocolListener.this.plugin.getSchedulerManager().runSync(() -> {
                        if (channel != null) {
                            plugin.getLogger().info("[NETTY] BEFORE ENCRYPTION_BEGIN pipeline=" + channel.pipeline().names());
                        }

                        sessionManager.createSession(clientAddress, username);
                        byte[] verifyToken = sessionManager.getVerifyToken(clientAddress);

                        PacketContainer encryptionRequest = protocolManager.createPacket(PacketType.Login.Server.ENCRYPTION_BEGIN);

                        try {
                            // 1. Server ID (zawsze pusty)
                            encryptionRequest.getStrings().writeSafely(0, "");

                            // 2. Klucz Publiczny RSA i Token
                            PublicKey pubKey = keyPair.getPublic();
                            byte[] pubKeyBytes = pubKey.getEncoded();

                            // Sprawdzamy jak ProtocolLib operuje na tym pakiecie w danej wersji
                            if (encryptionRequest.getSpecificModifier(PublicKey.class).size() > 0) {
                                encryptionRequest.getSpecificModifier(PublicKey.class).write(0, pubKey);
                                encryptionRequest.getByteArrays().writeSafely(0, verifyToken);
                            } else if (encryptionRequest.getByteArrays().size() >= 2) {
                                encryptionRequest.getByteArrays().writeSafely(0, pubKeyBytes);
                                encryptionRequest.getByteArrays().writeSafely(1, verifyToken);
                            } else {
                                encryptionRequest.getByteArrays().writeSafely(0, pubKeyBytes);
                                encryptionRequest.getByteArrays().writeSafely(1, verifyToken);
                            }

                            // Wymuszamy uwierzytelnianie klienta przez Mojang
                            if (encryptionRequest.getBooleans().size() > 0) {
                                encryptionRequest.getBooleans().write(0, true);
                                debug("[ENCRYPTION-REQUEST] shouldAuthenticate=true");
                            }

                            protocolManager.sendServerPacket(player, encryptionRequest);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                });
            }
        });

        // --- 2. ODBIERANIE ODPOWIEDZI SZYFROWANIA OD KLIENTA ---
        protocolManager.addPacketListener(new PacketAdapter(
                PacketAdapter.params()
                        .plugin(plugin)
                        .types(PacketType.Login.Client.ENCRYPTION_BEGIN)
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                debug("DEBUG [4.0/6] Received ENCRYPTION_BEGIN packet from client.");

                Player player = event.getPlayer();

                if (player == null || player.getAddress() == null) {
                    debugWarning("DEBUG [4.1/6] ❌ Player or address is null!");
                    return;
                }

                String clientAddress = player.getAddress().getAddress().getHostAddress();
                String username = sessionManager.getUsername(clientAddress);
                byte[] expectedToken = sessionManager.getVerifyToken(clientAddress);

                debug("DEBUG [4.2/6] 🔎 Session lookup: IP=" + clientAddress + ", pendingUsername=" + username + ", tokenPresent=" + (expectedToken != null));

                if (username == null || expectedToken == null) {
                    debug("DEBUG [4.3/6] ⏭️ No premium session for " + clientAddress + " - bypassing.");
                    return;
                }

                // Anulujemy pakiet natychmiast na głównym wątku, aby serwer offline go nie przetwarzał
                event.setCancelled(true);

                // Pobieramy Channel oraz bajty od razu, zanim ProtocolLib wyczyści event
                Channel channel = nettyExtractor.extractChannelFromEvent(event);
                if (channel == null) {
                    debugSevere("DEBUG [ERR] ❌ Could not extract Netty channel for " + username);
                    sessionManager.removeSession(clientAddress);
                    return;
                }

                PacketContainer packet = event.getPacket();
                byte[] encryptedSecret = null;
                byte[] encryptedToken = null;

                try {
                    List<byte[]> byteArrays = packet.getByteArrays().getValues();
                    if (byteArrays.size() >= 2) {
                        encryptedSecret = byteArrays.get(0);
                        encryptedToken = byteArrays.get(1);
                    } else {
                        encryptedSecret = packet.getSpecificModifier(byte[].class).read(0);
                        encryptedToken = packet.getSpecificModifier(byte[].class).read(1);
                    }
                } catch (Exception e) {
                    sessionManager.removeSession(clientAddress);
                    debugSevere("DEBUG [ERR] ❌ Failed to read byte arrays from packet: " + e.getClass().getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    return;
                }

                if (encryptedSecret == null || encryptedToken == null) {
                    sessionManager.removeSession(clientAddress);
                    debugWarning("DEBUG [ERR] ❌ Null encryption data received from " + clientAddress);
                    return;
                }

                final byte[] finalEncryptedSecret = encryptedSecret;
                final byte[] finalEncryptedToken = encryptedToken;

                // Przechodzimy do wykonywania zadań asynchronicznych z pobranymi danymi
                PremiumProtocolListener.this.plugin.getSchedulerManager().runAsync(() -> {
                    debug("DEBUG [5.0/6] 🔓 Starting RSA decryption for " + username);

                    SecretKey sharedSecret;
                    byte[] decryptedToken;

                    try {
                        sharedSecret = EncryptionUtil.decryptSharedSecret(keyPair.getPrivate(), finalEncryptedSecret);
                        decryptedToken = EncryptionUtil.decrypt(keyPair.getPrivate(), finalEncryptedToken);
                    } catch (Exception e) {
                        sessionManager.removeSession(clientAddress);
                        debugSevere("DEBUG [ERR] ❌ Exception during RSA decryption: " + e.getClass().getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        channel.close();
                        return;
                    }

                    if (sharedSecret == null || decryptedToken == null) {
                        sessionManager.removeSession(clientAddress);
                        debugWarning("DEBUG [ERR] ❌ Failed to decrypt keys for " + clientAddress);
                        channel.close();
                        return;
                    }

                    boolean tokenMatches = Arrays.equals(expectedToken, decryptedToken);

                    if (!tokenMatches) {
                        sessionManager.removeSession(clientAddress);
                        debugWarning("DEBUG [ERR] ❌ Verify token mismatch for " + clientAddress + "!");
                        channel.close();
                        return;
                    }

                    sessionManager.removeSession(clientAddress);

                    debug("DEBUG [KEYS-CHECK] 🔑 SharedSecret raw bytes length: " + (sharedSecret != null ? sharedSecret.getEncoded().length : "null"));
                    debug("DEBUG [KEYS-CHECK] 🔑 PublicKey raw bytes length: " + (keyPair.getPublic() != null ? keyPair.getPublic().getEncoded().length : "null"));

                    try {
                        String serverId = "";
                        String serverHash = EncryptionUtil.generateServerHash(serverId, keyPair.getPublic(), sharedSecret);

                        debug("[HASH-CHECK] serverId='" + serverId + "'");
                        debug("[HASH-CHECK] sharedSecretLength=" + sharedSecret.getEncoded().length);
                        debug("[HASH-CHECK] publicKeyLength=" + keyPair.getPublic().getEncoded().length);
                        debug("[HASH-CHECK] serverHash=" + serverHash);

                        debug("DEBUG [5.7/6] 🌐 Querying Mojang hasJoined API for " + username);

                        debug("DEBUG [HASH-CHECK] ServerId=" + serverId + " | Hash=" + serverHash);

                        WrappedGameProfile mojangProfile = mojangApi.fetchMojangProfile(username, serverHash);

                        if (mojangProfile == null) {
                            debugWarning("DEBUG [FAIL] ❌ Mojang hasJoined returned no profile for " + username);
                            if (channel.isOpen()) {
                                channel.close();
                            }
                            return;
                        }

                        PremiumProtocolListener.this.plugin.getPremiumManager().setAuthenticated(mojangProfile.getUUID(), true);

                        // Wstrzykujemy szyfrowanie bezpośrednio do obiektu Channel
                        nettyExtractor.enableEncryptionOnChannel(channel, sharedSecret, () -> {
                            sendLoginSuccess(player, mojangProfile);
                            debug("DEBUG [SUCCESS] 🔓 Player " + username + " (" + mojangProfile.getUUID() + ") successfully verified by Mojang!");
                        });

                    } catch (Exception e) {
                        debugSevere("DEBUG [ERR] ❌ Error during Mojang premium verification: " + e.getClass().getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        if (channel.isOpen()) {
                            channel.close();
                        }
                    }
                });
            }
        });
    }

    private void sendLoginSuccess(Player player, WrappedGameProfile profile) {
        PacketContainer success = protocolManager.createPacket(PacketType.Login.Server.SUCCESS);
        success.getGameProfiles().write(0, profile);

        try {
            protocolManager.sendServerPacket(player, success);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}