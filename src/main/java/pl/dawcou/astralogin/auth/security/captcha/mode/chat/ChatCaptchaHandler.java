package pl.dawcou.astralogin.auth.security.captcha.mode.chat;

import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ChatCaptchaHandler {

    private static final Map<UUID, Integer> activeChallenges = new ConcurrentHashMap<>();

    public static void openChatCaptcha(Player player, AstraLogin plugin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int type = random.nextInt(3);
        int result;
        String equation;

        if (type == 0) {
            int a = random.nextInt(2, 10);
            int b = random.nextInt(2, 10);
            int c = random.nextInt(2, 4);
            result = (a + b) * c;
            equation = "(" + a + " + " + b + ") * " + c;
        } else if (type == 1) {
            int a = random.nextInt(3, 8);
            int b = random.nextInt(3, 8);
            int c = random.nextInt(1, 15);
            result = (a * b) - c;
            equation = "(" + a + " * " + b + ") - " + c;
        } else {
            int a = random.nextInt(10, 30);
            int b = random.nextInt(5, 15);
            int c = random.nextInt(1, 10);
            result = a + b - c;
            equation = a + " + " + b + " - " + c;
        }

        activeChallenges.put(player.getUniqueId(), result);

        String message = plugin.getLanguageManager().getWithPrefix(player, "captcha.chat.instruction")
                .replace("%equation%", equation);
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