package pl.dawcou.astralogin.auth.security.premium.protocol.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientEncryptionResponse;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerEncryptionRequest;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.premium.api.MojangApiService;
import pl.dawcou.astralogin.auth.security.premium.protocol.LoginCompleter;
import pl.dawcou.astralogin.auth.security.premium.protocol.NMSReflection;
import pl.dawcou.astralogin.auth.security.premium.protocol.NettyChannelExtractor;
import pl.dawcou.astralogin.auth.security.premium.protocol.cipher.EncryptionUtils;
import pl.dawcou.astralogin.auth.security.premium.session.PremiumSessionManager;

import javax.crypto.SecretKey;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.UUID;

public class PacketEventsListener extends PacketListenerAbstract implements Listener {

    private final AstraLogin plugin;
    private final KeyPair keyPair;
    private final MojangApiService mojangApi;
    private final NettyChannelExtractor nettyExtractor;
    private final PremiumSessionManager sessionManager;

    public PacketEventsListener(AstraLogin plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.keyPair = plugin.getKeyPair();
        this.mojangApi = new MojangApiService(plugin);
        this.nettyExtractor = new NettyChannelExtractor(plugin);
        this.sessionManager = new PremiumSessionManager();

        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType().toString().contains("LOGIN") || event.getPacketType().toString().contains("ENCRYPTION")) {
            plugin.getPremiumManager().debug("DEBUG [PACKET-IN] Type: " + event.getPacketType());
        }

