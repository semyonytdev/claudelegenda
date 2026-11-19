package com.claudelegenda;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** /claude stop  и  /claudehello */
public final class ClaudeCommands {
    private ClaudeCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("claude")
                    .then(CommandManager.literal("stop").executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        StoryManager.stop(p);
                        ctx.getSource().sendFeedback(
                                () -> Text.literal("Серый мир, звёзды и эффекты убраны."), false);
                        return 1;
                    })));

            dispatcher.register(CommandManager.literal("claudehello").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                StoryManager.hello(p);
                ctx.getSource().sendFeedback(
                        () -> Text.literal("Серый мир вернулся. Клод снова здесь."), false);
                return 1;
            }));
        });
    }
}
