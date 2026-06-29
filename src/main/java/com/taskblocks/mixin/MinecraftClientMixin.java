package com.taskblocks.mixin;

import com.taskblocks.script.ScriptRunner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (!ScriptRunner.isRunning()) return;

        ScriptRunner.reapplyHeldKeys();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) return;

        // Screen was just closed — resume block breaking immediately
        if (screen == null && ScriptRunner.isHoldingMouse("left")) {
            client.options.attackKey.setPressed(true);
            client.attackCooldown = 0;
            if (client.crosshairTarget instanceof BlockHitResult hit
                    && client.world != null
                    && !client.world.getBlockState(hit.getBlockPos()).isAir()) {
                client.interactionManager.updateBlockBreakingProgress(
                    hit.getBlockPos(), hit.getSide());
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }
        }
    }
}