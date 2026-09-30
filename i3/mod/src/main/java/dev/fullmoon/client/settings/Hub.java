package dev.fullmoon.client.settings;

import dev.fullmoon.client.account.AccountScreen;
import dev.fullmoon.client.keybinds.KeybindsScreen;
import dev.fullmoon.client.mods.ModsScreen;
import dev.fullmoon.client.ui.HubScreen;

import net.minecraft.client.gui.screens.Screen;

/** The one place that knows which screen each page of 풀문 설정 is. */
public final class Hub {
    private Hub() {}

    /** The page for {@code tab}, shown in place of the page on tab {@code from}. */
    public static Screen open(HubScreen.Tab tab, Screen parent, int from) {
        return switch (tab) {
            case SETTINGS -> new SettingsScreen(parent, false, from);
            case KEYBINDS -> new KeybindsScreen(parent, false, from);
            case MODS -> new ModsScreen(parent, false, from);
            case ACCOUNT -> new AccountScreen(parent, false, from);
        };
    }
}
