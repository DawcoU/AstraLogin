package pl.dawcou.astralogin.auth.security.premium.protocol;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.premium.protocol.nms.NMSReflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class LoginCompleter {

    private final AstraLogin plugin;

    public LoginCompleter(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void complete(Channel channel, UUID uuid, WrappedGameProfile mojangProfile) {
        plugin.getPacketListener().debug(
                "DEBUG [8.0] 🚀 LoginCompleter started."
        );

        try {
            if (channel == null || !channel.isOpen()) {
                plugin.getPacketListener().debugSevere("[LoginCompleter] Provided Netty channel is null or closed!");
                return;
            }

            Object packetHandler = channel.pipeline().get("packet_handler");

            if (packetHandler == null) {
                killConnection(channel, "Netty handler 'packet_handler' was not found");
                return;
            }

            plugin.getPacketListener().debug(
                    "DEBUG [8.1] 📦 Found Netty 'packet_handler'."
            );

            Object loginListener = NMSReflection.findPacketListener(plugin, packetHandler);

            if (loginListener == null) {
                killConnection(channel, "Could not find PacketListener in NetworkManager");
                return;
            }

            plugin.getPacketListener().debug(
                    "DEBUG [8.2] 🎯 PacketListener resolved: "
                            + loginListener.getClass().getName()
            );

            GameProfile profile = NMSReflection.toNmsProfile(mojangProfile);
            Class<?> listenerClass = loginListener.getClass();

            plugin.getPacketListener().debug(
                    "DEBUG [8.3] 🔍 Detecting compatible login completion method."
            );

            /*
             * 1. Modern Method Lookup (MC 1.20.2+ / 1.21.x+)
             * Inject GameProfile directly and invoke the completion method (e.g. finishLoginAndWaitForClient).
             * Required for modern protocol state machine (Login -> Configuration -> Game).
             */
            Field gameProfileField = NMSReflection.findFieldByType(listenerClass, GameProfile.class);
            if (gameProfileField != null) {
                gameProfileField.setAccessible(true);
                gameProfileField.set(loginListener, profile);
            } else {
                killConnection(channel, "Could not find GameProfile field on login listener");
                return;
            }

            // Search dynamically for completion method (e.g. finishLoginAndWaitForClient)
            Method profileCompletionMethod = findProfileCompletionMethod(listenerClass, profile.getClass());
            if (profileCompletionMethod != null) {
                plugin.getPacketListener().debug(
                        "DEBUG [8.4] ▶️ Invoking profile completion method: " + profileCompletionMethod.getName()
                );

                profileCompletionMethod.setAccessible(true);
                profileCompletionMethod.invoke(loginListener, profile);

                success(uuid, profile);
                return;
            }

            // Fallback: If no method found, try setting State enum directly
            Field stateField = NMSReflection.findLoginStateField(listenerClass);
            if (stateField != null) {
                Object readyState = NMSReflection.findReadyState(plugin, stateField.getType());
                if (readyState != null) {
                    stateField.setAccessible(true);
                    stateField.set(loginListener, readyState);

                    plugin.getPacketListener().debug(
                            "DEBUG [8.4b] 🔄 Fallback: Updated LoginListener state to READY_TO_ACCEPT."
                    );
                    success(uuid, profile);
                    return;
                }
            }

            /*
             * 2. Legacy State / Handler Fallback (MC 1.18.x - 1.20.1 & transitional 1.20.x)
             * Fallback for older packet flow or legacy method invocation (handleAcceptedLogin / State transition).
             */
            Method parameterlessMethod = NMSReflection.findMethod(listenerClass, "handleAcceptedLogin");
            if (parameterlessMethod != null) {
                plugin.getPacketListener().debug(
                        "DEBUG [8.5] ▶️ Using handleAcceptedLogin()."
                );

                parameterlessMethod.setAccessible(true);
                parameterlessMethod.invoke(loginListener);

                success(uuid, profile);
                return;
            }

            /*
             * 3. Legacy fallback
             */
            plugin.getPacketListener().debug(
                    "DEBUG [8.6] ▶️ Using legacy LoginListener state transition."
            );

            completeLegacyLogin(loginListener, listenerClass, profile);
            success(uuid, profile);

        } catch (Throwable e) {
            plugin.getLogger().severe(
                    "[8.ERR] ❌ Critical error while completing login: "
                            + e.getClass().getName() + ": " + e.getMessage()
            );
            e.printStackTrace();
            killConnection(channel, "Exception during login completion: " + e.getClass().getSimpleName());
        }
    }

    private void completeLegacyLogin(Object loginListener, Class<?> listenerClass, GameProfile profile) throws Exception {
        Field gameProfileField = NMSReflection.findFieldByType(
                listenerClass,
                GameProfile.class
        );

        if (gameProfileField == null) {
            throw new NoSuchFieldException("GameProfile field");
        }

        gameProfileField.setAccessible(true);
        gameProfileField.set(loginListener, profile);

        Field stateField = NMSReflection.findLoginStateField(listenerClass);

        if (stateField == null) {
            throw new NoSuchFieldException("Login state field");
        }

        Object readyState = NMSReflection.findReadyState(plugin, stateField.getType());

        if (readyState == null) {
            throw new IllegalStateException("READY_TO_ACCEPT state not found");
        }

        stateField.setAccessible(true);
        stateField.set(loginListener, readyState);

        /*
         * Do not invoke an obfuscated completion method here.
         * The normal LoginListener tick will detect READY_TO_ACCEPT
         * and finish the login itself.
         */
    }

    private void success(UUID uuid, GameProfile profile) {
        if (uuid != null) {
            plugin.getPremiumManager().setAuthenticated(uuid, true);
        }

        plugin.getPacketListener().debug(
                "DEBUG [8.7] ✅ Successfully completed Premium authorization for "
                        + NMSReflection.getProfileName(profile)
        );
    }

    /**
     * Helper to dynamically scan for a completion method accepting GameProfile as parameter
     */
    private Method findProfileCompletionMethod(Class<?> listenerClass, Class<?> profileClass) {
        for (Class<?> current = listenerClass; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(profileClass)
                        && method.getReturnType().equals(void.class)) {

                    String mName = method.getName().toLowerCase();

                    // Filter out irrelevant single-parameter methods (Disconnect, Kick, Cookie API)
                    if (!mName.contains("disconnect")
                            && !mName.contains("kick")
                            && !mName.contains("cookie")) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private void killConnection(Channel channel, String reason) {
        plugin.getPacketListener().debugSevere(
                "[LoginCompleter] Kicking connection for channel "
                        + (channel != null ? channel.remoteAddress() : "null")
                        + " -> Reason: " + reason
        );

        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }
}