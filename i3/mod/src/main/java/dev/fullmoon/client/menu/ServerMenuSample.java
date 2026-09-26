package dev.fullmoon.client.menu;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.fullmoon.client.network.BridgeProtocol;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.CasinoProtocol;
import dev.fullmoon.client.network.MenuProtocol;

/**
 * A casino menu as the server would send it, for photographing the screen without a server.
 *
 * <p>It goes through the same decoder as a live snapshot, so a fixture that drifts outside the
 * contract fails the way a server would, instead of drawing something no server could send. The
 * figures are examples, and the capture rig is the only thing that asks for it.
 */
public final class ServerMenuSample {
    public static final String PROPERTY = "fullmoon.devScreen";

    private static final String CASINO = """
        {"type":"menu_open","proto":1,"id":"casino","revision":1,"title":"카지노","rows":6,"items":[
          {"slot":10,"label":"동전","material":"minecraft:gold_nugget","count":1,"actions":["left"],
           "icon":"fullmoon.casino.coinflip","chance":0.5,
           "details":["던져서 앞이나 뒤나. 반반에 가장 가까운 승부예요","배당 1.98배 · 한 판 최대 10,000원"]},
          {"slot":11,"label":"주사위","material":"minecraft:bone","count":1,"actions":["left"],
           "icon":"fullmoon.casino.dice","chance":0.5,
           "details":["목표가 낮을수록 배당이 커요","목표 50 · 배당 1.96배"]},
          {"slot":12,"label":"룰렛","material":"minecraft:compass","count":1,"actions":["left"],
           "icon":"fullmoon.casino.roulette","chance":0.4864864864864865,
           "details":["색·홀짝·구간에 걸어요","빨강 · 배당 2배"]},
          {"slot":13,"label":"슬롯","material":"minecraft:clock","count":1,"actions":["left"],
           "icon":"fullmoon.casino.slots","chance":0.07,
           "details":["세 릴이 맞으면 크게 받아요","최대 50배"]},
          {"slot":14,"label":"파친코","material":"minecraft:ender_pearl","count":1,"actions":["left"],
           "icon":"fullmoon.casino.moonfall",
           "details":["달빛 방울이 핀을 타고 떨어져요","일곱 칸 · 최대 20.5배"]},
          {"slot":15,"label":"잭팟","material":"minecraft:nether_star","count":1,"actions":["left","shift_left"],
           "icon":"fullmoon.casino.jackpot","chance":0.004,
           "details":["티켓을 사고 추첨을 기다려요","누적 3,200,000원"]},
          {"slot":30,"label":"오늘의 나","material":"minecraft:paper","count":1,"actions":[],"details":["+2,400원"]},
          {"slot":31,"label":"하우스 몫","material":"minecraft:paper","count":1,"actions":[],
           "details":["길게 하면 하우스가 이겨요. 그 차이가 이만큼이에요.","동전: 1.0%"]},
          {"slot":33,"label":"아직 결과가 없어요","material":"minecraft:paper","count":1,"actions":[],
           "details":["던지기를 누르면 여기 나와요"]},
          {"slot":32,"label":"내 잔액","material":"minecraft:paper","count":1,"actions":[],"details":["128,450원"]},
          {"slot":49,"label":"닫기","material":"minecraft:barrier","count":1,"actions":["left"],"details":[]}
        ]}
        """;

