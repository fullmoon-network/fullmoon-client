package dev.fullmoon.client.network;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The same archive is shipped intact and expanded into the mod's unconditional assets. */
final class BundledGuiPack {
    private BundledGuiPack() {}

    static String sha1() throws IOException {
        try (InputStream archive = BundledGuiPack.class.getResourceAsStream("/fullmoon/gui-pack.zip")) {
            if (archive == null) {
                throw new IOException("The bundled Fullmoon GUI pack is missing");
            }
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(archive.readAllBytes()));
            } catch (NoSuchAlgorithmException error) {
                throw new IOException("SHA-1 is unavailable for the bundled GUI pack", error);
            }
        }
    }
}