        // --- 1. ODBIERANIE PAKIETU STARTOWEGO LOGOWANIA ---
        if (event.getPacketType() == PacketType.Login.Client.LOGIN_START) {
            plugin.getPremiumManager().debug("DEBUG [1.0] 📥 Received START packet (PacketEvents).");

            boolean premiumAllowed = plugin.getPremiumManager().premiumLoginRequirements("FULL");
            if (!premiumAllowed) {
                plugin.getPremiumManager().debug("DEBUG [1.1] ⏭️ Premium login is disabled.");
                return;
            }

            WrapperLoginClientLoginStart loginStart = new WrapperLoginClientLoginStart(event);
            String username = loginStart.getUsername();
            final String playerName = username;

            Channel channel = (Channel) event.getChannel();
            plugin.getPremiumManager().debug("DEBUG [1.3] 🔌 Netty channel extracted: " + channel);

            // Natychmiastowe anulowanie pakietu startowego przed jakimkolwiek zapytaniem do sieci
            event.setCancelled(true);

            UUID playerUuid = plugin.getAccountManager().getUuidByUsername(username);
            if (playerUuid == null) {
                playerUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            }

            boolean isRegistered = plugin.getPasswordManager().isRegistered(playerUuid);
            if (isRegistered) {
                plugin.getPremiumManager().debugWarning("DEBUG [1.4] 🔒 Player " + username + " is registered. Enabling premium bypass flag.");
                plugin.getPremiumManager().markPendingBypass(playerUuid, true);
            } else {
                plugin.getPremiumManager().debugWarning("DEBUG [1.4] ℹ️ Player " + username + " is not registered. Setting bypass flag to false.");
                plugin.getPremiumManager().markPendingBypass(playerUuid, false);
            }

            // Asynchroniczna weryfikacja statusu konta premium przez API Mojanga
            UUID finalPlayerUuid = playerUuid;
            InetAddress address = event.getUser().getAddress().getAddress();

            plugin.getSchedulerManager().runAsync(() -> {
                boolean isAccountPremium = plugin.getPremiumManager().isUsernamePremium(playerName);

                if (!isAccountPremium) {
                    plugin.getPremiumManager().debug("DEBUG [1.6] ⏭️ Player is not premium. Processing as offline login.");

                    // Obsługa gracza non-premium za pomocą osobnej metody, zachowując natychmiastowe anulowanie
                    handleOfflineLogin(channel, address, finalPlayerUuid, playerName);
                    return;
                }

                // Inicjalizacja bezpiecznej sesji i wysłanie żądania szyfrowania dla graczy premium
                plugin.getSchedulerManager().runSync(() -> {
                    sessionManager.createSession(channel, playerName);
                    plugin.getPremiumManager().debug("DEBUG [1.7] 🔐 Premium session created for " + playerName);

                    byte[] verifyToken = sessionManager.getVerifyToken(channel);
                    plugin.getPremiumManager().debug("DEBUG [1.8] 🎫 Verify token generated.");

                    try {
                        byte[] pubKeyBytes = keyPair.getPublic().getEncoded();
                        WrapperLoginServerEncryptionRequest encryptionRequest = new WrapperLoginServerEncryptionRequest(
                                "",
                                pubKeyBytes,
                                verifyToken,
                                true
                        );

                        event.getUser().sendPacket(encryptionRequest);
                        plugin.getPremiumManager().debug("DEBUG [1.9] 📤 Encryption Request sent to " + playerName);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            });
        }

        // --- 2. RECEIVING ENCRYPTION RESPONSE ---
        else if (event.getPacketType() == PacketType.Login.Client.ENCRYPTION_RESPONSE) {
            plugin.getPremiumManager().debug("DEBUG [2.0] 📥 Received ENCRYPTION_RESPONSE packet from client (PacketEvents).");

            Channel channel = (Channel) event.getChannel();

            InetSocketAddress socketAddress = event.getUser().getAddress();
            String clientAddress = socketAddress.getAddress() != null
                    ? socketAddress.getAddress().getHostAddress()
                    : "UNKNOWN";

            String username = sessionManager.getUsername(channel);
            byte[] expectedToken = sessionManager.getVerifyToken(channel);

            if (username == null || expectedToken == null) {
                plugin.getPremiumManager().debug("DEBUG [2.1] ⏭️ No premium session for channel - bypassing.");
                return;
            }

            event.setCancelled(true);

            WrapperLoginClientEncryptionResponse encryptionResponse = new WrapperLoginClientEncryptionResponse(event);
            byte[] encryptedSecret = encryptionResponse.getEncryptedSharedSecret();
            byte[] encryptedToken = encryptionResponse.getEncryptedVerifyToken().orElse(null);

            if (encryptedToken == null) {
                sessionManager.removeSession(channel);
                plugin.getPremiumManager().debugWarning("DEBUG [2.2] ❌ Null encryption token received from " + clientAddress);
                return;
            }

            plugin.getSchedulerManager().runAsync(() -> {
                plugin.getPremiumManager().debug("DEBUG [3.0] 🔓 Starting RSA decryption for " + username);

                SecretKey sharedSecret;
                byte[] decryptedToken;

                try {
                    sharedSecret = EncryptionUtils.decryptSharedSecret(keyPair.getPrivate(), encryptedSecret);
                    decryptedToken = EncryptionUtils.decrypt(keyPair.getPrivate(), encryptedToken);
                } catch (Exception e) {
                    sessionManager.removeSession(channel);
                    plugin.getLogger().severe("DEBUG [3.1] ❌ Exception during RSA decryption: " + e.getMessage());
                    e.printStackTrace();
                    channel.close();
                    return;
                }

                if (sharedSecret == null || decryptedToken == null || !Arrays.equals(expectedToken, decryptedToken)) {
                    sessionManager.removeSession(channel);
                    plugin.getPremiumManager().debugWarning("DEBUG [3.2] ❌ Verify token mismatch or decryption error.");
                    channel.close();
                    return;
                }

                sessionManager.removeSession(channel);
                plugin.getPremiumManager().debug("DEBUG [3.3] 🧹 Premium session removed.");

                try {
                    String serverHash = EncryptionUtils.generateServerHash("", keyPair.getPublic(), sharedSecret);
                    plugin.getPremiumManager().debug("DEBUG [4.0] 🌐 Querying Mojang hasJoined API for " + username);

                    GameProfile mojangProfile = mojangApi.fetchMojangProfile(username, serverHash);

                    if (mojangProfile == null) {
                        plugin.getPremiumManager().debugWarning("DEBUG [5.0] ⚠️ No Mojang session found. Falling back to offline login.");
                        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
                        GameProfile offlineProfile = new GameProfile(offlineUuid, username);

                        plugin.getPremiumManager().setAuthenticated(offlineUuid, false);

                        nettyExtractor.enableEncryptionOnChannel(channel, sharedSecret, () -> {
                            try {
                                InetAddress address = socketAddress.getAddress();
                                AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(username, address, offlineUuid);
                                Bukkit.getPluginManager().callEvent(preLoginEvent);

                                if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                                    if (channel.isOpen()) channel.close();
                                    return;
                                }

                                plugin.getSchedulerManager().runSync(() -> {
                                    new LoginCompleter(plugin).complete(channel, offlineUuid, offlineProfile);
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                                if (channel.isOpen()) channel.close();
                            }
                        });
                        return;
                    }

                    UUID finalUUID = NMSReflection.getProfileId(mojangProfile);
                    String finalName = NMSReflection.getProfileName(mojangProfile);
                    if (finalName.equals("Unknown")) {
                        finalName = username;
                    }

                    String finalUsername = finalName;
                    nettyExtractor.enableEncryptionOnChannel(channel, sharedSecret, () -> {
                        try {
                            InetAddress address = socketAddress.getAddress();
                            AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(finalUsername, address, finalUUID);
                            Bukkit.getPluginManager().callEvent(preLoginEvent);

                            if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                                if (channel.isOpen()) channel.close();
                                return;
                            }

                            plugin.getSchedulerManager().runSync(() -> {
                                new LoginCompleter(plugin).complete(channel, finalUUID, mojangProfile);
                            });
                        } catch (Exception e) {
                            plugin.getLogger().severe("DEBUG [6.0] ❌ Error during AsyncPlayerPreLoginEvent execution: " + e.getMessage());
                            e.printStackTrace();
                            if (channel.isOpen()) {
                                channel.close();
                            }
                        }
                    });

                } catch (Exception e) {
                    plugin.getLogger().severe("DEBUG [4.1] ❌ Error during Mojang verification: " + e.getMessage());
                    e.printStackTrace();
                    if (channel.isOpen()) channel.close();
                }
            });
        }
    }

    /**
     * Obsługa logowania graczy non-premium po wykryciu braku konta premium w API Mojanga
     */
    private void handleOfflineLogin(Channel channel, InetAddress address, UUID uuid, String username) {
        try {
            GameProfile offlineProfile = new GameProfile(uuid, username);
            plugin.getPremiumManager().setAuthenticated(uuid, false);

            // Wywołanie eventu asynchronicznie (wymóg silnika Paper)
            AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(username, address, uuid);
            Bukkit.getPluginManager().callEvent(preLoginEvent);

            if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                plugin.getSchedulerManager().runSync(() -> {
                    if (channel.isOpen()) channel.close();
                });
                return;
            }

            // Ukończenie logowania na wątku głównym serwera
            plugin.getSchedulerManager().runSync(() -> {
                new LoginCompleter(plugin).complete(channel, uuid, offlineProfile);
            });
        } catch (Exception e) {
            e.printStackTrace();
            plugin.getSchedulerManager().runSync(() -> {
                if (channel.isOpen()) channel.close();
            });
        }
    }
}