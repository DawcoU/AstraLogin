package pl.dawcou.astralogin.auth.security.captcha.mode.chat;

import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.captcha.generator.ChatCaptchaGenerator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatCaptchaHandler {

    private static final Map<UUID, Integer> activeChallenges = new ConcurrentHashMap<>();

    public static void openChatCaptcha(Player player, AstraLogin plugin) {
        ChatCaptchaGenerator.ChallengeResult challenge = ChatCaptchaGenerator.generate(plugin);
        activeChallenges.put(player.getUniqueId(), challenge.result());

        String message = plugin.getLanguageManager().getWithPrefix(player, "captcha.modes.chat.instruction")
                .replace("%equation%", challenge.equation());
        player.sendMessage(message);
    }

    public static boolean verifyAnswer(Player player, String input) {
        UUID uuid = player.getUniqueId();
        Integer expected = activeChallenges.get(uuid);
        if (expected == null) {
            return false;
        }

        try {
            int parsed = Integer.parseInt(input.trim());
            return parsed == expected;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void removeSession(Player player) {
        activeChallenges.remove(player.getUniqueId());
    }

    public static boolean hasActiveCaptcha(Player player) {
        return activeChallenges.containsKey(player.getUniqueId());
    }
}