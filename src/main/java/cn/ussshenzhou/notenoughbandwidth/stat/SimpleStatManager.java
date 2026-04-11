package cn.ussshenzhou.notenoughbandwidth.stat;

import cn.ussshenzhou.notenoughbandwidth.util.LatencyCounter;

import java.util.concurrent.atomic.AtomicLong;

public class SimpleStatManager {
    public static final SimpleStatData LOCAL = new SimpleStatData();

    public static void inBaked(int size) {
        LOCAL.inboundBytesBaked().addAndGet(size);
        LOCAL.inboundSpeedBaked().put(size);
    }

    public static void inRaw(int size) {
        LOCAL.inboundBytesRaw().addAndGet(size);
        LOCAL.inboundSpeedRaw().put(size);
    }

    public static void outBaked(int size) {
        LOCAL.outboundBytesBaked().addAndGet(size);
        LOCAL.outboundSpeedBaked().put(size);
    }

    public static void outRaw(int size) {
        LOCAL.outboundBytesRaw().addAndGet(size);
        LOCAL.outboundSpeedRaw().put(size);
    }

    public static volatile long inboundBytesBakedServer;
    public static volatile long inboundBytesRawServer;
    public static volatile long outboundBytesBakedServer;
    public static volatile long outboundBytesRawServer;
    public static volatile double inboundSpeedBakedServer;
    public static volatile double inboundSpeedRawServer;
    public static volatile double outboundSpeedBakedServer;
    public static volatile double outboundSpeedRawServer;
    public static volatile int dictSizeServer;
    public static volatile int dictSampleCountServer;
    public static volatile int dictSampleThresholdServer;

    // Server-side chunk cache counters (written by ChunkDataSenderMixin on the server).
    public static final AtomicLong chunkCacheHits = new AtomicLong();
    public static final AtomicLong chunkCacheMisses = new AtomicLong();
    public static final AtomicLong chunkCacheSavedBytes = new AtomicLong();

    // Client-side display copies received via StatRespondPayload.
    public static volatile long chunkCacheHitsServer;
    public static volatile long chunkCacheMissesServer;
    public static volatile long chunkCacheSavedBytesServer;

    // Server-side NIC traffic speeds received via StatRespondPayload.
    public static volatile long nicInboundSpeedServer;
    public static volatile long nicOutboundSpeedServer;

    // Latency tracking (local side)
    public static final LatencyCounter bufferingLatency = new LatencyCounter();
    public static final LatencyCounter compressionTime = new LatencyCounter();
    public static final LatencyCounter decompressionTime = new LatencyCounter();
    public static final LatencyCounter encodeOverhead = new LatencyCounter();
    public static final LatencyCounter decodeOverhead = new LatencyCounter();

    // Server-side latency (received via StatRespondPayload)
    public static volatile double bufferingLatencyMsServer;
    public static volatile double compressionTimeMsServer;
    public static volatile double decompressionTimeMsServer;
    public static volatile double encodeOverheadMsServer;
    public static volatile double decodeOverheadMsServer;
}
