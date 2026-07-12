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

        if (action.startsWith("get_yaw(") && action.endsWith(")")) {
            String varName = action.substring(8, action.length() - 1).trim();
            float[] result = new float[1];
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            client.execute(() -> {
                if (client.player != null) result[0] = client.player.getYaw();
                latch.countDown();
            });
            latch.await();

            float normalizedYaw = result[0] % 360f;
            if (normalizedYaw > 180f)  normalizedYaw -= 360f;
            if (normalizedYaw < -180f) normalizedYaw += 360f;

            ctx.variables.put(varName, String.valueOf(Math.round(normalizedYaw * 10) / 10.0));
            return ActionResult.normal();
        }

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

        if (action.startsWith("move(") && action.endsWith(")")) {
            String inner = action.substring(5, action.length() - 1).trim();
            String[] parts = inner.split(",");

            if (parts.length != 2) {
                TaskBlocks.LOGGER.error("[TaskBlocks] move() needs 2 args: move(direction, blocks)");
                TaskBlocksNotifier.error("move() needs 2 args: move(direction, blocks)");
                return ActionResult.normal();
            }

            String direction = parts[0].trim().toLowerCase();
            double targetBlocks;
            try {
                targetBlocks = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid move() distance: " + parts[1]);
                TaskBlocksNotifier.error("Invalid move() distance: §f" + parts[1]);
                return ActionResult.normal();
            }

            walkDistanceNatural(client, direction, targetBlocks);
            return ActionResult.normal();
        }

        if (action.startsWith("move_precise(") && action.endsWith(")")) {
            String inner = action.substring(13, action.length() - 1).trim();
            String[] parts = inner.split(",");

            if (parts.length != 2) {
                TaskBlocks.LOGGER.error("[TaskBlocks] move_precise() needs 2 args: move_precise(direction, blocks)");
                TaskBlocksNotifier.error("move_precise() needs 2 args: move_precise(direction, blocks)");
                return ActionResult.normal();
            }

            String direction = parts[0].trim().toLowerCase();
            double targetBlocks;
            try {
                targetBlocks = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid move_precise() distance: " + parts[1]);
                TaskBlocksNotifier.error("Invalid move_precise() distance: §f" + parts[1]);
                return ActionResult.normal();
            }

            walkDistancePrecise(client, direction, targetBlocks);
            return ActionResult.normal();
        }

        if (action.equalsIgnoreCase("align")
                || (action.startsWith("align(") && action.endsWith(")"))) {
            String mode = "center";
            if (action.startsWith("align(")) {
                mode = action.substring(6, action.length() - 1).trim().toLowerCase();
            }
            alignToBlock(client, mode);
            return ActionResult.normal();
        }

        return null;
    }

    private static void alignToBlock(MinecraftClient client, String mode) throws InterruptedException {
        double[] pos = new double[2];
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        client.execute(() -> {
            if (client.player != null) {
                pos[0] = client.player.getX();
                pos[1] = client.player.getZ();
            }
            latch.countDown();
        });
        latch.await();

        double blockX = Math.floor(pos[0]);
        double blockZ = Math.floor(pos[1]);
        double targetX;
        double targetZ;

        switch (mode) {
            case "north" -> { targetX = blockX + 0.5; targetZ = blockZ; }
            case "south" -> { targetX = blockX + 0.5; targetZ = blockZ + 1.0; }
            case "east"  -> { targetX = blockX + 1.0; targetZ = blockZ + 0.5; }
            case "west"  -> { targetX = blockX;       targetZ = blockZ + 0.5; }
            default      -> { targetX = blockX + 0.5; targetZ = blockZ + 0.5; }
        }

        stepToward(client, targetX - pos[0], targetZ - pos[1]);
    }

    private static void walkDistanceNatural(MinecraftClient client, String direction, double targetBlocks)
            throws InterruptedException {
        net.minecraft.client.option.KeyBinding key = switch (direction) {
            case "forward"  -> client.options.forwardKey;
            case "backward" -> client.options.backKey;
            case "left"     -> client.options.leftKey;
            case "right"    -> client.options.rightKey;
            default         -> null;
        };

        if (key == null) {
            TaskBlocks.LOGGER.error("[TaskBlocks] move(): unknown direction '" + direction + "'");
            TaskBlocksNotifier.error("move(): unknown direction '" + direction + "'");
            return;
        }

        double[] startPos = new double[2];
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        client.execute(() -> {
            if (client.player != null) {
                startPos[0] = client.player.getX();
                startPos[1] = client.player.getZ();
            }
            startLatch.countDown();
        });
        startLatch.await();

        client.execute(() -> key.setPressed(true));

        long startTime = System.currentTimeMillis();
        long timeoutMs = (long) (targetBlocks * 2000) + 3000;

        while (true) {
            double[] curPos = new double[2];
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            client.execute(() -> {
                if (client.player != null) {
                    curPos[0] = client.player.getX();
                    curPos[1] = client.player.getZ();
                }
                latch.countDown();
            });
            latch.await();

            double dx = curPos[0] - startPos[0];
            double dz = curPos[1] - startPos[1];
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance >= targetBlocks) break;
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                TaskBlocks.LOGGER.warn("[TaskBlocks] move(): timed out before reaching target distance");
                TaskBlocksNotifier.warn("move(): timed out before reaching target distance");
                break;
            }
            if (!com.taskblocks.script.ScriptRunner.isRunning()) break;

            Thread.sleep(20);
        }

        client.execute(() -> key.setPressed(false));
    }

    private static void walkDistancePrecise(MinecraftClient client, String direction, double targetBlocks)
            throws InterruptedException {
        float[] yawHolder = new float[1];
        java.util.concurrent.CountDownLatch yawLatch = new java.util.concurrent.CountDownLatch(1);
        client.execute(() -> {
            if (client.player != null) yawHolder[0] = client.player.getYaw();
            yawLatch.countDown();
        });
        yawLatch.await();

        double yawRad = Math.toRadians(yawHolder[0]);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double dirX;
        double dirZ;
        switch (direction) {
            case "forward"  -> { dirX = forwardX;  dirZ = forwardZ; }
            case "backward" -> { dirX = -forwardX; dirZ = -forwardZ; }
            case "right"    -> { dirX = rightX;    dirZ = rightZ; }
            case "left"     -> { dirX = -rightX;   dirZ = -rightZ; }
            default -> {
                TaskBlocks.LOGGER.error("[TaskBlocks] move_precise(): unknown direction '" + direction + "'");
                TaskBlocksNotifier.error("move_precise(): unknown direction '" + direction + "'");
                return;
            }
        }

        stepToward(client, dirX * targetBlocks, dirZ * targetBlocks);
    }

    private static void stepToward(MinecraftClient client, double dx, double dz) throws InterruptedException {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.001) return;

        int steps = Math.max(1, (int) Math.ceil(distance / 0.2));
        double stepX = dx / steps;
        double stepZ = dz / steps;

        for (int i = 0; i < steps; i++) {
            if (Thread.currentThread().isInterrupted()) break;
            java.util.concurrent.CountDownLatch stepLatch = new java.util.concurrent.CountDownLatch(1);
            client.execute(() -> {
                if (client.player != null) {
                    client.player.setPosition(
                        client.player.getX() + stepX,
                        client.player.getY(),
                        client.player.getZ() + stepZ);
                }
                stepLatch.countDown();
            });
            stepLatch.await();
            Thread.sleep(50);
        }
    }
}