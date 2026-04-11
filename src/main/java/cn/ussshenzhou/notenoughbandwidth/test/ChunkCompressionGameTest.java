package cn.ussshenzhou.notenoughbandwidth.test;

import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDictTrainer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;

/**
 * Chunk compression benchmark with structural optimization comparison.
 * Uses real MC world gen (full pipeline) via gametest server.
 * <p>
 * Run: ./gradlew runGametest
 */
public class ChunkCompressionGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("NEB-Benchmark");
    private static final int GRID = 15;

    /**
     * Per-chunk data: flat sections blob + individual section byte arrays.
     */
    record ChunkData(byte[] allSections, byte[][] perSection) {}

    @GameTest(templateName = "fabric-gametest-api-v1:empty", tickLimit = 12000)
    public void chunkCompressionBenchmark(TestContext ctx) {
        try {
            var world = ctx.getWorld();
            int offsetX = 1000, offsetZ = 1000;

            LOG.info("=== Real MC Chunk Compression Benchmark (Full World Gen) ===");
            LOG.info("Grid: {}x{} = {} chunks | Seed: {}", GRID, GRID, GRID * GRID, world.getSeed());

            List<ChunkData> chunks = new ArrayList<>();
            for (int cx = 0; cx < GRID; cx++) {
                for (int cz = 0; cz < GRID; cz++) {
                    var chunk = world.getChunk(offsetX + cx, offsetZ + cz);
                    chunks.add(serializeChunk(chunk));
                }
                LOG.info("  Generated row {}/{}", cx + 1, GRID);
            }

            List<byte[]> flat = chunks.stream().map(ChunkData::allSections).toList();
            long totalRaw = flat.stream().mapToLong(c -> c.length).sum();
            long zlibTotal = 0;
            for (byte[] c : flat) zlibTotal += compressZlib(c, 6);

            LOG.info("Raw: {} bytes ({} KB), avg {}/chunk", totalRaw, totalRaw / 1024, totalRaw / chunks.size());
            LOG.info("Zlib lv6 baseline: {} bytes ({}% of raw)", zlibTotal, fmt(pct(zlibTotal, totalRaw)));

            // Current approach: full sections blob through streaming context
            LOG.info("");
            LOG.info("=== Structural Optimization Comparison (all lv19, w=16MB) ===");
            int LV = 19;

            long current = compressZstdStreaming(flat, LV, 24);
            logOpt("A) Current: full sections stream", current, totalRaw, zlibTotal);

            // B) Sections-only: skip empty (air) sections, mark with bitmask
            long sectionsOnly = benchmarkSectionsOnly(chunks, LV);
            logOpt("B) Skip air sections (bitmask)", sectionsOnly, totalRaw, zlibTotal);

            // C) Y-level grouped: reorder sections across chunks by Y level
            long yGrouped = benchmarkYGrouped(chunks, LV);
            logOpt("C) Y-level grouped stream", yGrouped, totalRaw, zlibTotal);

            // D) Delta encoding: XOR each section with prev chunk's same section
            long delta = benchmarkDelta(chunks, LV);
            logOpt("D) Delta (XOR prev chunk) + stream", delta, totalRaw, zlibTotal);

            // E) Combined: Y-grouped + delta
            long yDelta = benchmarkYGroupedDelta(chunks, LV);
            logOpt("E) Y-grouped + delta", yDelta, totalRaw, zlibTotal);

            // F) Per-Y-level separate contexts (one context per Y level)
            long perY = benchmarkPerYContext(chunks, LV);
            logOpt("F) Separate context per Y-level", perY, totalRaw, zlibTotal);

            ctx.complete();
        } catch (Exception e) {
            LOG.error("Benchmark failed", e);
            ctx.throwGameTestException(e.getMessage());
        }
    }

    // ── Structural optimization benchmarks ──────────────────────────────────

    /**
     * B) Skip sections that are "empty" (single-value palette = air).
     * Sends a bitmask (3 bytes for 24 sections) + only non-empty sections.
     */
    private long benchmarkSectionsOnly(List<ChunkData> chunks, int level) {
        // First pass: build filtered chunks (bitmask overhead + non-empty sections)
        List<byte[]> filtered = new ArrayList<>();
        for (var cd : chunks) {
            var out = new ByteArrayOutputStream();
            int sectionCount = cd.perSection.length;
            // 32-bit bitmask (supports up to 32 sections)
            int mask = 0;
            for (int i = 0; i < sectionCount; i++) {
                if (cd.perSection[i].length > 10) { // non-trivial section
                    mask |= (1 << i);
                }
            }
            // Write bitmask as 4 bytes
            out.write((mask >> 24) & 0xFF);
            out.write((mask >> 16) & 0xFF);
            out.write((mask >> 8) & 0xFF);
            out.write(mask & 0xFF);
            // Write only non-empty sections
            for (int i = 0; i < sectionCount; i++) {
                if ((mask & (1 << i)) != 0) {
                    out.writeBytes(cd.perSection[i]);
                }
            }
            filtered.add(out.toByteArray());
        }
        return compressZstdStreaming(filtered, level, 24);
    }

    /**
     * C) Reorder: instead of [chunk0_all_sections, chunk1_all_sections, ...],
     * feed [all_chunks_section0, all_chunks_section1, ...] into streaming context.
     */
    private long benchmarkYGrouped(List<ChunkData> chunks, int level) {
        int sectionCount = chunks.get(0).perSection.length;
        var ctx = createCtx(level);
        long total = 0;
        for (int y = 0; y < sectionCount; y++) {
            for (var cd : chunks) {
                byte[] sec = cd.perSection[y];
                total += compressOne(ctx, sec);
            }
        }
        ctx.close();
        return total;
    }

    /**
     * D) Delta encoding: XOR each section with the same Y-level section from
     * the previous chunk in row order. First chunk in each row is sent raw.
     */
    private long benchmarkDelta(List<ChunkData> chunks, int level) {
        var ctx = createCtx(level);
        long total = 0;
        byte[][] prev = null;
        for (var cd : chunks) {
            if (prev == null) {
                total += compressOne(ctx, cd.allSections);
            } else {
                byte[] deltaBlob = xorSections(cd.perSection, prev);
                total += compressOne(ctx, deltaBlob);
            }
            prev = cd.perSection;
        }
        ctx.close();
        return total;
    }

    /**
     * E) Combined: Y-level grouped + delta against previous chunk at same Y level.
     */
    private long benchmarkYGroupedDelta(List<ChunkData> chunks, int level) {
        int sectionCount = chunks.get(0).perSection.length;
        var ctx = createCtx(level);
        long total = 0;
        for (int y = 0; y < sectionCount; y++) {
            byte[] prev = null;
            for (var cd : chunks) {
                byte[] sec = cd.perSection[y];
                if (prev != null && prev.length == sec.length) {
                    byte[] delta = xor(sec, prev);
                    total += compressOne(ctx, delta);
                } else {
                    total += compressOne(ctx, sec);
                }
                prev = sec;
            }
        }
        ctx.close();
        return total;
    }

    /**
     * F) One separate streaming context per Y-level.
     * Each context's window only sees data from the same geological layer.
     */
    private long benchmarkPerYContext(List<ChunkData> chunks, int level) {
        int sectionCount = chunks.get(0).perSection.length;
        long total = 0;
        for (int y = 0; y < sectionCount; y++) {
            var ctx = createCtx(level);
            for (var cd : chunks) {
                total += compressOne(ctx, cd.perSection[y]);
            }
            ctx.close();
        }
        return total;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static byte[] xorSections(byte[][] current, byte[][] prev) {
        var out = new ByteArrayOutputStream();
        for (int i = 0; i < current.length; i++) {
            if (i < prev.length && current[i].length == prev[i].length) {
                out.writeBytes(xor(current[i], prev[i]));
            } else {
                out.writeBytes(current[i]);
            }
        }
        return out.toByteArray();
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    private static ZstdCompressCtx createCtx(int level) {
        var ctx = new ZstdCompressCtx();
        ctx.setLevel(level);
        ctx.setContentSize(false);
        ctx.setMagicless(true);
        ctx.setWindowLog(24);
        return ctx;
    }

    private static long compressOne(ZstdCompressCtx ctx, byte[] data) {
        var src = ByteBuffer.allocateDirect(data.length);
        src.put(data); src.flip();
        var dst = ByteBuffer.allocateDirect((int) Zstd.compressBound(data.length));
        ctx.compressDirectByteBufferStream(dst, src, EndDirective.FLUSH);
        dst.flip();
        return dst.remaining();
    }

    private static ChunkData serializeChunk(Chunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        byte[][] perSection = new byte[sections.length][];
        var allBuf = new PacketByteBuf(Unpooled.buffer());
        for (int i = 0; i < sections.length; i++) {
            var secBuf = new PacketByteBuf(Unpooled.buffer());
            sections[i].toPacket(secBuf);
            perSection[i] = new byte[secBuf.readableBytes()];
            secBuf.readBytes(perSection[i]);
            allBuf.writeBytes(perSection[i]);
            secBuf.release();
        }
        byte[] all = new byte[allBuf.readableBytes()];
        allBuf.readBytes(all);
        allBuf.release();
        return new ChunkData(all, perSection);
    }

    private void logOpt(String method, long compressed, long raw, long zlibBase) {
        LOG.info("  {}: {} bytes ({}% of raw) {}% vs zlib",
                method, compressed, fmt(pct(compressed, raw)), fmt(saving(compressed, zlibBase)));
    }

    private static int compressZlib(byte[] data, int level) {
        var d = new Deflater(level);
        d.setInput(data);
        d.finish();
        byte[] out = new byte[data.length + 256];
        int sz = d.deflate(out);
        d.end();
        return sz;
    }

    private static long compressZstdStreaming(List<byte[]> chunks, int level, int wlog) {
        var ctx = createCtx(level);
        if (wlog != 24) ctx.setWindowLog(wlog);
        long total = 0;
        for (byte[] chunk : chunks) total += compressOne(ctx, chunk);
        ctx.close();
        return total;
    }

    private static double pct(long part, long whole) { return (double) part / whole * 100; }
    private static double saving(long val, long base) { return (1.0 - (double) val / base) * 100; }
    private static String fmt(double v) { return String.format("%.1f", v); }
}
