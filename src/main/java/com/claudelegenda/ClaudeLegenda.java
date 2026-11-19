package com.claudelegenda;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

/** Общая (серверная + клиентская) точка входа мода. */
public class ClaudeLegenda implements ModInitializer {
    public static final String MOD_ID = "claude_legenda";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModParticles.register();
        PayloadTypeRegistry.playS2C().register(StoryStatePayload.ID, StoryStatePayload.CODEC);
        StoryManager.init();
        ClaudeCommands.register();
    }
}
