package pl.dawcou.astralogin.auth.passwords;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;

//--------------------------------------------------
// Password validation engine using config settings
//--------------------------------------------------
public class PasswordValidator {

    public enum ValidationResult {
        SUCCESS,
        INVALID_CHARACTERS,
        ONLY_LETTERS_FORBIDDEN,
        ONLY_DIGITS_FORBIDDEN
    }

    public static ValidationResult validate(String password, FileConfiguration config, Logger logger) {
        boolean enabled = config.getBoolean("features.password.validation.enabled", true);

        if (!enabled) {
            return ValidationResult.SUCCESS;
        }

        String pattern = config.getString("features.password.validation.pattern", "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$");

        try {
            if (!password.matches(pattern)) {
                return ValidationResult.INVALID_CHARACTERS;
            }
        } catch (PatternSyntaxException e) {
            // Logujemy błąd w konsoli, żeby admin wiedział co się zepsuło
            logger.severe("Invalid regex pattern in config.yml (features.password.validation.pattern): " + e.getMessage());

            // Nie blokujemy walidacji znaków dopóki błąd nie zostanie naprawiony
            return ValidationResult.SUCCESS;
        }

        boolean blockOnlyLetters = config.getBoolean("features.password.validation.block-only-letters", false);
        if (blockOnlyLetters && password.matches("^[a-zA-Z]+$")) {
            return ValidationResult.ONLY_LETTERS_FORBIDDEN;
        }

        boolean blockOnlyDigits = config.getBoolean("features.password.validation.block-only-digits", false);
        if (blockOnlyDigits && password.matches("^[0-9]+$")) {
            return ValidationResult.ONLY_DIGITS_FORBIDDEN;
        }

        return ValidationResult.SUCCESS;
    }
}