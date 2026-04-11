package cn.ussshenzhou.notenoughbandwidth.zstd;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.ClientConnection;

import java.util.concurrent.ExecutionException;

/**
 * Connection-level cache for chunk-dedicated Zstd contexts.
 * Same pattern as {@link ZstdHelper} but uses {@link ChunkZstdContext}
 * with a larger window and chunk-specific dictionary.
 */
public class ChunkZstdHelper {

    private static final Cache<ClientConnection, ChunkZstdContext> CONTEXT_CACHE = CacheBuilder.newBuilder()
            .weakKeys()
            .removalListener((RemovalListener<ClientConnection, ChunkZstdContext>) notification -> {
                if (notification.getValue() != null) {
                    notification.getValue().close();
                }
            })
            .build();

    public static ByteBuf compress(ClientConnection connection, ByteBuf raw) {
        return Unpooled.wrappedBuffer(get(connection).compress(raw.nioBuffer()));
    }

    public static ByteBuf decompress(ClientConnection connection, ByteBuf compressed, int originalSize) {
        try {
            if (compressed.isDirect()) {
                return Unpooled.wrappedBuffer(get(connection).decompress(compressed.nioBuffer(), originalSize));
            } else {
                var directBuf = Unpooled.directBuffer(compressed.readableBytes());
                try {
                    compressed.getBytes(compressed.readerIndex(), directBuf);
                    return Unpooled.wrappedBuffer(get(connection).decompress(directBuf.nioBuffer(), originalSize));
                } finally {
                    directBuf.release();
                }
            }
        } finally {
            compressed.release();
        }
    }

    private static ChunkZstdContext get(ClientConnection connection) {
        try {
            return CONTEXT_CACHE.get(connection, () -> new ChunkZstdContext(ChunkDictionaryManager.getDict()));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static void evict(ClientConnection connection) {
        CONTEXT_CACHE.invalidate(connection);
    }
}