    /** One settled bet per game, the payloads BRIDGE.md documents. */
    private static final Map<String, String> RESULTS = Map.of(
        "slots", "{\"type\":\"casino_result\",\"game\":\"slots\",\"won\":true,\"payout_multiplier\":12,"
            + "\"detail\":{\"reels\":[\"moon\",\"moon\",\"moon\"],\"matched\":3}}",
        "dice", "{\"type\":\"casino_result\",\"game\":\"dice\",\"won\":false,\"payout_multiplier\":0,"
            + "\"detail\":{\"roll\":73,\"target\":50}}",
        "roulette", "{\"type\":\"casino_result\",\"game\":\"roulette\",\"won\":true,\"payout_multiplier\":2,"
            + "\"detail\":{\"pocket\":17,\"bet_type\":\"black\"}}",
        "coinflip", "{\"type\":\"casino_result\",\"game\":\"coinflip\",\"won\":true,\"payout_multiplier\":1.98}");
    /** The fixture card replays on this period, so a capture at any moment finds it mid-life. */
    private static final long RESULT_LOOP = 6_000;

    private ServerMenuSample() {}

    private static final List<String> GAMES = List.of("slots", "dice", "roulette", "coinflip");

    /**
     * A replaying casino result for {@code casino-result:<game>}, decoded like a live one;
     * {@code casino-result:all} steps through the four games, one per period.
     */
    public static Optional<BridgeState.CasinoReveal> casinoReveal(long now) {
        String name = System.getProperty(PROPERTY, "");
        if (!name.startsWith("casino-result:")) {
            return Optional.empty();
        }
        String game = name.substring("casino-result:".length());
        if (game.equals("all")) {
            game = GAMES.get((int) (now / RESULT_LOOP % GAMES.size()));
        }
        String json = RESULTS.get(game);
        if (json == null) {
            return Optional.empty();
        }
        return BridgeProtocol.decode(json.getBytes(StandardCharsets.UTF_8)).message()
            .filter(CasinoProtocol.Result.class::isInstance)
            .map(result -> new BridgeState.CasinoReveal((CasinoProtocol.Result) result, now - now % RESULT_LOOP));
    }

    /** The lobby's campus routes as FullmoonBridge sends them (flagship waypoints, pass5). */
    private static final String CAMPUS = """
        {"type":"welcome","proto":1,"waypoints":[
          {"id":"plaza","name":"달빛 광장","icon":"moon","x":0,"y":73,"z":80,"world":"world","group":"광장"},
          {"id":"portico","name":"로톤다 정문","icon":"moon","x":0,"y":77,"z":10,"world":"world","group":"궁궐"},
          {"id":"casino","name":"달빛 카지노","icon":"moon","x":39,"y":77,"z":-31,"world":"world","group":"궁궐"},
          {"id":"archive","name":"기록보관동","icon":"moon","x":-92,"y":73,"z":29,"world":"world","group":"궁궐"},
          {"id":"moon_gate","name":"달빛 문","icon":"moon","x":0,"y":74,"z":-112,"world":"world","group":"문"},
          {"id":"water_garden","name":"물의 정원","icon":"moon","x":75,"y":73,"z":50,"world":"world","group":"정원"},
          {"id":"exedra","name":"남쪽 원형극장","icon":"moon","x":4,"y":73,"z":95,"world":"world","group":"정원"}
        ]}
        """;

    /** The routes the {@code warp} fixture shows, decoded like a live welcome. */
    public static List<BridgeProtocol.Waypoint> waypoints() {
        if (!System.getProperty(PROPERTY, "").equals("warp")) {
            return List.of();
        }
        return BridgeProtocol.decode(CAMPUS.getBytes(StandardCharsets.UTF_8)).message()
            .filter(BridgeProtocol.Welcome.class::isInstance)
            .map(welcome -> ((BridgeProtocol.Welcome) welcome).waypoints())
            .orElse(List.of());
    }

    /** The fixture the {@value #PROPERTY} system property names, if it names one. */
    public static Optional<MenuProtocol.Open> requested() {
        String name = System.getProperty(PROPERTY, "");
        if (!name.equals("casino-menu")) {
            return Optional.empty();
        }
        return BridgeProtocol.decode(CASINO.getBytes(StandardCharsets.UTF_8)).message()
            .filter(MenuProtocol.Open.class::isInstance)
            .map(MenuProtocol.Open.class::cast);
    }
}
