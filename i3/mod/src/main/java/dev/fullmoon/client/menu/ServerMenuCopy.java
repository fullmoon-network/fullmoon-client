package dev.fullmoon.client.menu;

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

    private static boolean decorative(char value) {
        return Character.isWhitespace(value) || DECORATION.indexOf(value) >= 0;
    }
}
