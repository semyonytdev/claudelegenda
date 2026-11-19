package com.claudelegenda;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Регистрация частиц мода. */
public final class ModParticles {
    private ModParticles() {}

    /** Красная звёздочка (текстура: textures/particle/red_star.png). */
    public static final SimpleParticleType RED_STAR = FabricParticleTypes.simple();

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE, ClaudeLegenda.id("red_star"), RED_STAR);
    }
}
