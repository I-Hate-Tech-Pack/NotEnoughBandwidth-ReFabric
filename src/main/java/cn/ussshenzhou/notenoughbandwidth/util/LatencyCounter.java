package cn.ussshenzhou.notenoughbandwidth.util;

import net.minecraft.util.Util;

import java.util.ArrayList;

/**
 * Sliding-window averager for latency samples.
 * Records nanoTime values and returns the windowed average in microseconds or milliseconds.
 */
public class LatencyCounter {
    private final ArrayList<long[]> samples = new ArrayList<>();
    private final int windowSizeMs;

    public LatencyCounter(int windowSizeMs) {
        this.windowSizeMs = windowSizeMs;
    }

    public LatencyCounter() {
        this(2000);
    }

    public synchronized void record(long nanos) {
        prune();
        samples.add(new long[]{Util.getMeasuringTimeMs(), nanos});
    }

    public synchronized double averageMicros() {
        prune();
        if (samples.isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (var s : samples) {
            sum += s[1];
        }
        return sum / (double) samples.size() / 1000.0;
    }

    public synchronized double averageMs() {
        return averageMicros() / 1000.0;
    }

    private void prune() {
        long now = Util.getMeasuringTimeMs();
        samples.removeIf(s -> now - s[0] > windowSizeMs);
    }
}
