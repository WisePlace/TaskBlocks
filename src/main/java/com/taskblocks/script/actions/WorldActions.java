package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class WorldActions {

    public static void register() {
        ActionRegistry.register(WorldActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx)
            throws InterruptedException {
        MinecraftClient client = MinecraftClient.getInstance();

        // ============================================================
        // block_place — place held block on targeted surface
        // ============================================================
        if (action.equalsIgnoreCase("block_place")) {
            client.execute(() -> {
                if (client.player != null
                        && client.interactionManager != null
                        && client.crosshairTarget instanceof BlockHitResult hit) {
                    client.interactionManager.interactBlock(
                        client.player, Hand.MAIN_HAND, hit);
                }
            });
            Thread.sleep(100);
            return ActionResult.normal();
        }

        // ============================================================
        // block_break — hold left click until targeted block breaks
        //   (default 30 second timeout)
        // block_break(timeoutMs) — same, with a custom timeout instead
        //   of the default 30 seconds — useful when mining through
        //   unpredictable terrain, so hitting bedrock or another
        //   unbreakable block doesn't stall the script for the full
        //   30 seconds every time
        // varName=block_break / varName=block_break(timeoutMs) — same,
        //   capturing "true"/"false" into varName depending on whether
        //   the block actually broke before the timeout, so a script
        //   can detect and react to an unbreakable block instead of
        //   blindly walking into it afterward
        // ============================================================
        if (action.equalsIgnoreCase("block_break")
                || (action.startsWith("block_break(") && action.endsWith(")"))
                || action.toLowerCase().contains("=block_break")) {

            String varName = null;
            String rest = action;

            int eqIdx = action.toLowerCase().indexOf("=block_break");
            if (eqIdx > 0) {
                varName = action.substring(0, eqIdx).trim();
                rest = action.substring(eqIdx + 1);
            }

            long timeout = 30_000L;
            if (rest.startsWith("block_break(") && rest.endsWith(")")) {
                String inner = rest.substring(12, rest.length() - 1).trim();
                if (!inner.isEmpty()) {
                    try {
                        timeout = Long.parseLong(inner);
                    } catch (NumberFormatException e) {
                        TaskBlocks.LOGGER.error("[TaskBlocks] Invalid block_break() timeout: " + inner);
                        TaskBlocksNotifier.error("Invalid block_break() timeout: §f" + inner);
                    }
                }
            } else if (!rest.equalsIgnoreCase("block_break")) {
                return null;
            }

            BlockPos[] targetPos = new BlockPos[1];
            client.execute(() -> {
                if (client.crosshairTarget instanceof BlockHitResult hit) {
                    targetPos[0] = hit.getBlockPos();
                }
            });
            Thread.sleep(50);

            if (targetPos[0] == null) {
                TaskBlocks.LOGGER.warn("[TaskBlocks] block_break: not targeting a block");
                TaskBlocksNotifier.warn("block_break: not targeting a block");
                if (varName != null) ctx.variables.put(varName, "false");
                return ActionResult.normal();
            }

            client.execute(() -> client.options.attackKey.setPressed(true));

            long startTime = System.currentTimeMillis();
            boolean brokeSuccessfully = false;

            while (System.currentTimeMillis() - startTime < timeout) {
                Thread.sleep(50);
                boolean[] broken = {false};
                client.execute(() -> {
                    if (client.world != null) {
                        BlockState state = client.world.getBlockState(targetPos[0]);
                        broken[0] = state.isAir();
                    }
                });
                Thread.sleep(20);
                if (broken[0]) {
                    brokeSuccessfully = true;
                    break;
                }
            }

            client.execute(() -> client.options.attackKey.setPressed(false));
            Thread.sleep(50);

            if (!brokeSuccessfully) {
                TaskBlocks.LOGGER.warn("[TaskBlocks] block_break: timed out before the block broke");
                TaskBlocksNotifier.warn("block_break: timed out before the block broke");
            }

            if (varName != null) {
                ctx.variables.put(varName, String.valueOf(brokeSuccessfully));
            }

            return ActionResult.normal();
        }

        // ============================================================
        // item_use — use item until fully consumed
        // ============================================================
        if (action.equalsIgnoreCase("item_use")) {
            int[] initialCount = {0};
            client.execute(() -> {
                if (client.player != null)
                    initialCount[0] = client.player.getMainHandStack().getCount();
            });
            Thread.sleep(30);

            client.execute(() -> {
                if (client.player != null && client.interactionManager != null) {
                    client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                    client.options.useKey.setPressed(true);
                }
            });

            long startTime = System.currentTimeMillis();
            long timeout   = 10_000L;

            while (System.currentTimeMillis() - startTime < timeout) {
                Thread.sleep(50);
                boolean[] done = {false};
                client.execute(() -> {
                    if (client.player != null) {
                        int currentCount = client.player.getMainHandStack().getCount();
                        boolean countChanged = currentCount < initialCount[0]
                            || client.player.getMainHandStack().isEmpty();
                        boolean notUsingItem = !client.player.isUsingItem();
                        done[0] = countChanged || (notUsingItem
                            && System.currentTimeMillis() - startTime > 500);
                    }
                });
                Thread.sleep(20);
                if (done[0]) break;
            }

            client.execute(() -> {
                if (client.player != null && client.interactionManager != null) {
                    client.options.useKey.setPressed(false);
                    client.interactionManager.stopUsingItem(client.player);
                }
            });
            Thread.sleep(50);
            return ActionResult.normal();
        }

        // ============================================================
        // block_pick — middle click to pick block (creative mode)
        // ============================================================
        if (action.equalsIgnoreCase("block_pick")) {
            client.execute(() -> {
                if (client.player != null
                        && client.interactionManager != null
                        && client.crosshairTarget instanceof BlockHitResult hit) {
                    client.interactionManager.pickItemFromBlock(hit.getBlockPos(), false);
                }
            });
            Thread.sleep(80);
            return ActionResult.normal();
        }

        // ============================================================
        // wait_until_on_ground — pause until player lands
        // ============================================================
        if (action.equalsIgnoreCase("wait_until_on_ground")) {
            long startTime = System.currentTimeMillis();
            long timeout   = 10_000L;
            Thread.sleep(100);
            while (System.currentTimeMillis() - startTime < timeout) {
                boolean[] onGround = {false};
                client.execute(() -> {
                    if (client.player != null)
                        onGround[0] = client.player.isOnGround();
                });
                Thread.sleep(50);
                if (onGround[0]) break;
            }
            return ActionResult.normal();
        }

        // ============================================================
        // screen_close — close any open screen
        // ============================================================
        if (action.equalsIgnoreCase("screen_close")) {
            client.execute(() -> {
                if (client.currentScreen != null)
                    client.currentScreen.close();
            });
            Thread.sleep(50);
            return ActionResult.normal();
        }

        // ============================================================
        // screen_click(slot) or screen_click(slot, left|right|middle)
        // ============================================================
        if (action.startsWith("screen_click(") && action.endsWith(")")) {
            String inner = action.substring(13, action.length() - 1).trim();
            String[] parts = inner.split(",");
            try {
                int slot   = Integer.parseInt(parts[0].trim());
                int button = 0;
                if (parts.length >= 2) {
                    button = switch (parts[1].trim().toLowerCase()) {
                        case "right"  -> 1;
                        case "middle" -> 2;
                        default       -> 0;
                    };
                }
                final int fs = slot, fb = button;
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            fs, fb, SlotActionType.PICKUP, client.player);
                });
                Thread.sleep(100);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid screen_click args: " + inner);
                TaskBlocksNotifier.error("Invalid screen_click args: §f" + inner);
            }
            return ActionResult.normal();
        }

        // ============================================================
        // screen_shift_click(slot) — shift+click to move entire stack
        // ============================================================
        if (action.startsWith("screen_shift_click(") && action.endsWith(")")) {
            String inner = action.substring(19, action.length() - 1).trim();
            try {
                int slot = Integer.parseInt(inner.trim());
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            slot, 0, SlotActionType.QUICK_MOVE, client.player);
                });
                Thread.sleep(100);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid screen_shift_click args: " + inner);
                TaskBlocksNotifier.error("Invalid screen_shift_click args: §f" + inner);
            }
            return ActionResult.normal();
        }

        // ============================================================
        // screen_move(fromSlot, toSlot) — move item between slots
        // ============================================================
        if (action.startsWith("screen_move(") && action.endsWith(")")) {
            String inner = action.substring(12, action.length() - 1).trim();
            String[] parts = inner.split(",");
            if (parts.length == 2) {
                try {
                    int from = Integer.parseInt(parts[0].trim());
                    int to   = Integer.parseInt(parts[1].trim());
                    client.execute(() -> {
                        if (client.player != null && client.player.currentScreenHandler != null)
                            client.interactionManager.clickSlot(
                                client.player.currentScreenHandler.syncId,
                                from, 0, SlotActionType.PICKUP, client.player);
                    });
                    Thread.sleep(100);
                    client.execute(() -> {
                        if (client.player != null && client.player.currentScreenHandler != null)
                            client.interactionManager.clickSlot(
                                client.player.currentScreenHandler.syncId,
                                to, 0, SlotActionType.PICKUP, client.player);
                    });
                    Thread.sleep(100);
                } catch (NumberFormatException e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Invalid screen_move args: " + inner);
                    TaskBlocksNotifier.error("Invalid screen_move args: §f" + inner);
                }
            } else {
                TaskBlocks.LOGGER.error("[TaskBlocks] screen_move needs 2 args: screen_move(from, to)");
                TaskBlocksNotifier.error("screen_move needs 2 args: screen_move(from, to)");
            }
            return ActionResult.normal();
        }

        // ============================================================
        // screen_move_stack(fromSlot, toSlot) — move entire stack
        // ============================================================
        if (action.startsWith("screen_move_stack(") && action.endsWith(")")) {
            String inner = action.substring(18, action.length() - 1).trim();
            String[] parts = inner.split(",");
            if (parts.length == 2) {
                try {
                    int from = Integer.parseInt(parts[0].trim());
                    int to   = Integer.parseInt(parts[1].trim());
                    client.execute(() -> {
                        if (client.player != null && client.player.currentScreenHandler != null)
                            client.interactionManager.clickSlot(
                                client.player.currentScreenHandler.syncId,
                                from, 0, SlotActionType.PICKUP, client.player);
                    });
                    Thread.sleep(100);
                    client.execute(() -> {
                        if (client.player != null && client.player.currentScreenHandler != null)
                            client.interactionManager.clickSlot(
                                client.player.currentScreenHandler.syncId,
                                to, 0, SlotActionType.PICKUP, client.player);
                    });
                    Thread.sleep(100);
                } catch (NumberFormatException e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Invalid screen_move_stack args: " + inner);
                    TaskBlocksNotifier.error("Invalid screen_move_stack args: §f" + inner);
                }
            } else {
                TaskBlocks.LOGGER.error("[TaskBlocks] screen_move_stack needs 2 args");
                TaskBlocksNotifier.error("screen_move_stack needs 2 args");
            }
            return ActionResult.normal();
        }

        // ============================================================
        // screen_select_all(slot) — collect all similar items to slot
        // ============================================================
        if (action.startsWith("screen_select_all(") && action.endsWith(")")) {
            String inner = action.substring(18, action.length() - 1).trim();
            try {
                int slot = Integer.parseInt(inner.trim());
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            slot, 0, SlotActionType.PICKUP, client.player);
                });
                Thread.sleep(100);
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            slot, 0, SlotActionType.PICKUP_ALL, client.player);
                });
                Thread.sleep(100);
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            slot, 0, SlotActionType.PICKUP, client.player);
                });
                Thread.sleep(100);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid screen_select_all args: " + inner);
                TaskBlocksNotifier.error("Invalid screen_select_all args: §f" + inner);
            }
            return ActionResult.normal();
        }

        // ============================================================
        // screen_drop(slot) — drop item from slot out of inventory
        // ============================================================
        if (action.startsWith("screen_drop(") && action.endsWith(")")) {
            String inner = action.substring(12, action.length() - 1).trim();
            try {
                int slot = Integer.parseInt(inner.trim());
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            slot, 0, SlotActionType.THROW, client.player);
                });
                Thread.sleep(100);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid screen_drop args: " + inner);
                TaskBlocksNotifier.error("Invalid screen_drop args: §f" + inner);
            }
            return ActionResult.normal();
        }

        // ============================================================
        // get_block_below(varName) — block directly under the player's feet
        // ============================================================
        if (action.startsWith("get_block_below(") && action.endsWith(")")) {
            String varName = action.substring(16, action.length() - 1).trim();
            String[] result = new String[1];

            client.execute(() -> {
                if (client.player != null && client.world != null) {
                    BlockPos pos = client.player.getBlockPos().down();
                    BlockState state = client.world.getBlockState(pos);
                    net.minecraft.util.Identifier id =
                        net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
                    result[0] = id.toString();
                }
            });
            Thread.sleep(50);

            if (result[0] != null) {
                ctx.variables.put(varName, result[0]);
            } else {
                TaskBlocks.LOGGER.warn("[TaskBlocks] get_block_below: could not read block");
                TaskBlocksNotifier.warn("get_block_below: could not read block");
            }
            return ActionResult.normal();
        }

        // ============================================================
        // get_block_target(varName) — block the crosshair is currently on
        // ============================================================
        if (action.startsWith("get_block_target(") && action.endsWith(")")) {
            String varName = action.substring(17, action.length() - 1).trim();
            String[] result = new String[1];

            client.execute(() -> {
                if (client.world != null && client.crosshairTarget instanceof BlockHitResult hit) {
                    BlockState state = client.world.getBlockState(hit.getBlockPos());
                    net.minecraft.util.Identifier id =
                        net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
                    result[0] = id.toString();
                }
            });
            Thread.sleep(50);

            if (result[0] != null) {
                ctx.variables.put(varName, result[0]);
            } else {
                TaskBlocks.LOGGER.warn("[TaskBlocks] get_block_target: not targeting a block");
                TaskBlocksNotifier.warn("get_block_target: not targeting a block");
            }
            return ActionResult.normal();
        }

        return null;
    }
}