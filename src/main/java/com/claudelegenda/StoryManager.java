package com.claudelegenda;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/** Состояние сюжета игрока + эффекты + главный серверный тик. */
public final class StoryManager {
    private StoryManager() {}

    public static final int F_STARTED = 1;   // первый вход уже был
    public static final int F_ACTIVE = 2;    // серый мир / эффекты включены
    public static final int F_BASE = 4;      // игрок уже побывал в комнате базы
    public static final int F_TRIGGERED = 8; // поршень уже сработал для игрока

    /** Битовые флаги, хранятся в данных игрока (переживают выход и смерть). */
    public static final AttachmentType<Integer> FLAGS = AttachmentRegistry.<Integer>create(
            ClaudeLegenda.id("flags"),
            b -> b.initializer(() -> 0).persistent(Codec.INT).copyOnDeath());

    public static boolean has(ServerPlayerEntity p, int flag) {
        return (p.getAttachedOrElse(FLAGS, 0) & flag) != 0;
    }

    public static void set(ServerPlayerEntity p, int flag, boolean on) {
        int v = p.getAttachedOrElse(FLAGS, 0);
        p.setAttached(FLAGS, on ? (v | flag) : (v & ~flag));
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(StoryManager::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(BaseBuilder::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BaseBuilder.onServerStopped());
    }

    // ------------------------------------------------------------------ вход

    private static void onJoin(ServerPlayerEntity p) {
        if (!has(p, F_STARTED)) {
            set(p, F_STARTED, true);
            set(p, F_ACTIVE, true);
            // Единственный предмет — «Записка». Инвентарь не чистим, чтобы не стереть чужие вещи.
            p.giveItemStack(Books.note());
        }
        sync(p);
        if (has(p, F_ACTIVE)) {
            applyEffects(p);
        }
    }

    static void sync(ServerPlayerEntity p) {
        ServerPlayNetworking.send(p, new StoryStatePayload(has(p, F_ACTIVE)));
    }

    // --------------------------------------------------------------- эффекты

    static void applyEffects(ServerPlayerEntity p) {
        ensureInfinite(p, StatusEffects.SLOWNESS, ClaudeConfig.SLOWNESS_AMPLIFIER);
        if (has(p, F_BASE)) {
            ensureInfinite(p, StatusEffects.REGENERATION, ClaudeConfig.REGENERATION_AMPLIFIER);
        }
    }

    private static void ensureInfinite(ServerPlayerEntity p, RegistryEntry<StatusEffect> effect, int amplifier) {
        StatusEffectInstance cur = p.getStatusEffect(effect);
        if (cur == null || !cur.isInfinite() || cur.getAmplifier() != amplifier) {
            p.addStatusEffect(new StatusEffectInstance(
                    effect, StatusEffectInstance.INFINITE, amplifier, false, false, true));
        }
    }

    private static void removeInfinite(ServerPlayerEntity p, RegistryEntry<StatusEffect> effect) {
        StatusEffectInstance cur = p.getStatusEffect(effect);
        if (cur != null && cur.isInfinite()) {
            p.removeStatusEffect(effect);
        }
    }

    // ---------------------------------------------------------------- команды

    /** /claude stop */
    public static void stop(ServerPlayerEntity p) {
        set(p, F_ACTIVE, false);
        removeInfinite(p, StatusEffects.SLOWNESS);
        removeInfinite(p, StatusEffects.REGENERATION);
        sync(p);
    }

    /** /claudehello */
    public static void hello(ServerPlayerEntity p) {
        set(p, F_STARTED, true);
        set(p, F_ACTIVE, true);
        applyEffects(p);
        sync(p);
    }

    // ------------------------------------------------------------ база: вход

    /** Игрок оказался внутри затопленной комнаты. */
    static void onArrive(ServerPlayerEntity p) {
        set(p, F_BASE, true);
        p.giveItemStack(Books.best());
        applyEffects(p); // теперь добавится постоянная Regeneration
        p.sendMessage(Text.literal("Ты в тайной базе Клода."), true);
    }

    // ------------------------------------------------------------------- тик

    private static void tick(MinecraftServer server) {
        int t = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!has(p, F_ACTIVE)) {
                continue;
            }
            if (p.isSprinting()) {
                p.setSprinting(false); // бегать нельзя
            }
            if (t % 20 == 0) {
                applyEffects(p); // вернёт эффекты, если их сняло молоко и т.п.
            }
            ServerWorld world = p.getEntityWorld();
            if (world.getRegistryKey() == World.OVERWORLD) {
                BaseBuilder.tickPlayer(server, world, p, t);
            }
        }
        BaseBuilder.tickPending(server);
    }
}
