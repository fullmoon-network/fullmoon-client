package dev.fullmoon.client.season;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import dev.fullmoon.client.network.BridgeProtocol;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.SeasonProtocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.minecraft.client.gui.screens.Screen;

/**
 * A fifty-level pass as a server would send it, for photographing the screen and the widget
 * without one. Three standings: a Plus holder at level 27 in the middle of the season, a free
 * player at level 12, and a finished Plus holder in the grace after the season.
 *
 * <p>The rewards are season 1's own, with the three cosmetics in the cells they replace, and every
 * amount is written out as the server would word it. The snapshot goes through the same decoder a
 * live one does, so a fixture that drifts outside the contract fails the way a server would. The
 * capture rig is the only thing that asks for it.
 */
public final class SeasonSample {
    public static final String PROPERTY = "fullmoon.devScreen";
    /** The widget's sample replays on this period: a finished stage for two seconds, then the recent line for three. */
    private static final long HUD_LOOP = 6_000;
    private static final long FLASH_SHOWN = 2_000;

    private static final int[] FREE = {
        25, 25, 30, 30, 50, 30, 30, 35, 35, 0, 40, 40, 40, 45, 0, 45, 50, 50, 50, 0, 55, 55, 60, 60, 100,
        60, 65, 65, 70, 0, 20, 20, 20, 20, 50, 20, 20, 20, 20, 0, 20, 20, 20, 20, 50, 20, 20, 20, 20, 0};
    private static final int[] PLUS = {
        0, 25, 25, 25, 50, 25, 30, 30, 30, 0, 30, 30, 30, 35, 60, 35, 35, 35, 35, 70, 40, 40, 40, 40, 0,
        40, 40, 45, 45, 100, 15, 15, 15, 15, 40, 15, 15, 15, 15, 0, 15, 15, 15, 15, 40, 15, 15, 15, 15, 0};
    /** How the server words each amount: the client never scales or formats money itself. */
    private static final Map<Integer, String> WORDED = Map.ofEntries(
        Map.entry(15, "3천원"), Map.entry(20, "4천원"), Map.entry(25, "5천원"), Map.entry(30, "6천원"),
        Map.entry(35, "7천원"), Map.entry(40, "8천원"), Map.entry(45, "9천원"), Map.entry(50, "1만원"),
        Map.entry(55, "1만 1천원"), Map.entry(60, "1만 2천원"), Map.entry(65, "1만 3천원"),
        Map.entry(70, "1만 4천원"), Map.entry(100, "2만원"));
    private static final Map<String, String[]> SPECIAL = Map.ofEntries(
        Map.entry("free10", new String[] {"title", "초승달", "yellow"}),
        Map.entry("free15", new String[] {"cosmetic", "달빛 곡괭이", "moonlight_pickaxe", "minecraft:iron_pickaxe"}),
        Map.entry("free20", new String[] {"title", "밤마실", "aqua"}),
        Map.entry("free30", new String[] {"title", "달마중", "green"}),
        Map.entry("free40", new String[] {"title", "달무리", "white"}),
        Map.entry("free50", new String[] {"title", "초승달 완주", "gold"}),
        Map.entry("plus1", new String[] {"title", "초승달 동행", "light_purple"}),
        Map.entry("plus10", new String[] {"cosmetic", "초승달 검", "crescent_sword", "minecraft:iron_sword"}),
        Map.entry("plus25", new String[] {"cosmetic", "월하 갑옷", "moonlit_armor", "minecraft:iron_chestplate"}),
        Map.entry("plus40", new String[] {"title", "달그림자", "blue"}),
        Map.entry("plus50", new String[] {"title", "그믐밤", "dark_purple"}));

    private SeasonSample() {}

    private enum Standing { PLUS, FREE, CLAIM }

    private static Optional<Standing> standing() {
        return switch (System.getProperty(PROPERTY, "")) {
            case "season-plus" -> Optional.of(Standing.PLUS);
            case "season-free" -> Optional.of(Standing.FREE);
            case "season-claim" -> Optional.of(Standing.CLAIM);
            default -> Optional.empty();
        };
    }

    /** The pass the {@value #PROPERTY} system property names, if it names one. */
    public static Optional<SeasonProtocol.Open> requested() {
        return standing().flatMap(standing -> BridgeProtocol.decode(open(standing).toString()
                .getBytes(StandardCharsets.UTF_8)).message())
            .filter(SeasonProtocol.Open.class::isInstance)
            .map(SeasonProtocol.Open.class::cast);
    }

