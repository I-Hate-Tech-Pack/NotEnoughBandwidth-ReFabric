package cn.ussshenzhou.notenoughbandwidth.zstd;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthConfig;
import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 * Per-connection Zstd context dedicated to chunk section data.
 * Uses a large streaming window (16MB default) so cross-chunk back-references
 * exploit the high similarity between adjacent chunks at the same Y layers.
 */
public class ChunkZstdContext implements Closeable {
    private final ZstdCompressCtx compressCtx;
    private final ZstdDecompressCtx decompressCtx;

    public ChunkZstdContext(@Nullable byte[] dict) {
        var cfg = NotEnoughBandwidthConfig.get();

        compressCtx = new ZstdCompressCtx();
        compressCtx.setLevel(cfg.getChunkCompressionLevel());
        compressCtx.setContentSize(false);
        compressCtx.setMagicless(true);
        compressCtx.setWindowLog(cfg.getChunkWindowLog());
        if (dict != null) {
            compressCtx.loadDict(dict);
        }

        decompressCtx = new ZstdDecompressCtx();
        decompressCtx.setMagicless(true);
        if (dict != null) {
            decompressCtx.loadDict(dict);
        }
    }

    public ByteBuffer compress(ByteBuffer raw) {
        int maxDstSize = (int) Zstd.compressBound(raw.remaining());
        var dst = ByteBuffer.allocateDirect(maxDstSize);
        compressCtx.compressDirectByteBufferStream(dst, raw, EndDirective.FLUSH);
        dst.flip();
        return dst;
    }

    public ByteBuffer decompress(ByteBuffer compressed, int originalSize) {
        var dst = ByteBuffer.allocateDirect(originalSize);
        decompressCtx.decompressDirectByteBufferStream(dst, compressed);
        dst.flip();
        return dst;
    }

    @Override
    public void close() {
        compressCtx.close();
        decompressCtx.close();
    }
}
