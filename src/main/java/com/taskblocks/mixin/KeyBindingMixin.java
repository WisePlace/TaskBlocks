package com.taskblocks.mixin;

import com.taskblocks.script.ScriptRunner;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyBinding.class)
public class KeyBindingMixin {

    @Inject(method = "unpressAll", at = @At("RETURN"))
    private static void onUnpressAll(CallbackInfo ci) {
        // After Minecraft clears all keys, re-apply our held keys
        if (ScriptRunner.isRunning()) {
            ScriptRunner.reapplyHeldKeys();
        }
    }
}