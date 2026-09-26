package dev.fullmoon.client.menu;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import dev.fullmoon.client.network.BridgeProtocol;
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
           "details":["앞일까 뒤일까, 반반 승부","배당 1.98배 · 한 판 최대 10,000원"]},
          {"slot":11,"label":"주사위","material":"minecraft:bone","count":1,"actions":["left"],
           "icon":"fullmoon.casino.dice","chance":0.5,
           "details":["목표가 낮을수록 배당이 커요","목표 50 · 배당 1.96배"]},
          {"slot":12,"label":"룰렛","material":"minecraft:compass","count":1,"actions":["left"],
           "icon":"fullmoon.casino.roulette","chance":0.4864864864864865,
           "details":["색·홀짝·구간에 걸어요","빨강 · 배당 2배"]},
          {"slot":13,"label":"슬롯","material":"minecraft:clock","count":1,"actions":["left"],
           "icon":"fullmoon.casino.slots","chance":0.07,
           "details":["세 릴이 맞으면 크게 받아요","최대 50배"]},
          {"slot":14,"label":"달빛 낙하","material":"minecraft:ender_pearl","count":1,"actions":["left"],
           "icon":"fullmoon.casino.moonfall",
           "details":["깊이 떨어질수록 배당이 커요","최대 20배"]},
          {"slot":15,"label":"잭팟","material":"minecraft:nether_star","count":1,"actions":["left","shift_left"],
           "icon":"fullmoon.casino.jackpot","chance":0.004,
           "details":["티켓을 사고 추첨을 기다려요","누적 3,200,000원"]},
          {"slot":30,"label":"오늘의 나","material":"minecraft:paper","count":1,"actions":[],"details":["+2,400원"]},
          {"slot":31,"label":"하우스 몫","material":"minecraft:paper","count":1,"actions":[],"details":["1.0%"]},
          {"slot":32,"label":"내 잔액","material":"minecraft:paper","count":1,"actions":[],"details":["128,450원"]},
          {"slot":49,"label":"닫기","material":"minecraft:barrier","count":1,"actions":["left"],"details":[]}
        ]}
        """;

    private ServerMenuSample() {}

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
