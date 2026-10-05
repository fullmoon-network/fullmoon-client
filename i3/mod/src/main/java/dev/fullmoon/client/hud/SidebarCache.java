package dev.fullmoon.client.hud;

import java.util.Collection;
import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The sidebar as last laid out, and what it was laid out from. {@link #matches} compares a frame's
 * scoreboard to that copy without building anything, so a frame in which the server said nothing
 * costs a walk over the entries and not a re-layout of them.
 *
 * <p>A line's text is its entry plus its team's prefix, suffix and colour, so all of those are
 * kept and compared; the objective, its title and number format, and the GUI scale and font epoch
 * the text was cut to fit are part of the signature too.
 */
final class SidebarCache {
    private Object source;
    private Component title;
    private NumberFormat format;
    private int guiScale;
    private int epoch;
    private PlayerScoreEntry[] entries = new PlayerScoreEntry[0];
    private Component[] prefixes = new Component[0];
    private Component[] suffixes = new Component[0];
    private ChatFormatting[] colors = new ChatFormatting[0];
    private ScoreboardSidebar.Layout layout;

    /** Whether {@link #layout} is still what these arguments would lay out. */
    boolean matches(Object source, Component title, NumberFormat format, Collection<PlayerScoreEntry> scores,
            Scoreboard scoreboard, int guiScale, int epoch) {
        if (layout == null || source != this.source || guiScale != this.guiScale || epoch != this.epoch
                || scores.size() != entries.length || !title.equals(this.title)
                || !Objects.equals(format, this.format)) {
            return false;
        }
        int i = 0;
        for (PlayerScoreEntry entry : scores) {
            if (!entry.equals(entries[i]) || !sameTeam(scoreboard.getPlayersTeam(entry.owner()), i)) {
                return false;
            }
            i++;
        }
        return true;
    }

    void store(Object source, Component title, NumberFormat format, Collection<PlayerScoreEntry> scores,
            Scoreboard scoreboard, int guiScale, int epoch, ScoreboardSidebar.Layout layout) {
        this.source = source;
        this.title = title;
        this.format = format;
        this.guiScale = guiScale;
        this.epoch = epoch;
        entries = scores.toArray(new PlayerScoreEntry[0]);
        prefixes = new Component[entries.length];
        suffixes = new Component[entries.length];
        colors = new ChatFormatting[entries.length];
        for (int i = 0; i < entries.length; i++) {
            PlayerTeam team = scoreboard.getPlayersTeam(entries[i].owner());
            if (team != null) {
                prefixes[i] = team.getPlayerPrefix();
                suffixes[i] = team.getPlayerSuffix();
                colors[i] = team.getColor();
            }
        }
        this.layout = layout;
    }

    ScoreboardSidebar.Layout layout() {
        return layout;
    }

    private boolean sameTeam(PlayerTeam team, int i) {
        if (team == null) {
            return prefixes[i] == null;
        }
        return prefixes[i] != null && team.getPlayerPrefix().equals(prefixes[i])
            && team.getPlayerSuffix().equals(suffixes[i]) && team.getColor() == colors[i];
    }
}
