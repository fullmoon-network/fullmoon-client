package dev.fullmoon.client.warp;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import dev.fullmoon.client.network.BridgeProtocol;

public final class WarpRoutes {
    /** Groups in order; inside a group the server's own order stands (the sort is stable). */
    private static final Comparator<BridgeProtocol.Waypoint> ORDER =
        Comparator.comparing(BridgeProtocol.Waypoint::group, String.CASE_INSENSITIVE_ORDER);

    /** The refusals the protocol names. Everything else is the server's own and reads as such. */
    private static final List<String> REASONS = List.of("cooldown", "permission", "world",
        "unknown", "unloaded", "timeout", "client_send");

    /** The sixteen points of the compass, clockwise from north. */
    private static final String[] POINTS = {
        "북", "북북동", "북동", "동북동", "동", "동남동", "남동", "남남동",
        "남", "남남서", "남서", "서남서", "서", "서북서", "북서", "북북서",
    };

    /** Within this many blocks a destination is where the player already is. */
    static final int HERE_METERS = 6;

    private WarpRoutes() {}

    public static List<BridgeProtocol.Waypoint> ordered(
            List<BridgeProtocol.Waypoint> waypoints) {
        Objects.requireNonNull(waypoints, "waypoints");
        return waypoints.stream().sorted(ORDER).toList();
    }

    /**
     * The {@code fullmoon.warp.reason.*} key a server reason maps to.
     *
     * <p>Here rather than on a screen because two surfaces now request the same warp, and a
     * vocabulary copied into both is a vocabulary that drifts in one of them. Anything the server
     * sends that this does not know is the server's own refusal and says so.
     */
    public static String reasonKey(String reason) {
        return reason != null && REASONS.contains(reason) ? reason : "server";
    }

    public static int distanceMeters(
            BridgeProtocol.Waypoint waypoint, double x, double y, double z) {
        Objects.requireNonNull(waypoint, "waypoint");
        double dx = x - (waypoint.x() + 0.5);
        double dy = y - waypoint.y();
        double dz = z - (waypoint.z() + 0.5);
        return (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The compass bearing from ({@code x}, {@code z}) to the waypoint: degrees clockwise from north, 0 to 360. */
    public static double bearing(BridgeProtocol.Waypoint waypoint, double x, double z) {
        double dx = waypoint.x() + 0.5 - x;
        double dz = waypoint.z() + 0.5 - z;
        return norm(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    /** The compass heading a game yaw faces: yaw 0 is south, 180 north, and it turns clockwise. */
    public static double heading(float yaw) {
        return norm(yaw + 180.0);
    }

    /** The turn from {@code heading} to {@code bearing}, −180 to 180; positive is to the right. */
    public static double turn(double heading, double bearing) {
        double delta = norm(bearing - heading);
        return delta > 180.0 ? delta - 360.0 : delta;
    }

    /** The nearest of the sixteen compass points to a bearing. */
    public static String compassPoint(double bearing) {
        return POINTS[(int) Math.round(norm(bearing) / 22.5) % POINTS.length];
    }

    /** {@code 오른쪽 20°}, {@code 왼쪽 20°}, {@code 정면} within a degree, {@code 뒤} within a degree of about-turn. */
    public static String turnLabel(double turn) {
        long degrees = Math.round(Math.abs(turn));
        if (degrees == 0) {
            return "정면";
        }
        if (degrees >= 180) {
            return "뒤";
        }
        return (turn > 0 ? "오른쪽 " : "왼쪽 ") + degrees + "°";
    }

    private static double norm(double degrees) {
        double d = degrees % 360.0;
        return d < 0 ? d + 360.0 : d;
    }
}
