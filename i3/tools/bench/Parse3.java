import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;

/**
 * Reads one JFR window recording and prints one JSON object on stdout.
 *
 *   java Parse3.java window.jfr > window.json
 *
 * Per-frame source: jdk.MethodTrace (JDK 25) on Minecraft.runTick and GameRenderer.render, one event
 * per invocation. `runTick` start-to-start is the frame interval; `render` is the time spent in
 * GameRenderer.render. Everything else a window needs comes from the same recording: render-thread
 * allocation (jdk.ThreadAllocationStatistics), garbage collections, heap after each collection,
 * CPU load, and a coarse attribution of the render thread's execution samples by package.
 */
public class Parse3 {
    static String cat(List<RecordedFrame> fr) {
        String mod = null; boolean mc = false;
        for (RecordedFrame f : fr) {
            String c = f.getMethod().getType().getName();
            if (c.startsWith("dev.fullmoon.")) return "fullmoon";
            if (mod == null) {
                if (c.startsWith("net.caffeinemc.mods.sodium")) mod = "sodium";
                else if (c.startsWith("net.caffeinemc.mods.lithium") || c.startsWith("me.jellysquid.mods.lithium")) mod = "lithium";
                else if (c.startsWith("net.raphimc.immediatelyfast")) mod = "immediatelyfast";
                else if (c.startsWith("dev.tr7zw.entityculling")) mod = "entityculling";
                else if (c.startsWith("ca.fxco.moreculling")) mod = "moreculling";
                else if (c.startsWith("me.bluecrow") || c.startsWith("dev.badoptimizations") || c.contains("badoptimizations")) mod = "badoptimizations";
                else if (c.startsWith("dev.isxander") || c.startsWith("dynamic_fps") || c.contains("dynamicfps")) mod = "dynamicfps";
            }
            if (c.startsWith("net.minecraft.") || c.startsWith("com.mojang.")) mc = true;
        }
        return mod != null ? mod : mc ? "vanilla" : "jdk/lwjgl/native";
    }

