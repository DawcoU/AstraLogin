package pl.dawcou.astralogin.auth.security.captcha.generator;

import pl.dawcou.astralogin.AstraLogin;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;

public class MapCaptchaGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int[][] GRAYSCALE_MAP_PALETTE = {
            // ID mapy, jasność RGB
            {119, 13}, {116, 17}, {117, 21}, {118, 25}, {87, 40}, {84, 53},
            {47, 59}, {85, 65}, {86, 76}, {44, 79}, {91, 81}, {27, 88},
            {45, 96}, {15, 105}, {88, 108}, {46, 112}, {24, 117}, {89, 132},
            {35, 135}, {12, 140}, {25, 144}, {90, 153}, {26, 167}, {13, 171},
            {32, 180}, {14, 199}, {33, 220}, {34, 255}
    };

    public record ChallengeResult(String code, byte[] mapData) {}

    public static ChallengeResult generate(AstraLogin plugin) {
        String difficulty = plugin.getConfig().getString("security.captcha.modes.map.difficulty", "medium").toLowerCase();

        int baseCodeLength = plugin.getConfig().getInt("security.captcha.modes.map.noise.code_length", 6);
        int baseFontSize = plugin.getConfig().getInt("security.captcha.modes.map.noise.font_size", 26);
        int baseNoiseLines = plugin.getConfig().getInt("security.captcha.modes.map.noise.lines", 50);
        int baseNoiseDots = plugin.getConfig().getInt("security.captcha.modes.map.noise.dots", 200);

        int codeLength = baseCodeLength;
        int fontSize = baseFontSize;
        int noiseLines = baseNoiseLines;
        int noiseDots = baseNoiseDots;

        switch (difficulty) {
            case "easy":
                codeLength = Math.max(3, baseCodeLength - 1);
                fontSize = baseFontSize + 4;
                noiseLines = Math.max(10, baseNoiseLines - 25);
                noiseDots = Math.max(50, baseNoiseDots - 100);
                break;
            case "hard":
                codeLength = baseCodeLength + 1;
                fontSize = Math.max(12, baseFontSize - 4);
                noiseLines = baseNoiseLines + 30;
                noiseDots = baseNoiseDots + 150;
                break;
            case "medium":
            default:
                break;
        }

        String code = generateRandomCode(codeLength);
        BufferedImage image = renderCaptchaImage(code, fontSize, noiseLines, noiseDots);
        byte[] mapData = convertImageToMapBytes(image);

        return new ChallengeResult(code, mapData);
    }

    private static String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private static BufferedImage renderCaptchaImage(String code, int fontSize, int linesCount, int dotsCount) {
        int width = 128;
        int height = 128;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Wygładzanie krawędzi, żeby człowiek bez problemu odczytał litery
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Jasne tło
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(0, 0, width, height);

        // 1. Gęste losowe kropki (szum utrudniający OCR botów)
        for (int i = 0; i < dotsCount; i++) {
            g2d.setColor(new Color(secureRandom.nextInt(180), secureRandom.nextInt(180), secureRandom.nextInt(180)));
            int x = secureRandom.nextInt(width);
            int y = secureRandom.nextInt(height);
            g2d.fillRect(x, y, 1 + secureRandom.nextInt(2), 1 + secureRandom.nextInt(2));
        }

        // 2. Losowe linie przecinające obraz
        g2d.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < linesCount; i++) {
            g2d.setColor(new Color(secureRandom.nextInt(150), secureRandom.nextInt(150), secureRandom.nextInt(150)));
            int x1 = secureRandom.nextInt(width);
            int y1 = secureRandom.nextInt(height);
            int x2 = secureRandom.nextInt(width);
            int y2 = secureRandom.nextInt(height);
            g2d.drawLine(x1, y1, x2, y2);
        }

        // 3. Rysowanie znaków z mocniejszym losowym pochyleniem
        g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));

        int startX = 10;
        int y = 78;
        for (int i = 0; i < code.length(); i++) {
            g2d.setColor(new Color(secureRandom.nextInt(40), secureRandom.nextInt(40), secureRandom.nextInt(40)));
            double angle = (secureRandom.nextDouble() - 0.5) * 0.7;
            g2d.rotate(angle, startX, y);
            g2d.drawString(String.valueOf(code.charAt(i)), startX, y);
            g2d.rotate(-angle, startX, y);
            startX += 18;
        }

        g2d.dispose();
        return image;
    }

    private static byte[] convertImageToMapBytes(BufferedImage image) {
        BufferedImage resized = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(image, 0, 0, 128, 128, null);
        g2d.dispose();

        byte[] mapBytes = new byte[128 * 128];
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int rgb = resized.getRGB(x, y);
                Color c = new Color(rgb);
                mapBytes[y * 128 + x] = getClosestMapColor(c);
            }
        }
        return mapBytes;
    }

    private static byte getClosestMapColor(Color color) {
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();

        int closestId = 0;
        int minDistance = Integer.MAX_VALUE;

        for (int[] entry : GRAYSCALE_MAP_PALETTE) {
            int gray = entry[1];
            int dr = red - gray;
            int dg = green - gray;
            int db = blue - gray;

            // Odległość RGB od rzeczywistego koloru palety
            int distance = dr * dr + dg * dg + db * db;

            if (distance < minDistance) {
                minDistance = distance;
                closestId = entry[0];
            }
        }

        return (byte) closestId;
    }
}