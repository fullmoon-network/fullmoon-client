package dev.fullmoon.client.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

final class BridgeProtocolTest {
    @Test
    void helloCarriesTheCurrentProtocolAndValidatedVersion() {
        byte[] encoded = BridgeProtocol.hello("3.0.0");
        BridgeProtocol.DecodeResult decoded = BridgeProtocol.decode(encoded);

        BridgeProtocol.Hello hello = assertInstanceOf(
            BridgeProtocol.Hello.class, decoded.message().orElseThrow());
        assertEquals(BridgeProtocol.VERSION, hello.proto());
        assertEquals("fullmoon", hello.client());
        assertEquals("3.0.0", hello.version());
    }

    @Test
    void helloRejectsBlankOrOversizedVersions() {
        assertEquals("client version is required", BridgeProtocol.helloError(" "));
        assertEquals("client version is required", BridgeProtocol.helloError(null));
        assertEquals("client version is too long", BridgeProtocol.helloError("x".repeat(33)));
        assertEquals("client version is required",
            assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.hello(" ")).getMessage());
    }

    @Test
    void decoderAcceptsBareAndMinecraftFramedJson() {
        byte[] bare = json("""
            {"type":"welcome","proto":1}
            """);
        byte[] framed = frame(bare);

        BridgeProtocol.Welcome first = assertInstanceOf(
            BridgeProtocol.Welcome.class, BridgeProtocol.decode(bare).message().orElseThrow());
        BridgeProtocol.Welcome second = assertInstanceOf(
            BridgeProtocol.Welcome.class, BridgeProtocol.decode(framed).message().orElseThrow());
        assertEquals(first, second);
    }

    @Test
    void welcomeCarriesAValidatedImmutableWaypointSnapshot() {
        BridgeProtocol.Welcome welcome = assertInstanceOf(
            BridgeProtocol.Welcome.class, BridgeProtocol.decode(json("""
                {"type":"welcome","proto":1,"waypoints":[
                  {"id":"palace_gate","name":"Palace Gate","icon":"moon",
                   "x":500,"y":72,"z":-140,"world":"world","group":"palace",
                   "perm":"warp.palace"}
                ]}
                """)).message().orElseThrow());

        BridgeProtocol.Waypoint waypoint = welcome.waypoints().getFirst();
        assertEquals("palace_gate", waypoint.id());
        assertEquals("Palace Gate", waypoint.name());
        assertEquals("moon", waypoint.icon());
        assertEquals(500, waypoint.x());
        assertEquals(72, waypoint.y());
        assertEquals(-140, waypoint.z());
        assertEquals("world", waypoint.world());
        assertEquals("palace", waypoint.group());
        assertEquals("warp.palace", waypoint.permission());
        assertThrows(UnsupportedOperationException.class, () -> welcome.waypoints().clear());
    }

    @Test
    void waypointSnapshotsRejectTheWholeInvalidSnapshot() {
        assertEquals("waypoints must be an array", decodeError("""
            {"type":"welcome","proto":1,"waypoints":{}}
            """));
        assertEquals("waypoint 0 id is required", decodeError("""
            {"type":"welcome","proto":1,"waypoints":[
              {"name":"Gate","x":0,"y":64,"z":0,"world":"world"}
            ]}
            """));
        assertEquals("waypoint 0 id is invalid", decodeError("""
            {"type":"welcome","proto":1,"waypoints":[
              {"id":"Bad ID","name":"Gate","x":0,"y":64,"z":0,"world":"world"}
            ]}
            """));
        assertEquals("waypoint 0 name is required", decodeError("""
            {"type":"welcome","proto":1,"waypoints":[
              {"id":"gate","name":" ","x":0,"y":64,"z":0,"world":"world"}
            ]}
            """));
        assertEquals("waypoint 0 coordinates are invalid", decodeError("""
            {"type":"welcome","proto":1,"waypoints":[
              {"id":"gate","name":"Gate","x":0.5,"y":64,"z":0,"world":"world"}
            ]}
            """));
        assertEquals("waypoint 0 world is required", decodeError("""
            {"type":"welcome","proto":1,"waypoints":[
              {"id":"gate","name":"Gate","x":0,"y":64,"z":0}
            ]}
            """));
        assertEquals("waypoint id is duplicated: gate", decodeError("""
            {"type":"welcome","proto":1,"waypoints":[
              {"id":"gate","name":"One","x":0,"y":64,"z":0,"world":"world"},
              {"id":"gate","name":"Two","x":1,"y":64,"z":1,"world":"world"}
            ]}
            """));
    }

    @Test
    void waypointSyncAndServerActionsUseTheV1OperationalContract() {
        BridgeProtocol.WaypointSync sync = assertInstanceOf(
            BridgeProtocol.WaypointSync.class, BridgeProtocol.decode(json("""
                {"type":"waypoint_sync","waypoints":[]}
                """)).message().orElseThrow());
        BridgeProtocol.TpResult accepted = assertInstanceOf(
            BridgeProtocol.TpResult.class, BridgeProtocol.decode(json("""
                {"type":"tp_result","id":"palace_gate","ok":true}
                """)).message().orElseThrow());
        BridgeProtocol.TpResult denied = assertInstanceOf(
            BridgeProtocol.TpResult.class, BridgeProtocol.decode(json("""
                {"type":"tp_result","id":"palace_gate","ok":false,"reason":"cooldown"}
                """)).message().orElseThrow());
        BridgeProtocol.ScreenOpen open = assertInstanceOf(
            BridgeProtocol.ScreenOpen.class, BridgeProtocol.decode(json("""
                {"type":"screen_open","screen":"warp","data":{"source":"command"}}
                """)).message().orElseThrow());

        assertEquals(BridgeProtocol.VERSION, sync.proto());
        assertTrue(sync.waypoints().isEmpty());
        assertTrue(accepted.ok());
        assertEquals("", accepted.reason());
        assertFalse(denied.ok());
        assertEquals("cooldown", denied.reason());
        assertEquals("warp", open.screen());
    }

    @Test
    void serverActionsRejectMissingOrMalformedAuthorityFields() {
        assertEquals("tp_result id is required", decodeError("""
            {"type":"tp_result","ok":true}
            """));
        assertEquals("tp_result ok must be a boolean", decodeError("""
            {"type":"tp_result","id":"gate","ok":"yes"}
            """));
        assertEquals("tp_result reason is required when denied", decodeError("""
            {"type":"tp_result","id":"gate","ok":false}
            """));
        assertEquals("screen_open screen is required", decodeError("""
            {"type":"screen_open"}
            """));
    }

    @Test
    void teleportRequestContainsOnlyTheValidatedServerOwnedId() {
        String request = new String(BridgeProtocol.teleportRequest("palace_gate"),
            StandardCharsets.UTF_8);

        assertEquals("{\"type\":\"tp_request\",\"id\":\"palace_gate\"}", request);
        assertEquals("waypoint id is required", BridgeProtocol.waypointIdError(" "));
        assertEquals("waypoint id is invalid", BridgeProtocol.waypointIdError("../../spawn"));
        assertEquals("waypoint id is required", assertThrows(IllegalArgumentException.class,
            () -> BridgeProtocol.teleportRequest(null)).getMessage());
    }

    @Test
    void menuOpenCarriesAnImmutableServerOwnedSurface() {
        MenuProtocol.Open open = assertInstanceOf(
            MenuProtocol.Open.class, BridgeProtocol.decode(json("""
                {"type":"menu_open","proto":1,"id":"menu-123","revision":7,
                 "title":"Casino","rows":6,"items":[
                   {"slot":22,"label":"Spin","material":"minecraft:clock","count":1,
                    "details":["Bet 100 won","Win rate 48%"],
                    "actions":["left","shift_left"],
                    "icon":"fullmoon.casino.slots"}
                 ]}
                """)).message().orElseThrow());

        assertEquals("menu-123", open.id());
        assertEquals(7, open.revision());
        assertEquals("Casino", open.title());
        assertEquals(6, open.rows());
        MenuProtocol.Item item = open.items().getFirst();
        assertEquals(22, item.slot());
        assertEquals("Spin", item.label());
        assertEquals("minecraft:clock", item.material());
        assertEquals(List.of("Bet 100 won", "Win rate 48%"), item.details());
        assertEquals(List.of(MenuProtocol.Click.LEFT, MenuProtocol.Click.SHIFT_LEFT),
            item.actions());
        assertEquals("fullmoon.casino.slots", item.icon());
        assertThrows(UnsupportedOperationException.class, () -> open.items().clear());
        assertThrows(UnsupportedOperationException.class, () -> item.details().clear());
        assertThrows(UnsupportedOperationException.class, () -> item.actions().clear());
    }

    @Test
    void menuOpenRejectsMalformedGeometryAndInteractionData() {
        assertEquals("menu_open id is required", decodeError("""
            {"type":"menu_open","proto":1,"revision":1,"title":"Shop","rows":3,"items":[]}
            """));
        assertEquals("menu_open id is required", decodeError("""
            {"type":"menu_open","proto":1,"id":1,"revision":1,"title":"Shop","rows":3,"items":[]}
            """));
        assertEquals("menu_open rows must be between 1 and 6", decodeError("""
            {"type":"menu_open","proto":1,"id":"m","revision":1,"title":"Shop","rows":7,"items":[]}
            """));
        assertEquals("menu item 0 slot is outside the menu", decodeError("""
            {"type":"menu_open","proto":1,"id":"m","revision":1,"title":"Shop","rows":1,
             "items":[{"slot":9,"label":"Buy","material":"minecraft:stone","count":1,
             "details":[],"actions":["left"]}]}
            """));
        assertEquals("menu item slot is duplicated: 1", decodeError("""
            {"type":"menu_open","proto":1,"id":"m","revision":1,"title":"Shop","rows":1,
             "items":[
               {"slot":1,"label":"One","material":"minecraft:stone","count":1,"details":[],"actions":[]},
               {"slot":1,"label":"Two","material":"minecraft:dirt","count":1,"details":[],"actions":[]}
             ]}
            """));
        assertEquals("menu item 0 action is invalid", decodeError("""
            {"type":"menu_open","proto":1,"id":"m","revision":1,"title":"Shop","rows":1,
             "items":[{"slot":1,"label":"Buy","material":"minecraft:stone","count":1,
             "details":[],"actions":["middle"]}]}
            """));
        assertEquals("menu_open revision must be non-negative", decodeError("""
            {"type":"menu_open","proto":1,"id":"m","revision":9223372036854775808,
             "title":"Shop","rows":1,"items":[]}
            """));
        assertEquals("menu_open revision must be non-negative", decodeError("""
            {"type":"menu_open","proto":1,"id":"m","revision":"1",
             "title":"Shop","rows":1,"items":[]}
            """));
        assertEquals("menu item 0 slot is outside the menu", decodeError("""
            {"type":"menu_open","proto":1,"id":"m","revision":1,"title":"Shop","rows":1,
             "items":[{"slot":1.5,"label":"Buy","material":"minecraft:stone","count":1,
             "details":[],"actions":["left"]}]}
            """));
        assertEquals("menu item 0 icon is invalid", decodeError("""
            {"type":"menu_open","proto":1,"id":"m","revision":1,"title":"Shop","rows":1,
             "items":[{"slot":1,"label":"Buy","material":"minecraft:stone","count":1,
             "details":[],"actions":["left"],"icon":"Not An Icon!"}]}
            """));
    }

    @Test
    void menuItemsWithoutAnIconDefaultToTheItemRender() {
        MenuProtocol.Open open = assertInstanceOf(
            MenuProtocol.Open.class, BridgeProtocol.decode(json("""
                {"type":"menu_open","proto":1,"id":"m","revision":1,"title":"Shop","rows":1,
                 "items":[{"slot":1,"label":"Buy","material":"minecraft:stone","count":1,
                 "details":[],"actions":["left"]}]}
                """)).message().orElseThrow());

        assertEquals("", open.items().getFirst().icon());
    }

    @Test
    void menuActionsContainOnlyTheOpaqueSessionAndSelectedSlot() {
        String action = new String(
            MenuProtocol.action("menu-123", 7, 22, MenuProtocol.Click.SHIFT_LEFT),
            StandardCharsets.UTF_8);
        String close = new String(MenuProtocol.close("menu-123", 7), StandardCharsets.UTF_8);

        assertEquals(
            "{\"type\":\"menu_action\",\"id\":\"menu-123\",\"revision\":7,\"slot\":22,\"click\":\"shift_left\"}",
            action);
        assertEquals("{\"type\":\"menu_close\",\"id\":\"menu-123\",\"revision\":7}", close);
        assertThrows(IllegalArgumentException.class,
            () -> MenuProtocol.action(" ", 7, 22, MenuProtocol.Click.LEFT));
        assertThrows(IllegalArgumentException.class,
            () -> MenuProtocol.action("menu-123", -1, 22, MenuProtocol.Click.LEFT));
        assertThrows(IllegalArgumentException.class,
            () -> MenuProtocol.action("menu-123", 7, 54, MenuProtocol.Click.LEFT));
    }

    @Test
    void menuCloseIsAValidatedServerInstruction() {
        MenuProtocol.Close close = assertInstanceOf(
            MenuProtocol.Close.class, BridgeProtocol.decode(json("""
                {"type":"menu_close","proto":1,"id":"menu-123","revision":8}
                """)).message().orElseThrow());

        assertEquals("menu-123", close.id());
        assertEquals(8, close.revision());
        assertEquals("menu_close revision must be non-negative", decodeError("""
            {"type":"menu_close","proto":1,"id":"menu-123","revision":-1}
            """));
    }

    @Test
    void decoderRejectsMalformedAndOversizedPayloads() {
        BridgeProtocol.DecodeResult absent = BridgeProtocol.decode(null);
        BridgeProtocol.DecodeResult malformed = BridgeProtocol.decode(json("not-json"));
        BridgeProtocol.DecodeResult oversized = BridgeProtocol.decode(
            new byte[BridgeProtocol.MAX_PAYLOAD_BYTES + 1]);

        assertEquals("payload is required", absent.error().orElseThrow());
        assertEquals("payload is not a JSON object", malformed.error().orElseThrow());
        assertEquals("payload exceeds 32767 bytes", oversized.error().orElseThrow());
        assertTrue(malformed.message().isEmpty());
        assertTrue(oversized.message().isEmpty());
    }

    @Test
    void decoderRequiresAnObjectType() {
        assertEquals("payload is not a JSON object", decodeError("[]"));
        assertEquals("payload type is required", decodeError("{}"));
        assertEquals("payload type is required", decodeError("{\"type\":null}"));
    }

    @Test
    void handshakeMessagesValidateEveryRequiredField() {
        assertEquals("hello proto must be a non-negative integer", decodeError("""
            {"type":"hello","client":"fullmoon","version":"3.0.0"}
            """));
        assertEquals("hello proto must be a non-negative integer", decodeError("""
            {"type":"hello","proto":-1,"client":"fullmoon","version":"3.0.0"}
            """));
        assertEquals("hello client is required", decodeError("""
            {"type":"hello","proto":1,"version":"3.0.0"}
            """));
        assertEquals("client version is too long", decodeError(String.format("""
            {"type":"hello","proto":1,"client":"fullmoon","version":"%s"}
            """, "x".repeat(33))));
        assertEquals("welcome proto must be a non-negative integer",
            decodeError("{\"type\":\"welcome\"}"));
        assertEquals("welcome proto must be a non-negative integer",
            decodeError("{\"type\":\"welcome\",\"proto\":-1}"));
    }

    @Test
    void decoderRejectsAnInvalidFrameInsteadOfReadingPastIt() {
        byte[] body = json("{\"type\":\"welcome\",\"proto\":1}");
        byte[] framed = frame(body);
        framed[0] = (byte) (framed[0] + 1);

        BridgeProtocol.DecodeResult result = BridgeProtocol.decode(framed);

        assertTrue(result.message().isEmpty());
        assertEquals("payload is not a JSON object", result.error().orElseThrow());
    }

    @Test
    void hudSyncRequiresFiniteBoundedMetricsAndRevision() {
        BridgeProtocol.HudSync sync = assertInstanceOf(
            BridgeProtocol.HudSync.class,
            BridgeProtocol.decode(json("""
                {"type":"hud_sync","proto":1,"revision":7,"tps":19.8,"tick_ms":12.4}
                """)).message().orElseThrow());

        assertEquals(7, sync.revision());
        assertEquals(19.8, sync.ticksPerSecond());
        assertEquals(12.4, sync.tickMilliseconds());

        assertEquals("hud_sync tps must be between 0 and 20",
            BridgeProtocol.decode(json("""
                {"type":"hud_sync","proto":1,"revision":7,"tps":20.1,"tick_ms":12.4}
                """)).error().orElseThrow());
        assertEquals("hud_sync tick_ms must be between 0 and 1000",
            BridgeProtocol.decode(json("""
                {"type":"hud_sync","proto":1,"revision":7,"tps":19.8,"tick_ms":-1}
                """)).error().orElseThrow());
        assertEquals("hud_sync revision must be non-negative",
            BridgeProtocol.decode(json("""
                {"type":"hud_sync","proto":1,"revision":-1,"tps":19.8,"tick_ms":12.4}
                """)).error().orElseThrow());
        assertEquals("hud_sync proto must be a non-negative integer", decodeError("""
            {"type":"hud_sync","revision":7,"tps":19.8,"tick_ms":12.4}
            """));
        assertEquals("hud_sync revision must be non-negative", decodeError("""
            {"type":"hud_sync","proto":1,"revision":1.5,"tps":19.8,"tick_ms":12.4}
            """));
        assertEquals("hud_sync tps must be between 0 and 20", decodeError("""
            {"type":"hud_sync","proto":1,"revision":7,"tps":"NaN","tick_ms":12.4}
            """));
        assertEquals("hud_sync tick_ms must be between 0 and 1000", decodeError("""
            {"type":"hud_sync","proto":1,"revision":7,"tps":19.8,"tick_ms":"bad"}
            """));
    }

    @Test
    void noticeValidatesIdentityCopySeverityAndDuration() {
        BridgeProtocol.Notice notice = assertInstanceOf(
            BridgeProtocol.Notice.class,
            BridgeProtocol.decode(json("""
                {"type":"notice","proto":1,"id":"restart","title":"Server restart",
                 "body":"Lobby restarts in five minutes.","severity":"warning","duration_ms":6000}
                """)).message().orElseThrow());

        assertEquals("restart", notice.id());
        assertEquals("Server restart", notice.title());
        assertEquals("Lobby restarts in five minutes.", notice.body());
        assertEquals(BridgeProtocol.Severity.WARNING, notice.severity());
        assertEquals(6000, notice.durationMillis());

        assertEquals("notice title is required", decodeError("""
            {"type":"notice","proto":1,"id":"restart","title":" ","body":"Body",
             "severity":"info","duration_ms":5000}
            """));
        assertEquals("notice severity is invalid", decodeError("""
            {"type":"notice","proto":1,"id":"restart","title":"Title","body":"Body",
             "severity":"purple","duration_ms":5000}
            """));
        assertEquals("notice duration_ms must be between 1000 and 10000", decodeError("""
            {"type":"notice","proto":1,"id":"restart","title":"Title","body":"Body",
             "severity":"info","duration_ms":30000}
            """));
        assertEquals("notice proto must be a non-negative integer", decodeError("""
            {"type":"notice","id":"restart","title":"Title","body":"Body",
             "severity":"info","duration_ms":5000}
            """));
        assertEquals("notice id is required", decodeError("""
            {"type":"notice","proto":1,"title":"Title","body":"Body",
             "severity":"info","duration_ms":5000}
            """));
        assertEquals("notice id is too long", decodeError(String.format("""
            {"type":"notice","proto":1,"id":"%s","title":"Title","body":"Body",
             "severity":"info","duration_ms":5000}
            """, "x".repeat(65))));
        assertEquals("notice title is too long", decodeError(String.format("""
            {"type":"notice","proto":1,"id":"id","title":"%s","body":"Body",
             "severity":"info","duration_ms":5000}
            """, "x".repeat(65))));
        assertEquals("notice body is required", decodeError("""
            {"type":"notice","proto":1,"id":"id","title":"Title",
             "severity":"info","duration_ms":5000}
            """));
        assertEquals("notice body is too long", decodeError(String.format("""
            {"type":"notice","proto":1,"id":"id","title":"Title","body":"%s",
             "severity":"info","duration_ms":5000}
            """, "x".repeat(161))));
        assertEquals("notice duration_ms must be between 1000 and 10000", decodeError("""
            {"type":"notice","proto":1,"id":"id","title":"Title","body":"Body",
             "severity":"success","duration_ms":2147483648}
            """));
    }

    @Test
    void unknownTypesRemainHarmlessAndObservable() {
        BridgeProtocol.Unknown unknown = assertInstanceOf(
            BridgeProtocol.Unknown.class,
            BridgeProtocol.decode(json("{\"type\":\"future_surface\",\"proto\":1}"))
                .message().orElseThrow());

        assertEquals("future_surface", unknown.type());
        assertEquals(1, unknown.proto());

        BridgeProtocol.Unknown unversioned = assertInstanceOf(
            BridgeProtocol.Unknown.class,
            BridgeProtocol.decode(json("{\"type\":\"future_surface\"}"))
                .message().orElseThrow());
        assertEquals(0, unversioned.proto());
    }

    @Test
    void framingUsesTheFullMinecraftVarintRange() {
        byte[] body = new byte[300];
        byte[] framed = frame(body);

        assertArrayEquals(new byte[] { (byte) 0xAC, 0x02 }, new byte[] { framed[0], framed[1] });
        assertEquals(302, framed.length);
        assertFalse(framed[0] == 44);
    }

    @Test
    void anUnterminatedVarintIsRejected() {
        byte[] invalid = {
            (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80
        };

        assertEquals("payload is not a JSON object",
            BridgeProtocol.decode(invalid).error().orElseThrow());
    }

    @Test
    void decodeResultCannotContainBothOrNeitherOutcome() {
        assertThrows(IllegalArgumentException.class, () -> new BridgeProtocol.DecodeResult(
            Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new BridgeProtocol.DecodeResult(
            Optional.of(new BridgeProtocol.Welcome(1)), Optional.of("error")));
        assertThrows(NullPointerException.class, () -> new BridgeProtocol.DecodeResult(
            null, Optional.of("error")));
        assertThrows(NullPointerException.class, () -> new BridgeProtocol.DecodeResult(
            Optional.empty(), null));
    }

    private static String decodeError(String text) {
        return BridgeProtocol.decode(json(text)).error().orElseThrow();
    }

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] frame(byte[] body) {
        byte[] prefix = new byte[5];
        int value = body.length;
        int size = 0;
        while ((value & ~0x7F) != 0) {
            prefix[size++] = (byte) ((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        prefix[size++] = (byte) value;

        byte[] framed = new byte[size + body.length];
        System.arraycopy(prefix, 0, framed, 0, size);
        System.arraycopy(body, 0, framed, size, body.length);
        return framed;
    }
}
