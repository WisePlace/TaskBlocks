package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

public class MovementActions {

    public static void register() {
        ActionRegistry.register(MovementActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx)
            throws InterruptedException {
        MinecraftClient client = MinecraftClient.getInstance();

        if (action.startsWith("look(") && action.endsWith(")")) {
            String inner = action.substring(5, action.length() - 1).trim();
            String[] parts = inner.split(",");

            Float targetYaw   = null;
            Float targetPitch = null;
            int   steps       = 1;
            long  delayMs     = 0;
            int   argOffset   = 0;

            String first = parts[0].trim().toLowerCase();

            switch (first) {
                case "north" -> { targetYaw = 180f;  targetPitch = 0f;   argOffset = 1; }
                case "south" -> { targetYaw = 0f;    targetPitch = 0f;   argOffset = 1; }
                case "east"  -> { targetYaw = -90f;  targetPitch = 0f;   argOffset = 1; }
                case "west"  -> { targetYaw = 90f;   targetPitch = 0f;   argOffset = 1; }
                case "up"    -> { targetYaw = null;  targetPitch = -90f; argOffset = 1; }
                case "down"  -> { targetYaw = null;  targetPitch = 90f;  argOffset = 1; }
                default -> {
                    try {
                        targetYaw = Float.parseFloat(parts[0].trim());
                        if (parts.length >= 2) {
                            targetPitch = Float.parseFloat(parts[1].trim());
                        } else {
                            targetPitch = 0f;
                        }
                        argOffset = 2;
                    } catch (NumberFormatException e) {
                        TaskBlocks.LOGGER.error("[TaskBlocks] Invalid look() argument: " + first);
                        TaskBlocksNotifier.error("Invalid look() argument: §f" + first);
                        return ActionResult.normal();
                    }
                }
            }

            if (parts.length > argOffset) {
                try {
                    steps   = Integer.parseInt(parts[argOffset].trim());
                    delayMs = parts.length > argOffset + 1
                        ? Long.parseLong(parts[argOffset + 1].trim())
                        : 16;
                } catch (NumberFormatException e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Invalid look() steps/delay: " + inner);
                    TaskBlocksNotifier.error("Invalid look() steps/delay: §f" + inner);
                    return ActionResult.normal();
                }
            }

            if (targetPitch != null) {
                targetPitch = MathHelper.clamp(targetPitch, -90f, 90f);
            }

            final Float finalTargetYaw   = targetYaw;
            final Float finalTargetPitch = targetPitch;

            if (steps <= 1) {
                // Instant
                java.util.concurrent.CountDownLatch instantLatch =
                    new java.util.concurrent.CountDownLatch(1);
                client.execute(() -> {
                    if (client.player != null) {
                        if (finalTargetYaw   != null) client.player.setYaw(finalTargetYaw);
                        if (finalTargetPitch != null) client.player.setPitch(finalTargetPitch);
                    }
                    instantLatch.countDown();
                });
                instantLatch.await();
                Thread.sleep(50);
            } else {
                // Smooth — read starting position once, then fire-and-forget each step
                float[] current = new float[2];
                java.util.concurrent.CountDownLatch latch =
                    new java.util.concurrent.CountDownLatch(1);
                client.execute(() -> {
                    if (client.player != null) {
                        current[0] = client.player.getYaw();
                        current[1] = client.player.getPitch();
                    }
                    latch.countDown();
                });
                latch.await();

                float startYaw   = current[0];
                float startPitch = current[1];
                float endYaw     = finalTargetYaw   != null ? finalTargetYaw   : startYaw;
                float endPitch   = finalTargetPitch != null ? finalTargetPitch : startPitch;

                float yawDiff = endYaw - startYaw;
                while (yawDiff > 180f)  yawDiff -= 360f;
                while (yawDiff < -180f) yawDiff += 360f;

                final float finalYawDiff    = yawDiff;
                final float finalStartYaw   = startYaw;
                final float finalStartPitch = startPitch;
                final float finalEndPitch   = endPitch;

                for (int i = 1; i <= steps; i++) {
                    if (Thread.currentThread().isInterrupted()) break;
                    float t      = (float) i / steps;
                    float smooth = t * t * (3f - 2f * t);
                    float newYaw   = finalStartYaw   + finalYawDiff   * smooth;
                    float newPitch = finalStartPitch + (finalEndPitch - finalStartPitch) * smooth;

                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.setYaw(newYaw);
                            client.player.setPitch(MathHelper.clamp(newPitch, -90f, 90f));
                        }
                    });
                    Thread.sleep(delayMs);
                }
            }
            return ActionResult.normal();
        }

        // --- get_yaw(varName) ---
// --- get_yaw(varName) ---
        if (action.startsWith("get_yaw(") && action.endsWith(")")) {
            String varName = action.substring(8, action.length() - 1).trim();
            float[] result = new float[1];
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            client.execute(() -> {
                if (client.player != null) result[0] = client.player.getYaw();
                latch.countDown();
            });
            latch.await();

            // Normalize to -180..180 range, matching the F3 debug screen
            float normalizedYaw = result[0] % 360f;
            if (normalizedYaw > 180f)  normalizedYaw -= 360f;
            if (normalizedYaw < -180f) normalizedYaw += 360f;

            ctx.variables.put(varName, String.valueOf(Math.round(normalizedYaw * 10) / 10.0));
            return ActionResult.normal();
        }

        // --- get_pitch(varName) ---
        if (action.startsWith("get_pitch(") && action.endsWith(")")) {
            String varName = action.substring(10, action.length() - 1).trim();
            float[] result = new float[1];
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            client.execute(() -> {
                if (client.player != null) result[0] = client.player.getPitch();
                latch.countDown();
            });
            latch.await();
            ctx.variables.put(varName, String.valueOf(Math.round(result[0] * 10) / 10.0));
            return ActionResult.normal();
        }

        // --- get_pos(varX, varY, varZ) ---
        if (action.startsWith("get_pos(") && action.endsWith(")")) {
            String inner = action.substring(8, action.length() - 1).trim();
            String[] parts = inner.split(",");
            if (parts.length == 3) {
                String varX = parts[0].trim();
                String varY = parts[1].trim();
                String varZ = parts[2].trim();
                double[] result = new double[3];
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                client.execute(() -> {
                    if (client.player != null) {
                        result[0] = client.player.getX();
                        result[1] = client.player.getY();
                        result[2] = client.player.getZ();
                    }
                    latch.countDown();
                });
                latch.await();
                ctx.variables.put(varX, String.valueOf(Math.round(result[0] * 10) / 10.0));
                ctx.variables.put(varY, String.valueOf(Math.round(result[1] * 10) / 10.0));
                ctx.variables.put(varZ, String.valueOf(Math.round(result[2] * 10) / 10.0));
            } else {
                TaskBlocks.LOGGER.error("[TaskBlocks] get_pos() needs 3 args: get_pos(varX, varY, varZ)");
                TaskBlocksNotifier.error("get_pos() needs 3 args: get_pos(varX, varY, varZ)");
            }
            return ActionResult.normal();
        }

        return null;
    }
}