    static long nanos(java.time.Duration d) { try { return d.toNanos(); } catch (ArithmeticException e) { return -1; } }  // an unset minimum is Long.MAX_VALUE

    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    static String arr(List<Long> v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.size(); i++) { if (i > 0) sb.append(','); sb.append(v.get(i)); }
        return sb.append(']').toString();
    }

    public static void main(String[] a) throws Exception {
        List<long[]> ticks = new ArrayList<>();           // {startNs, durationNs}
        List<long[]> renders = new ArrayList<>();
        Map<String, long[]> timing = new TreeMap<>();     // method -> {invocations, avgNs, minNs, maxNs}
        Map<String, Integer> sampleCat = new TreeMap<>();
        Map<String, long[]> traces = new TreeMap<>();     // any other traced method -> {n, totalNs, maxNs, firstStartMs, lastEndMs}
        Map<String, Integer> allCat = new TreeMap<>(); int allSamples = 0;
        Map<String, long[]> alloc = new HashMap<>();      // thread -> {firstBytes, firstTs, lastBytes, lastTs}
        int gcCount = 0; double gcSum = 0, gcMax = 0; Map<String, Integer> gcNames = new TreeMap<>();
        List<Long> heapAfterGc = new ArrayList<>(); List<Long> heapCommitted = new ArrayList<>();
        double jvmUser = 0, jvmSys = 0, machine = 0; int cpuN = 0;
        double rtUser = 0, rtSys = 0; int rtN = 0;
        int samples = 0; long firstTs = Long.MAX_VALUE, lastTs = 0;
        long jvmStartMs = -1;
        try (RecordingFile rf = new RecordingFile(Path.of(a[0]))) {
            while (rf.hasMoreEvents()) {
                RecordedEvent e = rf.readEvent();
                String t = e.getEventType().getName();
                switch (t) {
                    case "jdk.MethodTrace" -> {
                        RecordedMethod m = e.getValue("method");
                        String name = m.getType().getName() + "::" + m.getName();
                        long start = e.getStartTime().getEpochSecond() * 1_000_000_000L + e.getStartTime().getNano();
                        long dur = e.getDuration().toNanos();
                        if (name.endsWith("Minecraft::runTick")) ticks.add(new long[]{start, dur});
                        else if (name.endsWith("GameRenderer::render")) renders.add(new long[]{start, dur});
                        else {
                            long[] v = traces.computeIfAbsent(name, k -> new long[]{0, 0, 0, Long.MAX_VALUE, 0});
                            v[0]++; v[1] += dur; v[2] = Math.max(v[2], dur);
                            v[3] = Math.min(v[3], start / 1_000_000L); v[4] = Math.max(v[4], (start + dur) / 1_000_000L);
                        }
                        firstTs = Math.min(firstTs, start); lastTs = Math.max(lastTs, start);
                    }
                    case "jdk.MethodTiming" -> {
                        RecordedMethod m = e.getValue("method");
                        timing.put(m.getType().getName() + "::" + m.getName(), new long[]{
                            e.getLong("invocations"), nanos(e.getDuration("average")),
                            nanos(e.getDuration("minimum")), nanos(e.getDuration("maximum"))});
                    }
                    case "jdk.ExecutionSample", "jdk.NativeMethodSample" -> {
                        RecordedThread th = e.getThread("sampledThread");
                        RecordedStackTrace st = e.getStackTrace(); if (th == null || st == null) break;
                        boolean ttf = false;
                        for (RecordedFrame f : st.getFrames()) { String c = f.getMethod().getType().getName(); if (c.contains("TrueTypeGlyphProvider") || c.startsWith("org.lwjgl.util.freetype")) { ttf = true; break; } }
                        allSamples++; allCat.merge(ttf ? "truetype" : cat(st.getFrames()), 1, Integer::sum);
                        if (t.equals("jdk.ExecutionSample") && "Render thread".equals(th.getJavaName())) { samples++; sampleCat.merge(cat(st.getFrames()), 1, Integer::sum); }
                    }
                    case "jdk.ThreadAllocationStatistics" -> {
                        RecordedThread th = e.getThread("thread");
                        if (th == null) break;
                        String tn = th.getJavaName() == null ? th.getOSName() : th.getJavaName();
                        long al = e.getLong("allocated"); long ts = e.getStartTime().toEpochMilli();
                        long[] v = alloc.computeIfAbsent(tn, k -> new long[]{al, ts, al, ts});
                        if (ts < v[1]) { v[0] = al; v[1] = ts; }
                        if (ts >= v[3]) { v[2] = al; v[3] = ts; }
                    }
                    case "jdk.GarbageCollection" -> {
                        gcCount++;
                        gcSum += e.getDuration("sumOfPauses").toNanos() / 1e6;
                        gcMax = Math.max(gcMax, e.getDuration("longestPause").toNanos() / 1e6);
                        gcNames.merge(e.getString("name"), 1, Integer::sum);
                    }
                    case "jdk.GCHeapSummary" -> {
                        if ("After GC".equals(e.getString("when"))) {
                            heapAfterGc.add(e.getLong("heapUsed"));
                            RecordedObject hs = e.getValue("heapSpace");
                            if (hs != null) heapCommitted.add(hs.getLong("committedSize"));
                        }
                    }
                    case "jdk.CPULoad" -> { jvmUser += e.getDouble("jvmUser"); jvmSys += e.getDouble("jvmSystem"); machine += e.getDouble("machineTotal"); cpuN++; }
                    case "jdk.ThreadCPULoad" -> {
                        RecordedThread th = e.getThread("eventThread");
                        if (th != null && "Render thread".equals(th.getJavaName())) { rtUser += e.getDouble("user"); rtSys += e.getDouble("system"); rtN++; }
                    }
                    case "jdk.JVMInformation" -> jvmStartMs = e.getLong("jvmStartTime");
                    default -> {}
                }
            }
        }
        ticks.sort(Comparator.comparingLong(x -> x[0]));
        renders.sort(Comparator.comparingLong(x -> x[0]));
        List<Long> tickStart = new ArrayList<>(), tickDur = new ArrayList<>(), renderDur = new ArrayList<>();
        for (long[] x : ticks) { tickStart.add(x[0]); tickDur.add(x[1]); }
        for (long[] x : renders) renderDur.add(x[1]);
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"tick_start_ns\":").append(arr(tickStart));
        sb.append(",\"tick_dur_ns\":").append(arr(tickDur));
        sb.append(",\"render_dur_ns\":").append(arr(renderDur));
        sb.append(",\"timing\":{");
        boolean first = true;
        for (var en : timing.entrySet()) {
            if (!first) sb.append(','); first = false;
            long[] v = en.getValue();
            sb.append('"').append(esc(en.getKey())).append("\":{\"n\":").append(v[0]).append(",\"avg_ns\":").append(v[1])
              .append(",\"min_ns\":").append(v[2]).append(",\"max_ns\":").append(v[3]).append('}');
        }
        sb.append("},\"render_samples\":{\"total\":").append(samples);
        for (var en : sampleCat.entrySet()) sb.append(",\"").append(esc(en.getKey())).append("\":").append(en.getValue());
        sb.append("},\"all_samples\":{\"total\":").append(allSamples);
        for (var en : allCat.entrySet()) sb.append(",\"").append(esc(en.getKey())).append("\":").append(en.getValue());
        sb.append("},\"traces\":{");
        first = true;
        for (var en : traces.entrySet()) {
            if (!first) sb.append(','); first = false;
            long[] v = en.getValue();
            sb.append('"').append(esc(en.getKey())).append("\":{\"n\":").append(v[0]).append(",\"total_ns\":").append(v[1]).append(",\"max_ns\":").append(v[2])
              .append(",\"first_ms\":").append(v[3]).append(",\"last_end_ms\":").append(v[4]).append('}');
        }
        sb.append("},\"alloc\":{");
        first = true;
        for (var en : alloc.entrySet()) {
            long[] v = en.getValue();
            if (v[3] <= v[1]) continue;
            if (!first) sb.append(','); first = false;
            sb.append('"').append(esc(en.getKey())).append("\":{\"bytes\":").append(v[2] - v[0]).append(",\"ms\":").append(v[3] - v[1]).append('}');
        }
        sb.append("},\"gc\":{\"count\":").append(gcCount).append(",\"sum_pause_ms\":").append(gcSum)
          .append(",\"max_pause_ms\":").append(gcMax).append(",\"names\":{");
        first = true;
        for (var en : gcNames.entrySet()) { if (!first) sb.append(','); first = false; sb.append('"').append(esc(en.getKey())).append("\":").append(en.getValue()); }
        sb.append("},\"heap_after_gc\":").append(arr(heapAfterGc)).append(",\"committed_after_gc\":").append(arr(heapCommitted)).append('}');
        sb.append(",\"cpu\":{\"jvm_user\":").append(cpuN > 0 ? jvmUser / cpuN : -1).append(",\"jvm_system\":").append(cpuN > 0 ? jvmSys / cpuN : -1)
          .append(",\"machine\":").append(cpuN > 0 ? machine / cpuN : -1).append(",\"render_user\":").append(rtN > 0 ? rtUser / rtN : -1)
          .append(",\"render_system\":").append(rtN > 0 ? rtSys / rtN : -1).append('}');
        sb.append(",\"jvm_start_ms\":").append(jvmStartMs).append('}');
        System.out.println(sb);
    }
}
