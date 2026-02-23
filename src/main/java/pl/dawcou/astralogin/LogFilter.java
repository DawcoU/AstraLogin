package pl.dawcou.astralogin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

public class LogFilter extends AbstractFilter {

    private final AstraLogin plugin;

    public LogFilter(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Result filter(LogEvent event) {
        Message msg = event.getMessage();
        if (msg == null) return Result.NEUTRAL;

        String formatted = msg.getFormattedMessage();
        if (formatted == null || !formatted.contains("issued server command:")) {
            return Result.NEUTRAL;
        }

        String lower = formatted.toLowerCase();
        if (lower.contains("/login ") ||
                lower.contains("/register ") ||
                lower.contains("/zaloguj ") ||
                lower.contains("/zarejestruj ") ||
                lower.contains("/zmienhaslo ") ||
                lower.contains("/changepassword ")) {

            // Pobieramy TYLKO akcję. Domyślnie "deny", bo to najbezpieczniejsza opcja.
            String action = plugin.getConfig().getString("security.logger.action", "deny");

            if (action.equalsIgnoreCase("mask")) {
                String masked = maskPassword(formatted);
                LogManager.getLogger("AstraLogin").info(masked);
            }

            // Oryginał ZAWSZE ginie. Zero luk.
            return Result.DENY;
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