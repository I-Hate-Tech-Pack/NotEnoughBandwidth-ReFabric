package cn.ussshenzhou.notenoughbandwidth.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Sent from server to client during handshake, after the general DictionarySyncPayload.
 * Carries a Zstd dictionary trained exclusively on chunk section data for the
 * dedicated chunk compression context.
 */
public record ChunkDictSyncPayload(byte[] dictionary) implements CustomPayload {
    public static final Id<ChunkDictSyncPayload> TYPE =
            new Id<>(Identifier.of(ModConstants.NETWORK_NAMESPACE, "chunk_dict_sync"));

    private static final int MAX_DICT_SIZE = 256 * 1024;

    public static final PacketCodec<PacketByteBuf, ChunkDictSyncPayload> CODEC =
            PacketCodec.of(ChunkDictSyncPayload::write, ChunkDictSyncPayload::read);

    private void write(PacketByteBuf buf) {
        if (dictionary != null && dictionary.length > 0) {
            buf.writeVarInt(dictionary.length);
            buf.writeBytes(dictionary);
        } else {
            buf.writeVarInt(0);
        }
    }

    private static ChunkDictSyncPayload read(PacketByteBuf buf) {
        int length = buf.readVarInt();
        if (length > MAX_DICT_SIZE) {
            throw new IllegalArgumentException("Chunk dictionary too large: " + length + " bytes (max " + MAX_DICT_SIZE + ")");
        }
        if (length > 0) {
            byte[] dict = new byte[length];
            buf.readBytes(dict);
            return new ChunkDictSyncPayload(dict);
        }
        return new ChunkDictSyncPayload(new byte[0]);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