    public static Screen screen(Screen parent, SeasonProtocol.Open pass) {
        return new SeasonScreen(parent, pass, true);
    }

    /** The widget's feed while a season fixture is up, decoded like a live {@code season_progress}. */
    public static Optional<BridgeState.SeasonFeed> feed(long now) {
        if (standing().isEmpty()) {
            return Optional.empty();
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "season_progress");
        json.addProperty("level", 27);
        json.addProperty("into", 95);
        json.addProperty("step", 147);
        json.add("tracked", goal("daily_coal", "석탄 캐기", "minecraft:coal", 13, 32, "개"));
        json.add("recent", goal("daily_iron", "철광석 캐기", "minecraft:raw_iron", 6, 16, "개"));
        JsonObject flash = new JsonObject();
        flash.addProperty("mission", "season_monster_hunter");
        flash.addProperty("label", "몬스터 사냥꾼 II 완료");
        json.add("flash", flash);
        return BridgeProtocol.decode(json.toString().getBytes(StandardCharsets.UTF_8)).message()
            .filter(SeasonProtocol.Progress.class::isInstance)
            .map(SeasonProtocol.Progress.class::cast)
            .map(p -> {
                long start = now - now % HUD_LOOP;
                long recentAt = now - start < FLASH_SHOWN ? start - BridgeState.SEASON_RECENT_MILLIS : start + FLASH_SHOWN;
                return new BridgeState.SeasonFeed(p.level(), p.into(), p.step(), p.tracked(), p.recent(), recentAt,
                    p.flash(), start);
            });
    }

