package cn.ussshenzhou.notenoughbandwidth.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Carries a chunk's full serialized data (ChunkData + LightData) compressed through
 * a dedicated per-connection Zstd streaming context whose window contains only prior
 * chunk data. This yields far better cross-chunk back-references than the shared
 * aggregation context.
 * <p>
 * Sent instead of the vanilla ChunkDataS2CPacket for cache-miss chunks when NEB is active.
 * Bypasses the aggregation system (already optimally compressed).
 */
public record CompressedChunkPayload(
        int chunkX,
        int chunkZ,
        int originalSize,
        byte[] compressedData
) implements CustomPayload {
    public static final Id<CompressedChunkPayload> TYPE =
            new Id<>(Identifier.of(ModConstants.NETWORK_NAMESPACE, "compressed_chunk"));

    // Vanilla chunks are at most ~2MB sections + light + BE.
    private static final int MAX_ORIGINAL_SIZE = 8 * 1024 * 1024;
    private static final int MAX_COMPRESSED_SIZE = 4 * 1024 * 1024;

    public static final PacketCodec<PacketByteBuf, CompressedChunkPayload> CODEC =
            PacketCodec.of(CompressedChunkPayload::write, CompressedChunkPayload::read);

    private void write(PacketByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeVarInt(originalSize);
        buf.writeVarInt(compressedData.length);
        buf.writeBytes(compressedData);
    }

    private static CompressedChunkPayload read(PacketByteBuf buf) {
        int x = buf.readInt();
        int z = buf.readInt();
        int originalSize = buf.readVarInt();
        if (originalSize > MAX_ORIGINAL_SIZE) {
            throw new IllegalArgumentException("Original chunk size too large: " + originalSize);
        }
        int compressedLen = buf.readVarInt();
        if (compressedLen > MAX_COMPRESSED_SIZE) {
            throw new IllegalArgumentException("Compressed chunk too large: " + compressedLen);
        }
        byte[] data = new byte[compressedLen];
        buf.readBytes(data);
        return new CompressedChunkPayload(x, z, originalSize, data);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
