package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

// sound_start(id) / sound_start(id, volume) / sound_start(id, volume, pitch)
// plays a Minecraft sound once. sound_stop cuts off the last one started
// if it's still playing. sound_volume(amount) sets the default volume
// used by future sound_start() calls that don't specify their own.
public class SoundActions {

    private static float defaultVolume = 1.0f;
    private static SoundInstance lastSoundInstance = null;

    public static void register() {
        ActionRegistry.register(SoundActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (action.startsWith("sound_start(") && action.endsWith(")")) {
            String inner = action.substring(12, action.length() - 1);
            String[] parts = inner.split(",");

            if (parts.length < 1 || parts[0].trim().isEmpty()) {
                TaskBlocks.LOGGER.error("[TaskBlocks] sound_start() needs a sound id");
                TaskBlocksNotifier.error("sound_start() needs a sound id");
                return ActionResult.normal();
            }

            String soundName = parts[0].trim();
            float volume = defaultVolume;
            float pitch = 1.0f;

            if (parts.length >= 2) {
                try {
                    volume = Float.parseFloat(parts[1].trim());
                } catch (NumberFormatException e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Invalid sound_start() volume: " + parts[1]);
                    TaskBlocksNotifier.error("Invalid sound_start() volume: §f" + parts[1]);
                }
            }

            if (parts.length >= 3) {
                try {
                    pitch = Float.parseFloat(parts[2].trim());
                } catch (NumberFormatException e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Invalid sound_start() pitch: " + parts[2]);
                    TaskBlocksNotifier.error("Invalid sound_start() pitch: §f" + parts[2]);
                }
            }

            Identifier soundId = soundName.contains(":")
                ? Identifier.of(soundName)
                : Identifier.of("minecraft", soundName);

            SoundEvent event = Registries.SOUND_EVENT.get(soundId);
            if (event == null) {
                TaskBlocks.LOGGER.error("[TaskBlocks] sound_start(): unknown sound '" + soundName + "'");
                TaskBlocksNotifier.error("sound_start(): unknown sound '" + soundName + "'");
                return ActionResult.normal();
            }

            SoundInstance instance = PositionedSoundInstance.ui(event, pitch, volume);
            lastSoundInstance = instance;

            client.execute(() -> client.getSoundManager().play(instance));

            return ActionResult.normal();
        }

        if (action.equalsIgnoreCase("sound_stop")) {
            SoundInstance instance = lastSoundInstance;
            if (instance != null) {
                client.execute(() -> client.getSoundManager().stop(instance));
            }
            return ActionResult.normal();
        }

        if (action.startsWith("sound_volume(") && action.endsWith(")")) {
            String inner = action.substring(14, action.length() - 1).trim();
            try {
                defaultVolume = Float.parseFloat(inner);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid sound_volume() value: " + inner);
                TaskBlocksNotifier.error("Invalid sound_volume() value: §f" + inner);
            }
            return ActionResult.normal();
        }

        return null;
    }
}