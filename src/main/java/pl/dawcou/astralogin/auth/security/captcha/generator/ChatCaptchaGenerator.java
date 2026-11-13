package pl.dawcou.astralogin.auth.security.captcha.generator;

import pl.dawcou.astralogin.AstraLogin;

import java.util.concurrent.ThreadLocalRandom;

public class ChatCaptchaGenerator {

    public record ChallengeResult(String equation, int result) {}

    public static ChallengeResult generate(AstraLogin plugin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String difficulty = plugin.getConfig().getString("security.captcha.modes.chat.difficulty", "medium").toLowerCase();
        int result;
        String equation;

        switch (difficulty) {
            case "easy": {
                int a = random.nextInt(1, 15);
                int b = random.nextInt(1, 15);
                if (random.nextBoolean()) {
                    result = a + b;
                    equation = a + " + " + b;
                } else {
                    if (a < b) { int temp = a; a = b; b = temp; }
                    result = a - b;
                    equation = a + " - " + b;
                }
                break;
            }
            case "hard": {
                int type = random.nextInt(3);
                if (type == 0) {
                    int a = random.nextInt(2, 6);
                    int b = random.nextInt(2, 6);
                    int c = random.nextInt(2, 5);
                    result = (a * b) * c;
                    equation = "(" + a + " * " + b + ") * " + c;
                } else if (type == 1) {
                    int a = random.nextInt(10, 30);
                    int b = random.nextInt(5, 15);
                    int c = random.nextInt(2, 6);
                    result = (a + b) * c;
                    equation = "(" + a + " + " + b + ") * " + c;
                } else {
                    int divisor = random.nextInt(2, 6);
                    int quotient = random.nextInt(2, 10);
                    int dividend = divisor * quotient;
                    int addend = random.nextInt(1, 10);
                    result = quotient + addend;
                    equation = "(" + dividend + " / " + divisor + ") + " + addend;
                }
                break;
            }
            case "medium":
            default: {
                int type = random.nextInt(3);
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
                break;
            }
        }

        return new ChallengeResult(equation, result);
    }
}