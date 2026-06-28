package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;

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
        // Polls every 50ms to detect when block becomes air
        // Times out after 30 seconds as safety net
        // ============================================================
        if (action.equalsIgnoreCase("block_break")) {
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
                return ActionResult.normal();
            }

            client.execute(() -> client.options.attackKey.setPressed(true));

            long startTime = System.currentTimeMillis();
            long timeout   = 30_000L;

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
                if (broken[0]) break;
            }

            client.execute(() -> client.options.attackKey.setPressed(false));
            Thread.sleep(50);
            return ActionResult.normal();
        }

        // ============================================================
        // item_use — use item until fully consumed
        // Holds right click and waits until the item is used
        // (food eaten, potion drunk, etc.)
        // Detects completion by watching item stack or use state
        // Times out after 10 seconds
        // ============================================================
        if (action.equalsIgnoreCase("item_use")) {
            // Get current item count before using
            int[] initialCount = {0};
            client.execute(() -> {
                if (client.player != null)
                    initialCount[0] = client.player.getMainHandStack().getCount();
            });
            Thread.sleep(30);

            // Start holding use key
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
                        // Done if: item count decreased, stack is empty,
                        // or player is no longer using an item
                        int currentCount = client.player.getMainHandStack().getCount();
                        boolean countChanged = currentCount < initialCount[0]
                            || client.player.getMainHandStack().isEmpty();
                        boolean notUsingItem = !client.player.isUsingItem();
                        // We wait until they stop using AND something changed
                        done[0] = countChanged || (notUsingItem
                            && System.currentTimeMillis() - startTime > 500);
                    }
                });
                Thread.sleep(20);
                if (done[0]) break;
            }

            // Release use key
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
        // Times out after 10 seconds
        // ============================================================
        if (action.equalsIgnoreCase("wait_until_on_ground")) {
            long startTime = System.currentTimeMillis();
            long timeout   = 10_000L;
            // Small initial delay so we don't catch "on ground" before the jump registers
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
                    // Pick up from source
                    client.execute(() -> {
                        if (client.player != null && client.player.currentScreenHandler != null)
                            client.interactionManager.clickSlot(
                                client.player.currentScreenHandler.syncId,
                                from, 0, SlotActionType.PICKUP, client.player);
                    });
                    Thread.sleep(100);
                    // Place at destination
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
        // Picks up the item from slot first, then double-clicks to
        // collect all matching items, then places back
        // ============================================================
        if (action.startsWith("screen_select_all(") && action.endsWith(")")) {
            String inner = action.substring(18, action.length() - 1).trim();
            try {
                int slot = Integer.parseInt(inner.trim());
                // Step 1: pick up item from slot
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            slot, 0, SlotActionType.PICKUP, client.player);
                });
                Thread.sleep(100);
                // Step 2: double click (PICKUP_ALL) to collect all similar
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            slot, 0, SlotActionType.PICKUP_ALL, client.player);
                });
                Thread.sleep(100);
                // Step 3: place the collected stack back into the slot
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

        return null;
    }
}