package com.claudelegenda.client;

import com.claudelegenda.ClaudeLegenda;
import com.claudelegenda.ModParticles;
import com.claudelegenda.StoryStatePayload;
import com.claudelegenda.mixin.client.GameRendererAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/** Клиент: серый шейдер, дождь из красных звёзд, запрет бега. */
public class ClaudeLegendaClient implements ClientModInitializer {
    /** assets/claude_legenda/post_effect/gray.json */
    private static final Identifier GRAY_EFFECT = ClaudeLegenda.id("gray");

    private static boolean gray = false;

    @Override
    public void onInitializeClient() {
        // Частица. Берём готовую фабрику ванильного огонька (полная яркость, не гаснет мгновенно),
        // текстура — наша красная звезда из particles/red_star.json.
        ParticleFactoryRegistry.getInstance().register(ModParticles.RED_STAR, FlameParticle.Factory::new);

        ClientPlayNetworking.registerGlobalReceiver(StoryStatePayload.ID,
                (payload, context) -> context.client().execute(() -> gray = payload.gray()));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            gray = false;
            clearShader(client);
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (gray && client.player != null) {
                client.options.sprintKey.setPressed(false);
                client.player.setSprinting(false);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(ClaudeLegendaClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }
        updateShader(client);
        if (gray) {
            spawnStars(client);
        }
    }

    // ----------------------------------------------------------------- шейдер

    private static void updateShader(MinecraftClient client) {
        GameRenderer renderer = client.gameRenderer;
        Identifier current = renderer.getPostProcessorId();
        if (gray) {
            if (current == null) {
                ((GameRendererAccessor) renderer).claudeLegenda$setPostProcessor(GRAY_EFFECT);
            }
        } else if (GRAY_EFFECT.equals(current)) {
            renderer.clearPostProcessor();
        }
    }

    private static void clearShader(MinecraftClient client) {
        if (client.gameRenderer != null && GRAY_EFFECT.equals(client.gameRenderer.getPostProcessorId())) {
            client.gameRenderer.clearPostProcessor();
        }
    }

    // ------------------------------------------------------------------ звёзды

    private static void spawnStars(MinecraftClient client) {
        String mode = ((Enum<?>) client.options.getParticles().getValue()).name();
        if (mode.equals("MINIMAL")) {
            return; // частицы выключены — звёзд нет
        }
        int count = mode.equals("DECREASED") ? 3 : 8;

        ClientWorld world = client.world;
        Random rnd = world.getRandom();
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        for (int i = 0; i < count; i++) {
            double x = px + (rnd.nextDouble() - 0.5) * 32.0;
            double y = py + 3.0 + rnd.nextDouble() * 12.0;
            double z = pz + (rnd.nextDouble() - 0.5) * 32.0;
            // «с неба»: только там, где над точкой нет крыши
            if (!world.isSkyVisible(BlockPos.ofFloored(x, y, z))) {
                continue;
            }
            world.addParticleClient(ModParticles.RED_STAR, x, y, z, 0.0, -0.12, 0.0);
        }
    }
}
