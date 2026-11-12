package dev.fullmoon.client.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BundledGuiPackTest {
    @Test
    void builtInArtIsAdvertisedWithoutAnyExternalPack(@TempDir Path directory) throws Exception {
        assertEquals(List.of(new BridgeProtocol.Pack("fullmoon-gui",
            "175de05a740e46b47a2db6533c3d217c1c14cb19")), ClientPacks.active(List.of("vanilla"), directory));
    }

    @Test
    void aSelectedExternalArchiveIsReportedHonestly(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("fullmoon-gui.zip"), "fullmoon-gui", StandardCharsets.UTF_8);
        assertEquals(List.of(new BridgeProtocol.Pack("fullmoon-gui",
            "cb87e049b17f552adea4c53eea928d34288fd526")),
            ClientPacks.active(List.of("vanilla", "file/fullmoon-gui.zip"), directory));
    }

    @Test
    void theAcceptedBetaPackIsIncludedInTheMod() throws Exception {
        try (InputStream archive = getClass().getResourceAsStream("/fullmoon/gui-pack.zip")) {
            assertNotNull(archive);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(archive.readAllBytes()));
            assertEquals("175de05a740e46b47a2db6533c3d217c1c14cb19", digest);
        }
    }

    @Test
    void everyPackAssetIsLoadedAsAnUnconditionalModResource() throws Exception {
        int assets = 0;
        try (ZipInputStream archive = new ZipInputStream(
                getClass().getResourceAsStream("/fullmoon/gui-pack.zip"))) {
            for (ZipEntry entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                if (!entry.getName().startsWith("assets/") || entry.isDirectory()) {
                    continue;
                }
                try (InputStream resource = getClass().getResourceAsStream("/" + entry.getName())) {
                    assertNotNull(resource, entry.getName());
                    assertArrayEquals(archive.readAllBytes(), resource.readAllBytes(), entry.getName());
                    assets++;
                }
            }
        }
        assertTrue(assets > 0);
    }
}
