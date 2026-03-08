package pl.dawcou.astralogin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

public class LogFilter extends AbstractFilter {

    private final AstraLogin plugin;
    private static final Logger ROOT_LOGGER = LogManager.getRootLogger();
    // Niewidzialny znacznik: reset koloru, którego nie widać w konsoli 🛡️
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

        // Jeśli log kończy się naszym niewidzialnym markerem - puszczaj! ⚓
        if (formatted.endsWith(HIDDEN_MARKER)) {
            return Result.NEUTRAL;
        }

        if (formatted.contains("issued server command:")) {
            String lower = formatted.toLowerCase();
            if (lower.contains("/login ") || lower.contains("/register ") ||
                    lower.contains("/zaloguj ") || lower.contains("/zarejestruj ") ||
                    lower.contains("/zmienhaslo ") || lower.contains("/changepassword ")) {

                String action = plugin.getConfig().getString("security.logger.action", "deny");

                if (action.equalsIgnoreCase("mask")) {
                    String masked = maskPassword(formatted);

                    // WYSYŁAMY: Prefix + Wiadomość + Niewidzialny Marker
                    // W konsoli marker §r nie zajmuje miejsca i jest niewidoczny! 🚀
                    ROOT_LOGGER.info(AstraLogin.PREFIX2 + " §f" + masked + HIDDEN_MARKER);
                }

                return Result.DENY;
            }
        }

        return Result.NEUTRAL;
    }

    private String maskPassword(String message) {
        if (message == null || message.isEmpty()) return message;
        String[] parts = message.split(" ");
        if (parts.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                sb.append(parts[i]).append(" ");
            }
            sb.append("********");
            return sb.toString();
        }
        return message;
    }
}