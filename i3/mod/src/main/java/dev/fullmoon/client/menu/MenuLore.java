package dev.fullmoon.client.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.fullmoon.client.network.MenuProtocol;

/**
 * The server's lore conventions, read back into meaning.
 *
 * <p>coin-bridge writes every chest menu's tooltip through one builder, and that builder gives
 * each kind of line a leading mark: {@code » 클릭: …} is what a click does, {@code » Shift+클릭: …}
 * the second click, {@code ✖ …} why a click would be refused, {@code ✔ …} a state the slot is
 * already in, {@code 채팅에 입력: …} a command to type, {@code 이름 · 값} a fact, and a run of
 * {@code ■□} a bar. Plain text carries no colour, so the marks are the only thing that tells the
 * lines apart, and a line that starts with none of them is prose. Nothing here changes what is
 * sent or what a click means; it only decides how a line is drawn.
 */
public final class MenuLore {
    static final String CLICK = "» 클릭:";
    static final String SHIFT_CLICK = "» Shift+클릭:";
    static final String BLOCKED = "✖";
    static final String DONE = "✔";
    static final String TYPED = "채팅에 입력:";
    static final String SEPARATOR = " · ";

    private static final Pattern BAR = Pattern.compile("^[■□]{3,}$");
    private static final Pattern FIGURE = Pattern.compile("^(약 )?[0-9][0-9,.]*([천만억]( [0-9][0-9,.]*[천만억]?)?)?(원|%|x|배|개|장|초|분|명|HP)?$");
    private static final int FACT_KEY_MAX = 14;
    private static final int FACT_VALUE_MAX = 28;

    /** One click the server advertises for this item, and what it says it does. */
    public record Action(MenuProtocol.Click click, String text) {}

    /** A {@code key · value} line: a figure a player reads at a glance. */
    public record Fact(String key, String value) {}

    /** A {@code ■■■□□} line: how full something is. */
    public record Bar(int filled, int total) {
        public float fraction() {
            return total == 0 ? 0.0f : (float) filled / total;
        }
    }

    /** Everything an item's lore said, sorted by what kind of line said it, in the order sent. */
    public record Parsed(
            List<String> prose,
            List<Fact> facts,
            List<String> figures,
            List<Bar> bars,
            List<Action> actions,
            List<String> blocked,
            List<String> done,
            List<String> typed) {
        public Parsed {
            prose = List.copyOf(prose);
            facts = List.copyOf(facts);
            figures = List.copyOf(figures);
            bars = List.copyOf(bars);
            actions = List.copyOf(actions);
            blocked = List.copyOf(blocked);
            done = List.copyOf(done);
            typed = List.copyOf(typed);
        }

        /** Whether the server says a click would be refused right now. */
        public boolean isBlocked() {
            return !blocked.isEmpty();
        }

        /** The one figure a row shows on its right: the first money-like value, or nothing. */
        public String headline() {
            for (String figure : figures) {
                return figure;
            }
            for (Fact fact : facts) {
                if (looksLikeFigure(fact.value())) {
                    return fact.value();
                }
            }
            return "";
        }

        /** A fact by its key, or empty. */
        public String fact(String key) {
            for (Fact fact : facts) {
                if (fact.key().equals(key)) {
                    return fact.value();
                }
            }
            return "";
        }
    }

    private MenuLore() {}

    public static Parsed parse(List<String> details) {
        List<String> prose = new ArrayList<>();
        List<Fact> facts = new ArrayList<>();
        List<String> figures = new ArrayList<>();
        List<Bar> bars = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        List<String> done = new ArrayList<>();
        List<String> typed = new ArrayList<>();
        for (String raw : details) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(SHIFT_CLICK)) {
                actions.add(new Action(MenuProtocol.Click.SHIFT_LEFT, after(line, SHIFT_CLICK)));
            } else if (line.startsWith(CLICK)) {
                actions.add(new Action(MenuProtocol.Click.LEFT, after(line, CLICK)));
            } else if (line.startsWith("»")) {
                actions.add(new Action(MenuProtocol.Click.LEFT, after(line, "»")));
            } else if (line.startsWith(BLOCKED)) {
                blocked.add(after(line, BLOCKED));
            } else if (line.startsWith(DONE)) {
                done.add(after(line, DONE));
            } else if (line.startsWith(TYPED)) {
                typed.add(after(line, TYPED));
            } else if (BAR.matcher(line).matches()) {
                bars.add(new Bar(count(line, '■'), line.length()));
            } else if (isFact(line)) {
                int at = line.indexOf(SEPARATOR);
                facts.add(new Fact(line.substring(0, at).strip(), line.substring(at + SEPARATOR.length()).strip()));
            } else if (looksLikeFigure(line)) {
                figures.add(line);
            } else {
                prose.add(line);
            }
        }
        return new Parsed(prose, facts, figures, bars, actions, blocked, done, typed);
    }

    /**
     * A typed command split from what follows it: {@code /지갑 으로 오늘 번 돈을 볼 수 있어요}
     * is the chip {@code /지갑} and the rest as body. A command runs to the first space that is
     * not inside an angle-bracket placeholder.
     */
    public static String[] command(String typed) {
        String text = typed.strip();
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth = Math.max(0, depth - 1);
            } else if (c == ' ' && depth == 0 && i > 0 && looksLikeCommandEnd(text, i)) {
                return new String[] {text.substring(0, i), text.substring(i + 1).strip()};
            }
        }
        return new String[] {text, ""};
    }

    /** Whether {@code line} is {@code key · value} with a short key and a short value. */
    static boolean isFact(String line) {
        int at = line.indexOf(SEPARATOR);
        if (at <= 0 || at != line.lastIndexOf(SEPARATOR)) {
            return false;
        }
        String key = line.substring(0, at).strip();
        String value = line.substring(at + SEPARATOR.length()).strip();
        return !key.isEmpty() && !value.isEmpty()
            && key.length() <= FACT_KEY_MAX && value.length() <= FACT_VALUE_MAX
            && !key.endsWith(".") && !value.endsWith(".");
    }

    /** Whether a value is a number a player reads: {@code 8천원}, {@code 52.4%}, {@code 1.98x}, {@code 64개}. */
    static boolean looksLikeFigure(String value) {
        return FIGURE.matcher(value.strip()).matches();
    }

    private static boolean looksLikeCommandEnd(String text, int space) {
        // A command's arguments are placeholders or short words; a sentence after it starts with
        // a particle glued to the command (으로, 을) or a verb, which is why the split is at the
        // first space after any run of bracketed arguments.
        String rest = text.substring(space + 1);
        return !rest.startsWith("<");
    }

    private static String after(String line, String mark) {
        return line.substring(mark.length()).strip();
    }

    private static int count(String line, char c) {
        int n = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
