package com.claudelegenda.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Даёт доступ к приватному GameRenderer#setPostProcessor(Identifier). */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("setPostProcessor")
    void claudeLegenda$setPostProcessor(Identifier id);
}
