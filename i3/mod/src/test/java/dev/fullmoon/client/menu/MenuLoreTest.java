package dev.fullmoon.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.fullmoon.client.network.MenuProtocol;

import org.junit.jupiter.api.Test;

/** The server's footer conventions, as coin-bridge's own MenuConventionsTest pins them, read back. */
final class MenuLoreTest {
    @Test
    void aClickFooterBecomesAnAction() {
        MenuLore.Parsed lore = MenuLore.parse(List.of("» 클릭: 1개 판매", "» Shift+클릭: 64개 전부 판매"));
        assertEquals(List.of(
            new MenuLore.Action(MenuProtocol.Click.LEFT, "1개 판매"),
            new MenuLore.Action(MenuProtocol.Click.SHIFT_LEFT, "64개 전부 판매")), lore.actions());
        assertTrue(lore.prose().isEmpty());
    }

    @Test
    void aRefusalIsBlockedAndAStateIsDone() {
        MenuLore.Parsed lore = MenuLore.parse(List.of("✖ 판매할 아이템이 없어요", "✔ 착용 중"));
        assertEquals(List.of("판매할 아이템이 없어요"), lore.blocked());
        assertEquals(List.of("착용 중"), lore.done());
        assertTrue(lore.isBlocked());
    }

    @Test
    void aTypedCommandIsSplitFromWhatFollowsIt() {
        MenuLore.Parsed lore = MenuLore.parse(List.of("채팅에 입력: /길드 가입 <이름>"));
        assertEquals(List.of("/길드 가입 <이름>"), lore.typed());
        assertEquals("/길드 가입 <이름>", MenuLore.command("/길드 가입 <이름>"));
        assertEquals("/link", MenuLore.command(" /link "));
    }

    @Test
    void aKeyDotValueLineIsAFactAndABareFigureIsAFigure() {
        MenuLore.Parsed lore = MenuLore.parse(List.of("개당 · 200원", "보유 · 64개", "따면 · 1.98x", "8천원", "52.4%"));
        assertEquals(List.of(new MenuLore.Fact("개당", "200원"), new MenuLore.Fact("보유", "64개"),
            new MenuLore.Fact("따면", "1.98x")), lore.facts());
        assertEquals(List.of("8천원", "52.4%"), lore.figures());
        assertEquals("8천원", lore.headline());
        assertEquals("64개", lore.fact("보유"));
    }

    @Test
    void proseWithMiddleDotsStaysProse() {
        MenuLore.Parsed lore = MenuLore.parse(List.of(
            "빨강·검정·홀짝·구간. 0이 하우스 몫.",
            "던져서 앞 아니면 뒤예요. 반반에 가장 가까운 판이에요.",
            "길게 하면 하우스가 이겨요. 그 차이가 이만큼이에요."));
        assertEquals(3, lore.prose().size());
        assertTrue(lore.facts().isEmpty());
        assertFalse(MenuLore.isFact("빨강·검정·홀짝·구간. 0이 하우스 몫."));
    }

    @Test
    void aBarIsReadAsAFraction() {
        MenuLore.Parsed lore = MenuLore.parse(List.of("■■■■■■□□□□", "남음 · 20만원"));
        assertEquals(List.of(new MenuLore.Bar(6, 10)), lore.bars());
        assertEquals(0.6f, lore.bars().getFirst().fraction(), 0.001f);
        assertEquals("20만원", lore.headline());
    }

    @Test
    void figuresAreWhatAPlayerReadsAsNumbers() {
        for (String yes : List.of("200원", "1천원", "8천원", "20만원", "약 1만 2천원", "2억원", "52.4%", "1.98x",
                "64개", "50장", "3초", "300 HP", "60분마다".substring(0, 3))) {
            assertTrue(MenuLore.looksLikeFigure(yes), yes);
        }
        for (String no : List.of("반반에 가장 가까운 판이에요", "빨간 칸 18개", "동전 던지기", "")) {
            assertFalse(MenuLore.looksLikeFigure(no), no);
        }
    }

    @Test
    void theOrderSentIsTheOrderKept() {
        MenuLore.Parsed lore = MenuLore.parse(List.of(
            "세 릴이 맞으면 크게. 만월 3개면 팟을 가져가요.",
            "만월 팟 · 200만원",
            "최고 배당 · 200x + 팟",
            "» 클릭: 게임 고르기"));
        assertEquals(List.of("세 릴이 맞으면 크게. 만월 3개면 팟을 가져가요."), lore.prose());
        assertEquals("만월 팟", lore.facts().getFirst().key());
        assertEquals("200x + 팟", lore.facts().get(1).value());
        assertEquals("200만원", lore.headline());
    }
}
