package pl.dawcou.astralogin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

public class LogFilter extends AbstractFilter {

    private final AstraLogin plugin;
    private static final Logger ROOT_LOGGER = LogManager.getRootLogger();
    // Niewidzialny znacznik: reset koloru, którego nie widać w konsoli
    private static final String HIDDEN_MARKER = "§r";

    public LogFilter(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Result filter(LogEvent event) {
        Message msg = event.getMessage();
        if (msg == null) return Result.NEUTRAL;

        String formatted = msg.getFormattedMessage();
        if (formatted == null) return Result.NEUTRAL;

        // Jeśli log kończy się naszym niewidzialnym markerem - puszczaj!
        if (formatted.endsWith(HIDDEN_MARKER)) {
            return Result.NEUTRAL;
        }

        if (formatted.contains("issued server command:")) {
            String lower = formatted.toLowerCase();
            if (lower.contains("/login ") || lower.contains("/register ") ||
                    lower.contains("/zaloguj ") || lower.contains("/zarejestruj ") ||
                    lower.contains("/zmienhaslo ") || lower.contains("/changepassword ")) {

                // Wewnątrz filter(LogEvent event)
                String action = plugin.getConfig().getString("security.logger.action", "deny");

                if (action.equalsIgnoreCase("deny")) {
                    return Result.DENY; // Po prostu blokujemy, nic nie wypisujemy
                }

                if (action.equalsIgnoreCase("mask")) {
                    String masked = maskPassword(formatted);
                    // Wysyłamy zamaskowane
                    ROOT_LOGGER.info(AstraLogin.PREFIX2 + " §f" + masked + HIDDEN_MARKER);
                    return Result.DENY;
                }

                return Result.DENY;
            }
        }

        return Result.NEUTRAL;
    }

    private String maskPassword(String message) {
        if (message == null || message.isEmpty()) return message;

        // Lista komend, po których chcemy uciąć resztę
        String[] commands = {"/login", "/register", "/zaloguj", "/zarejestruj", "/zmienhaslo", "/changepassword"};

        for (String cmd : commands) {
            if (message.toLowerCase().contains(cmd + " ")) {
                int index = message.toLowerCase().indexOf(cmd + " ");
                // Zwracamy wszystko do komendy włącznie + gwiazdki
                return message.substring(0, index + cmd.length() + 1) + "********";
            }
        }
        return message;
    }
}