    private static JsonObject goal(String id, String name, String material, long progress, long goal, String unit) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("material", material);
        json.addProperty("progress", progress);
        json.addProperty("goal", goal);
        json.addProperty("unit", unit);
        json.addProperty("done", progress >= goal);
        return json;
    }

    private static JsonObject open(Standing standing) {
        int level = switch (standing) {
            case PLUS -> 27;
            case FREE -> 12;
            case CLAIM -> 50;
        };
        boolean plus = standing != Standing.FREE;
        JsonObject json = new JsonObject();
        json.addProperty("type", "season_open");
        json.addProperty("id", "sample-" + standing.name().toLowerCase(java.util.Locale.ROOT));
        json.addProperty("revision", 3);
        json.addProperty("tab", "track");

        JsonObject season = new JsonObject();
        season.addProperty("id", "s1");
        season.addProperty("name", "시즌 1 · 초승달");
        season.addProperty("phase", standing == Standing.CLAIM ? "claim" : "live");
        season.addProperty("endsIn", standing == Standing.CLAIM ? "2일 3시간 남음" : "17일 남음");
        season.addProperty("endsAt", standing == Standing.CLAIM ? "2027-01-04T06:00:00+09:00" : "2026-12-28T06:00:00+09:00");
        json.add("season", season);
        JsonObject terms = new JsonObject();
        terms.addProperty("point", "XP");
        json.add("terms", terms);

        long at = at(level);
        long step = level >= 50 ? at(50) - at(49) : at(level + 1) - at;
        long into = switch (standing) {
            case PLUS -> 95;
            case FREE -> 30;
            case CLAIM -> step;
        };
        JsonObject me = new JsonObject();
        me.addProperty("level", level);
        me.addProperty("maxLevel", 50);
        me.addProperty("xp", standing == Standing.CLAIM ? at : at + into);
        me.addProperty("into", into);
        me.addProperty("step", step);
        me.addProperty("plus", plus);
        JsonArray levels = new JsonArray();
        int claimable = 0;
        for (int n = 1; n <= 50; n++) {
            JsonObject column = new JsonObject();
            column.addProperty("level", n);
            column.addProperty("at", at(n));
            SeasonProtocol.Status free = freeStatus(standing, n, level);
            SeasonProtocol.Status extra = plusStatus(standing, n, level);
            claimable += (free == SeasonProtocol.Status.CLAIMABLE ? 1 : 0) + (extra == SeasonProtocol.Status.CLAIMABLE ? 1 : 0);
            column.add("free", reward("free", n, FREE[n - 1], free));
            column.add("plus", reward("plus", n, PLUS[n - 1], extra));
            levels.add(column);
        }
        me.addProperty("claimable", claimable);
        me.addProperty("balance", switch (standing) {
            case PLUS -> "약 7만 8천원";
            case FREE -> "약 52만원";
            case CLAIM -> "약 31만원";
        });
        json.add("me", me);
        json.add("levels", levels);
        json.add("missions", missions(standing));
        json.add("plus", plusOffer(standing));
        json.add("notice", switch (standing) {
            case PLUS -> notice("ok", "보상을 받았어요 · 1만 2천원  24레벨 무료");
            case FREE -> JsonNull.INSTANCE;
            case CLAIM -> notice("error", "오늘 받을 수 있는 보상 한도를 다 채웠어요. 한도가 풀리면 다시 받을 수 있어요.");
        });
        return json;
    }

    /** The season total that opens level {@code n}: the vanilla experience curve at {@code n - 1}, times 1.6. */
    static long at(int n) {
        int vanilla = n - 1;
        double total;
        if (vanilla <= 16) {
            total = vanilla * vanilla + 6.0 * vanilla;
        } else if (vanilla <= 31) {
            total = 2.5 * vanilla * vanilla - 40.5 * vanilla + 360;
        } else {
            total = 4.5 * vanilla * vanilla - 162.5 * vanilla + 2220;
        }
        return Math.round(total * 1.6);
    }

    private static SeasonProtocol.Status freeStatus(Standing standing, int n, int level) {
        if (n > level) {
            return SeasonProtocol.Status.LOCKED;
        }
        int claimedThrough = switch (standing) {
            case PLUS -> 24;
            case FREE -> 9;
            case CLAIM -> 47;
        };
        if ((standing == Standing.PLUS && n == 25) || (standing == Standing.CLAIM && n == 48)) {
            return SeasonProtocol.Status.PENDING;
        }
        return n <= claimedThrough ? SeasonProtocol.Status.CLAIMED : SeasonProtocol.Status.CLAIMABLE;
    }

    private static SeasonProtocol.Status plusStatus(Standing standing, int n, int level) {
        if (standing == Standing.FREE) {
            return n <= level ? SeasonProtocol.Status.PLUS_LOCKED : SeasonProtocol.Status.LOCKED;
        }
        if (n > level) {
            return SeasonProtocol.Status.LOCKED;
        }
        int claimedThrough = standing == Standing.PLUS ? 26 : 48;
        return n <= claimedThrough ? SeasonProtocol.Status.CLAIMED : SeasonProtocol.Status.CLAIMABLE;
    }

    private static JsonObject reward(String track, int n, int amount, SeasonProtocol.Status status) {
        JsonObject json = new JsonObject();
        String[] special = SPECIAL.get(track + n);
        if (special == null) {
            json.addProperty("kind", "won");
            json.addProperty("label", WORDED.get(amount));
            json.addProperty("material", amount >= 100 ? "minecraft:gold_block"
                : amount >= 50 ? "minecraft:gold_ingot" : "minecraft:gold_nugget");
        } else if (special[0].equals("title")) {
            json.addProperty("kind", "title");
            json.addProperty("label", special[1]);
            json.addProperty("color", special[2]);
            json.addProperty("material", "minecraft:name_tag");
        } else {
            json.addProperty("kind", "cosmetic");
            json.addProperty("label", special[1]);
            json.addProperty("id", special[2]);
            json.addProperty("model", "fullmoon.skin." + special[2] + ".1");
            json.addProperty("material", special[3]);
        }
        json.addProperty("state", SeasonProtocol.wire(status));
        return json;
    }

    private static JsonObject missions(Standing standing) {
        JsonObject json = new JsonObject();
        if (standing == Standing.CLAIM) {
            json.add("daily", new JsonArray());
            json.add("weekly", new JsonArray());
            json.add("season", new JsonArray());
            json.add("resets", JsonNull.INSTANCE);
            return json;
        }
        boolean plus = standing == Standing.PLUS;
        int scale = plus ? 1 : 0;
        JsonArray daily = new JsonArray();
        daily.add(mission("daily_coal", "석탄 캐기", "minecraft:coal", 12, 32, "개", 30, 0, 1, true, false, false));
        daily.add(mission("daily_iron", "철광석 캐기", "minecraft:raw_iron", plus ? 16 : 5, 16, "개", 40, 0, 1, false, false, false));
        daily.add(mission("daily_redstone", "레드스톤 캐기", "minecraft:redstone", 4 * scale, 10, "개", 40, 0, 1, false, false, false));
        daily.add(mission("daily_wheat", "밀 거두기", "minecraft:wheat", 20 * scale, 32, "개", 30, 0, 1, false, true, false));
        JsonArray weekly = new JsonArray();
        weekly.add(mission("weekly_diamonds", "다이아몬드 캐기", "minecraft:diamond", 3, 5, "개", 150, 0, 1, false, false, false));
        weekly.add(mission("weekly_monster_sweep", "몬스터 소탕", "minecraft:diamond_sword", plus ? 88 : 31, 150, "마리", 150, 0, 1,
            false, false, false));
        weekly.add(mission("weekly_voice", "음성 채널에서 함께하기", "minecraft:note_block", plus ? 140 : 40, 180, "분", 120, 0, 1,
            false, false, true));
        JsonArray season = new JsonArray();
        season.add(mission("season_monster_hunter", "몬스터 사냥꾼 II", "minecraft:diamond_sword", plus ? 212 : 64,
            plus ? 400 : 100, "마리", plus ? 80 : 40, plus ? 1 : 0, 3, false, false, false));
        season.add(mission("season_miner", "광부 III", "minecraft:diamond_pickaxe", plus ? 530 : 180, plus ? 1000 : 400, "개",
            plus ? 160 : 80, plus ? 2 : 1, 3, false, false, false));
        season.add(mission("season_harvest", "풍년 I", "minecraft:hay_block", 120, 200, "개", 40, 0, 3, false, false, false));
        season.add(mission("season_angler", "월척 III", "minecraft:tropical_fish", plus ? 200 : 20, plus ? 200 : 80, "마리",
            plus ? 160 : 80, plus ? 3 : 1, 3, false, false, false));
        season.add(mission("season_rancher", "목장 주인 I", "minecraft:saddle", 4, 10, "번", 40, 0, 3, false, false, false));
        season.add(mission("season_haggler", "흥정꾼 II", "minecraft:bell", 25, 80, "번", 80, 1, 3, false, false, false));
        season.add(mission("season_adventurer", "모험가 II", "minecraft:filled_map", 7, 15, "개", 80, 1, 3, false, false, false));
        json.add("daily", daily);
        json.add("weekly", weekly);
        json.add("season", season);
        JsonObject resets = new JsonObject();
        resets.addProperty("daily", "3시간 12분");
        resets.addProperty("weekly", "2일 4시간");
        json.add("resets", resets);
        return json;
    }

    private static JsonObject mission(String id, String name, String material, long progress, long goal, String unit,
            int xp, int tier, int tiers, boolean tracked, boolean plusExtra, boolean discord) {
        boolean done = tier >= tiers || progress >= goal && tiers == 1;
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("material", material);
        json.addProperty("progress", progress);
        json.addProperty("goal", goal);
        json.addProperty("unit", unit);
        json.addProperty("xp", xp);
        json.addProperty("tier", tier);
        json.addProperty("tiers", tiers);
        json.addProperty("done", done);
        json.addProperty("plusExtra", plusExtra);
        json.addProperty("tracked", tracked);
        json.addProperty("trackable", !done && !discord && (!plusExtra || progress > 0));
        json.addProperty("discord", discord);
        return json;
    }

    private static JsonObject plusOffer(Standing standing) {
        JsonObject json = new JsonObject();
        json.addProperty("owned", standing != Standing.FREE);
        json.addProperty("price", "40만원");
        json.addProperty("canBuy", standing == Standing.FREE);
        json.addProperty("confirming", false);
        JsonArray perks = new JsonArray();
        perks.add("레벨마다 플러스 보상이 하나씩 더 있어요");
        perks.add("이미 지난 레벨의 보상도 바로 받을 수 있어요");
        perks.add("매일 미션이 1개 더 열려요");
        perks.add("플러스로만 얻는 이번 시즌 칭호 셋");
        perks.add("디스코드 계정을 연동했다면 플러스 역할이 붙어요");
        if (standing == Standing.FREE) {
            perks.add("지금 사면 바로 받을 보상 · 12개");
        }
        json.add("perks", perks);
        json.add("reason", JsonNull.INSTANCE);
        return json;
    }

    private static JsonObject notice(String tone, String text) {
        JsonObject json = new JsonObject();
        json.addProperty("tone", tone);
        json.addProperty("text", text);
        return json;
    }
}
