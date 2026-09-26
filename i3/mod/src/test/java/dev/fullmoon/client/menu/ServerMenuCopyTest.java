package dev.fullmoon.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ServerMenuCopyTest {
    @Test
    void removesChestGuiDecorationFromNativeLabels() {
        assertEquals("동전", ServerMenuCopy.label("» 동전"));
        assertEquals("뒤로", ServerMenuCopy.label("« 뒤로"));
        assertEquals("닫기", ServerMenuCopy.label("✖ 닫기"));
        assertEquals("달빛 낙하", ServerMenuCopy.label("✔ 달빛 낙하"));
    }

    @Test
    void preservesMeaningfulCopyAndTrailingPunctuation() {
        assertEquals("페이지 2 / 5", ServerMenuCopy.label("페이지 2 / 5"));
        assertEquals("다음", ServerMenuCopy.label("다음 »"));
        assertEquals("1만원 · 보유", ServerMenuCopy.label("1만원 · 보유"));
    }
}
