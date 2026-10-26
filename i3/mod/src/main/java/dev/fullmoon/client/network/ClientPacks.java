package dev.fullmoon.client.network;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

import net.minecraft.client.Minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The resource packs the handshake reports. There is one: the server's GUI pack, which the
 * launcher keeps in the instance's {@code resourcepacks/} and switches on once. It is reported
 * only while the player has it on, and by the hash of the file on disk rather than by the hash
 * the launcher was told to expect, so a server that compares it is comparing what is loaded.
 */
public final class ClientPacks {
    private static final Logger LOG = LoggerFactory.getLogger("Fullmoon/Packs");
    public static final String GUI_PACK = "fullmoon-gui";
    static final String GUI_FILE = GUI_PACK + ".zip";

    /** The last file hashed, so a reconnect does not read it again unless it changed. */
    private static Hashed last;

    private ClientPacks() {}

    private record Hashed(Path file, long size, long modified, String sha1) {}

    public static List<BridgeProtocol.Pack> active() {
        Minecraft client = Minecraft.getInstance();
        if (!selected(client.getResourcePackRepository().getSelectedIds())) {
            return List.of();
        }
        Path file = client.getResourcePackDirectory().resolve(GUI_FILE);
        try {
            return List.of(new BridgeProtocol.Pack(GUI_PACK, sha1(file)));
        } catch (IOException error) {
            LOG.warn("Could not hash {}; the handshake will not report it", file, error);
            return List.of();
        }
    }

    static boolean selected(Collection<String> ids) {
        return ids.contains("file/" + GUI_FILE);
    }

    static synchronized String sha1(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        long modified = attributes.lastModifiedTime().toMillis();
        if (last != null && last.file().equals(file) && last.size() == attributes.size()
                && last.modified() == modified) {
            return last.sha1();
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException missing) {
            throw new IOException("SHA-1 is not available", missing);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
        }
        String sha1 = HexFormat.of().formatHex(digest.digest());
        last = new Hashed(file, attributes.size(), modified, sha1);
        return sha1;
    }
}
