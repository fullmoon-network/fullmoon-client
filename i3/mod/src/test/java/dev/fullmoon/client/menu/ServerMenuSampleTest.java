package dev.fullmoon.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fullmoon.client.network.MenuProtocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class ServerMenuSampleTest {
    @AfterEach
    void clear() {
        System.clearProperty(ServerMenuSample.PROPERTY);
    }

    @Test
    void nothingOpensUnlessTheRigAsks() {
        assertTrue(ServerMenuSample.requested().isEmpty());
    }

    @Test
    void theCasinoFixturePassesTheSameDecoderAsTheServer() {
        System.setProperty(ServerMenuSample.PROPERTY, "casino-menu");
        MenuProtocol.Open menu = ServerMenuSample.requested().orElseThrow();
        assertEquals("casino", menu.id());
        assertEquals(10, menu.items().size());
        assertEquals(5, menu.items().stream().filter(item -> item.chance().isPresent()).count());
    }
}
