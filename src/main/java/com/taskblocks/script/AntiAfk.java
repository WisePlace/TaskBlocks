package com.taskblocks.script;

import java.util.Random;

import com.taskblocks.TaskBlocks;

import net.minecraft.client.MinecraftClient;

// Periodically nudges the camera by a small random amount while a
// script is running, so servers checking for stalled position/
// rotation don't flag the player as AFK.
public class AntiAfk {

    private static final Random RANDOM = new Random();

    private static boolean enabled = false;
    private static int intervalSeconds = 60;
    private static float nudgeRangeDegrees = 0.2f;
    private static long tickCounter = 0;
    private static long lastActionTick = 0;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static int getIntervalSeconds() {
        return intervalSeconds;
    }

    public static void setIntervalSeconds(int seconds) {
        intervalSeconds = Math.max(5, seconds);
    }

    public static float getNudgeRangeDegrees() {
        return nudgeRangeDegrees;
    }

    public static void setNudgeRangeDegrees(float degrees) {
        nudgeRangeDegrees = Math.max(0.01f, degrees);
    }

    public static void tick(MinecraftClient client) {
        if (!enabled || client.player == null) return;
        if (!ScriptRunner.isRunning()) return;

        tickCounter++;
        long intervalTicks = intervalSeconds * 20L;

        if (tickCounter - lastActionTick < intervalTicks) return;
        lastActionTick = tickCounter;

        float yawNudge = (RANDOM.nextFloat() - 0.5f) * nudgeRangeDegrees;
        float pitchNudge = (RANDOM.nextFloat() - 0.5f) * nudgeRangeDegrees;

        client.player.setYaw(client.player.getYaw() + yawNudge);
        client.player.setPitch(client.player.getPitch() + pitchNudge);

        TaskBlocks.LOGGER.info("[TaskBlocks] Anti-AFK: nudged");
    }
}