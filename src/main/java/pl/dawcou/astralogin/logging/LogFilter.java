package pl.dawcou.astralogin.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;
import pl.dawcou.astralogin.auth.AstraLogin;

public class LogFilter extends AbstractFilter {

    private final AstraLogin plugin;
    private static final Logger ROOT_LOGGER = LogManager.getRootLogger();

    // Zapobiega ponownemu przechwyceniu naszego zamaskowanego logu
    private final ThreadLocal<Boolean> sendingMaskedMessage = ThreadLocal.withInitial(() -> false);

    public LogFilter(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Result filter(LogEvent event) {
        // Jeśli to log wygenerowany przez nas po zamaskowaniu,
        // pozwalamy mu przejść bez ponownego filtrowania.
        if (sendingMaskedMessage.get()) {
            return Result.NEUTRAL;
        }

        Message msg = event.getMessage();
        if (msg == null) return Result.NEUTRAL;

        String formatted = msg.getFormattedMessage();
        if (formatted == null) return Result.NEUTRAL;

        if (!formatted.contains("issued server command:")) {
            return Result.NEUTRAL;
        }

        String lower = formatted.toLowerCase();

        int maskFromArgument = getMaskArgument(lower);

        // Nie jest to komenda zawierająca dane wrażliwe
        if (maskFromArgument < 1) {
            return Result.NEUTRAL;
        }

        String action = plugin.getConfig().getString("security.logger.action", "deny");

        if (action.equalsIgnoreCase("deny")) {
            return Result.DENY;
        }

        if (action.equalsIgnoreCase("mask")) {
            String masked = maskPassword(formatted, maskFromArgument);

            sendingMaskedMessage.set(true);
            try {
                ROOT_LOGGER.info(masked);
            } finally {
                sendingMaskedMessage.remove();
            }

            return Result.DENY;
        }

        return Result.DENY;
    }

    private int getMaskArgument(String message) {
        // /pin set <PIN>
        if (message.contains("/pin ")) {
            return 2;
        }

        // Komendy z hasłem/PIN-em jako pierwszym argumentem
        if (message.contains("/login ") ||
                message.contains("/l ") ||
                message.contains("/register ") ||
                message.contains("/reg ") ||
                message.contains("/zaloguj ") ||
                message.contains("/zarejestruj ") ||
                message.contains("/zmienhaslo ") ||
                message.contains("/changepassword ") ||
                message.contains("/niepamietamhasla ") ||
                message.contains("/forgotpassword ") ||
                message.contains("/forgotpass ")) {
            return 1;
        }

        return 0;
    }

    private String maskPassword(String message, int maskFromArgument) {
        if (message == null || message.isEmpty() || maskFromArgument < 1) {
            return message;
        }

        String[] commands = {
                "/login", "/l",
                "/register", "/reg",
                "/zaloguj", "/zarejestruj",
                "/zmienhaslo", "/changepassword",
                "/niepamietamhasla", "/forgotpassword", "/forgotpass",
                "/pin"
        };

        String lowerMessage = message.toLowerCase();

        for (String cmd : commands) {
            String commandWithSpace = cmd + " ";
            int index = lowerMessage.indexOf(commandWithSpace);

            if (index != -1) {
                int argumentsStart = index + commandWithSpace.length();

                String beforeArguments = message.substring(0, argumentsStart);
                String arguments = message.substring(argumentsStart);

                String[] args = arguments.split(" ");

                if (args.length < maskFromArgument) {
                    return message;
                }

                StringBuilder maskedArguments = new StringBuilder();

                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        maskedArguments.append(" ");
                    }

                    if (i + 1 >= maskFromArgument) {
                        maskedArguments.append("******");
                    } else {
                        maskedArguments.append(args[i]);
                    }
                }

                return beforeArguments + maskedArguments;
            }
        }

        return message;
    }
}