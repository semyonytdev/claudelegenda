package com.claudelegenda;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Сервер -> клиент: включён ли серый мир и звёзды. */
public record StoryStatePayload(boolean gray) implements CustomPayload {
    public static final CustomPayload.Id<StoryStatePayload> ID =
            new CustomPayload.Id<>(ClaudeLegenda.id("story_state"));

    public static final PacketCodec<ByteBuf, StoryStatePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.BOOLEAN, StoryStatePayload::gray, StoryStatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
