package dev.fullmoon.client.menu;

import java.util.Locale;

final class ServerMenuCopy {
    private static final String DECORATION = "»«✔✖■□★┃";

    private ServerMenuCopy() {}

    static String label(String source) {
        String value = source.trim();
        int start = 0;
        int end = value.length();
        while (start < end && decorative(value.charAt(start))) {
            start++;
        }
        while (start < end && decorative(value.charAt(end - 1))) {
            end--;
        }
        String cleaned = value.substring(start, end).trim();
        return cleaned.isEmpty() ? value : cleaned;
    }

    /**
     * A win chance as players read one: whole percent when it is one, a tenth where the tenth
     * matters (48.6% roulette, 0.4% jackpot).
     */
    static String percent(double chance) {
        double value = chance * 100.0;
        double whole = Math.rint(value);
        if (value >= 1.0 && Math.abs(value - whole) < 0.05) {
            return (long) whole + "%";
        }
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static boolean decorative(char value) {
        return Character.isWhitespace(value) || DECORATION.indexOf(value) >= 0;
    }
}
