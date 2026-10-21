
package pl.dawcou.astralogin.system.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionComparator {

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^[vV]?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(.+))?$"
    );

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private VersionComparator() {
    }

    public static int compare(String first, String second) {
        Version a = parse(first);
        Version b = parse(second);

        int result = Integer.compare(a.major, b.major);
        if (result != 0) return result;

        result = Integer.compare(a.minor, b.minor);
        if (result != 0) return result;

        result = Integer.compare(a.patch, b.patch);
        if (result != 0) return result;

        if (a.suffix == null && b.suffix == null) return 0;
        if (a.suffix == null) return 1;
        if (b.suffix == null) return -1;

        return compareSuffix(a.suffix, b.suffix);
    }

    public static int compareCore(String first, String second) {
        Version a = parse(first);
        Version b = parse(second);

        int result = Integer.compare(a.major, b.major);
        if (result != 0) return result;

        result = Integer.compare(a.minor, b.minor);
        if (result != 0) return result;

        return Integer.compare(a.patch, b.patch);
    }

    public static int extractBuildNumber(String version) {
        int separator = version.indexOf('-');
        if (separator < 0) return 0;

        String suffix = version.substring(separator + 1);
        Matcher matcher = NUMBER_PATTERN.matcher(suffix);

        int number = 0;

        while (matcher.find()) {
            try {
                number = Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
                return Integer.MAX_VALUE;
            }
        }

        return number;
    }

    private static int compareSuffix(String first, String second) {
        String a = first.toLowerCase();
        String b = second.toLowerCase();

        int result = Integer.compare(getRank(a), getRank(b));
        if (result != 0) return result;

        result = Integer.compare(
                extractBuildNumber("0.0.0-" + a),
                extractBuildNumber("0.0.0-" + b)
        );
        if (result != 0) return result;

        return a.compareTo(b);
    }

    private static int getRank(String suffix) {
        if (suffix.startsWith("beta")) return 0;
        if (suffix.startsWith("pre")) return 1;
        if (suffix.startsWith("rc")) return 2;

        return 2;
    }

    private static Version parse(String input) {
        Matcher matcher = VERSION_PATTERN.matcher(input.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Unsupported version format: " + input
            );
        }

        try {
            return new Version(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    matcher.group(3) == null
                            ? 0
                            : Integer.parseInt(matcher.group(3)),
                    matcher.group(4)
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Version number is out of range: " + input,
                    e
            );
        }
    }

    private record Version(int major, int minor, int patch, String suffix) { }
}