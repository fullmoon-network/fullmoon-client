package dev.fullmoon.client.ui;

import java.util.ArrayList;
import java.util.List;

import dev.fullmoon.client.FullmoonClient;
import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * What every development surface in this client has in common: the scrim over the world, the
 * masthead, the rail of pages under it, the footer, and the one path every pointer and key takes
 * into a {@link Surface}.
 *
 * <p>The pages are one document rather than three screens that happen to share tokens, which is
 * the whole reason this class exists. It also means the plumbing is written once: a screen that
 * forgot to forward {@code mouseScrolled} would be a list that cannot scroll, and that bug is
 * only possible three times if the forwarding is copied three times.
 *
 * <p>A subclass supplies its own layout and its own drawing. The chrome is closed to it — every
 * hook the game calls is final here — so a page cannot quietly grow a second masthead.
 */
public abstract class DevScreen extends SurfaceScreen {
    /** The development pages, in rail order. */
    public enum Page {
        SPECIMEN("표본", "디자인 표본"),
        KIT("위젯", "위젯 키트"),
        LIST("목록", "목록과 툴팁");

        private final String tab;
        private final String title;

        Page(String tab, String title) {
            this.tab = tab;
            this.title = title;
        }

        /** The word on the rail. Short, because a rail of sentences is a rail nobody reads. */
        public String tab() {
            return tab;
        }

        public String title() {
            return title;
        }
    }

    protected static final int MAX_CONTENT = 520;

    private final Page page;
    private final TabRail rail;

    /** The column the chrome and the page are both measured in. */
    private Box content = Box.EMPTY;

    /** What is left of {@link #content} once the chrome has taken its share at both ends. */
    private Box body = Box.EMPTY;

    /** Whether the keyboard should arrive on the rail with the ring up. See {@link #go}. */
    private boolean ringed;

    protected DevScreen(Page page) {
        super(Typeset.say(Tokens.Type.ROW, page.title()));
        this.page = page;
        // First on the surface, so Tab reaches the rail before the page under it — and so a step
        // from nowhere lands there, which is what carries the ring across a page change.
        this.rail = surface.add(new TabRail("페이지", tabs(), page.ordinal(), this::go));
        rail.hint("← → 로 옮기고 Enter 로 연다");
    }

    public static DevScreen open(Page page) {
        return switch (page) {
            case SPECIMEN -> new SpecimenScreen();
            case KIT -> new KitScreen();
            case LIST -> new ListScreen();
        };
    }

    public final Page page() {
        return page;
    }

    /** The world keeps running behind the blur: a dev surface is not a pause menu. */
    @Override
    public final boolean isPauseScreen() {
        return false;
    }

    /**
     * How wide the page's own column may get. The chrome's is {@link #MAX_CONTENT} whatever a page
     * says, because a masthead that moves when the tab changes reads as the screen having reloaded:
     * a narrower page gives up its right edge and keeps the left one the whole surface is aligned to.
     */
    protected int maxContent() {
        return MAX_CONTENT;
    }

    /** Where the page's own controls go. Called with the box the chrome has left. */
    protected abstract void lay(Box body);

    /** The page's own drawing. Its surface widgets are drawn after this, by the chrome. */
    protected abstract void paint(Painter painter, Box body);

    /** The right-hand end of the footer rule, which is the page's to fill. */
    protected abstract String status();

    @Override
    protected final void init() {
        int frame = Math.min(MAX_CONTENT, width - Tokens.Space.SECTION * 2);
        int left = (width - frame) / 2;
        content = Box.between(left, Tokens.Space.SECTION, left + frame, DevChrome.footerY(height));
        rail.place(new Box(content.x(), railY(content.y()), content.w(), TabRail.HEIGHT));
        body = Box.between(content.x(), content.y() + chromeHeight(),
            content.x() + Math.min(maxContent(), content.w()), content.bottom());
        lay(body);
        if (ringed) {
            surface.focus().advance(1);
        }
    }

    @Override
    public final void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        Painter painter = new Painter(gfx);
        painter.blurredStratum();
        painter.fill(0, 0, painter.width(), painter.height(),
            Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.82f));
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        // Read the hover every frame rather than only on a move event: the pointer can sit still
        // while the layout under it changes, and then a stale hover is a lie about the surface.
        surface.hover(mouseX, mouseY);

        Painter painter = new Painter(gfx);
        DevChrome.header(painter, content.x(), content.y(), content.w(),
            "클라이언트 i3 · " + page.title());
        paint(painter, body);

        surface.draw(painter);

        DevChrome.footer(painter, content.x(), DevChrome.footerY(height), content.w(), keys(),
            status());

        Widget tipped = surface.tipped();
        if (tipped != null) {
            Tooltip.draw(painter, tipped.hint(), tipped.bounds(), content);
        }
    }

    /** The masthead, the rail beneath its rule, and the gutter between the rail and the page. */
    static int chromeHeight() {
        return DevChrome.headerHeight() + TabRail.HEIGHT + Tokens.Stroke.HAIR;
    }

    /** Directly under the masthead rule, so the two rules read as one bar with the tabs in it. */
    private static int railY(int contentY) {
        return contentY + DevChrome.headerHeight() - Tokens.Space.GUTTER + Tokens.Stroke.HAIR;
    }

    private static List<String> tabs() {
        List<String> tabs = new ArrayList<>(Page.values().length);
        for (Page page : Page.values()) {
            tabs.add(page.tab());
        }
        return tabs;
    }

    /**
     * A tab was chosen. The next page comes up with the keyboard on its own rail: a player who
     * changed page with the keyboard has not asked to be put back at the top of the surface.
     */
    private void go(int tab) {
        DevScreen next = open(Page.values()[tab]);
        next.ringed = surface.focus().rings(rail);
        Minecraft.getInstance().setScreen(next);
    }

    private String keys() {
        return "Esc 닫기 · Tab 이동 · " + FullmoonClient.pageKey(page) + " 다시 열기";
    }

}
