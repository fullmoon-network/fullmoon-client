package dev.fullmoon.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientPacksTest {
    @Test
    void onlyTheSelectedGuiArchiveIsReported() {
        assertTrue(ClientPacks.selected(List.of("vanilla", "file/fullmoon-gui.zip")));
        assertFalse(ClientPacks.selected(List.of("vanilla")));
        assertFalse(ClientPacks.selected(List.of("file/another.zip")));
    }

    @Test
    void theReportedSha1IsTheArchiveOnDisk(@TempDir Path directory) throws IOException {
        Path archive = directory.resolve("fullmoon-gui.zip");
        Files.writeString(archive, "fullmoon-gui", StandardCharsets.UTF_8);

        assertEquals("cb87e049b17f552adea4c53eea928d34288fd526", ClientPacks.sha1(archive));
    }

    @Test
    void theHashIsRecomputedWhenTheArchiveChanges(@TempDir Path directory) throws IOException {
        Path archive = directory.resolve("fullmoon-gui.zip");
        Files.writeString(archive, "first", StandardCharsets.UTF_8);
        String first = ClientPacks.sha1(archive);

        Files.writeString(archive, "second archive with another length", StandardCharsets.UTF_8);
        String second = ClientPacks.sha1(archive);

        assertFalse(first.equals(second));
    }
}
