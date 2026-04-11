package cn.ussshenzhou.notenoughbandwidth.stat;

import static cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.*;

/**
 * Bridges live data sources into TimeSeries ring buffers for chart display.
 * Maintains 6 bandwidth series + 6 latency series.
 */
public final class ChartSampler {
    private static final int CAPACITY = 120;

    // Client-side bandwidth series
    public static final TimeSeries clientNic = new TimeSeries(CAPACITY);
    public static final TimeSeries clientBaked = new TimeSeries(CAPACITY);
    public static final TimeSeries clientRaw = new TimeSeries(CAPACITY);

    // Server-side bandwidth series
    public static final TimeSeries serverNic = new TimeSeries(CAPACITY);
    public static final TimeSeries serverBaked = new TimeSeries(CAPACITY);
    public static final TimeSeries serverRaw = new TimeSeries(CAPACITY);

    // Client-side latency series (values in microseconds)
    public static final TimeSeries clientBufferingLatency = new TimeSeries(CAPACITY);
    public static final TimeSeries clientCompressTime = new TimeSeries(CAPACITY);
    public static final TimeSeries clientDecompressTime = new TimeSeries(CAPACITY);

    // Server-side latency series (values in microseconds)
    public static final TimeSeries serverBufferingLatency = new TimeSeries(CAPACITY);
    public static final TimeSeries serverCompressTime = new TimeSeries(CAPACITY);
    public static final TimeSeries serverDecompressTime = new TimeSeries(CAPACITY);

    private ChartSampler() {}

    /**
     * Sample current speeds into all series. Called every 10 ticks (500ms).
     */
    public static void sample() {
        // Client NIC
        if (SystemTrafficMonitor.isAvailable()) {
            clientNic.push(
                    SystemTrafficMonitor.getInboundBytesPerSec()
                            + SystemTrafficMonitor.getOutboundBytesPerSec()
            );
        } else {
            clientNic.push(0);
        }

        // Client mod stats
        clientBaked.push(
                (long) (LOCAL.inboundSpeedBaked().averageIn1s()
                        + LOCAL.outboundSpeedBaked().averageIn1s())
        );
        clientRaw.push(
                (long) (LOCAL.inboundSpeedRaw().averageIn1s()
                        + LOCAL.outboundSpeedRaw().averageIn1s())
        );

        // Server NIC
        serverNic.push(nicInboundSpeedServer + nicOutboundSpeedServer);

        // Server mod stats
        serverBaked.push((long) (inboundSpeedBakedServer + outboundSpeedBakedServer));
        serverRaw.push((long) (inboundSpeedRawServer + outboundSpeedRawServer));

        // Client latency (local LatencyCounters, values in microseconds)
        clientBufferingLatency.push((long) bufferingLatency.averageMicros());
        clientCompressTime.push((long) compressionTime.averageMicros());
        clientDecompressTime.push((long) decompressionTime.averageMicros());

        // Server latency (volatile ms from payload, convert to microseconds)
        serverBufferingLatency.push((long) (bufferingLatencyMsServer * 1000));
        serverCompressTime.push((long) (compressionTimeMsServer * 1000));
        serverDecompressTime.push((long) (decompressionTimeMsServer * 1000));
    }

    public static void reset() {
        clientNic.reset();
        clientBaked.reset();
        clientRaw.reset();
        serverNic.reset();
        serverBaked.reset();
        serverRaw.reset();
        clientBufferingLatency.reset();
        clientCompressTime.reset();
        clientDecompressTime.reset();
        serverBufferingLatency.reset();
        serverCompressTime.reset();
        serverDecompressTime.reset();
    }
}
