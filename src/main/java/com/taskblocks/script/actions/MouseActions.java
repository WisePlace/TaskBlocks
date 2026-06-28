package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.SlotActionType;

public class MouseActions {

    public static void register() {
        ActionRegistry.register(MouseActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx)
            throws InterruptedException {
        MinecraftClient client = MinecraftClient.getInstance();

        // ============================================================
        // MOUSE BUTTONS
        // ============================================================

        // left_click — instant left click in world
        if (action.equalsIgnoreCase("left_click")) {
            client.execute(() -> client.options.attackKey.setPressed(true));
            Thread.sleep(80);
            client.execute(() -> client.options.attackKey.setPressed(false));
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // left_click_press — hold left click
        if (action.equalsIgnoreCase("left_click_press")) {
            client.execute(() -> client.options.attackKey.setPressed(true));
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // left_click_release — release left click
        if (action.equalsIgnoreCase("left_click_release")) {
            client.execute(() -> client.options.attackKey.setPressed(false));
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // right_click — instant right click in world
        if (action.equalsIgnoreCase("right_click")) {
            client.execute(() -> client.options.useKey.setPressed(true));
            Thread.sleep(80);
            client.execute(() -> client.options.useKey.setPressed(false));
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // right_click_press — hold right click
        if (action.equalsIgnoreCase("right_click_press")) {
            client.execute(() -> client.options.useKey.setPressed(true));
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // right_click_release — release right click
        if (action.equalsIgnoreCase("right_click_release")) {
            client.execute(() -> client.options.useKey.setPressed(false));
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // ============================================================
        // HOTBAR
        // scroll_slot(1-9) — select hotbar slot
        // ============================================================
        if (action.startsWith("scroll_slot(") && action.endsWith(")")) {
            String inner = action.substring(12, action.length() - 1).trim();
            try {
                int slot = Integer.parseInt(inner) - 1; // 1-based to 0-based
                if (slot < 0 || slot > 8) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] scroll_slot: must be 1-9");
                    TaskBlocksNotifier.error("scroll_slot: must be 1-9");
                    return ActionResult.normal();
                }
                client.execute(() -> {
                    if (client.player != null)
                        client.player.getInventory().setSelectedSlot(slot);
                });
                Thread.sleep(30);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid scroll_slot value: " + inner);
                TaskBlocksNotifier.error("Invalid scroll_slot value: §f" + inner);
            }
            return ActionResult.normal();
        }

        // ============================================================
        // SCREEN ACTIONS
        // All actions that interact with open GUI screens
        // ============================================================

        // screen_close — closes any open screen
        if (action.equalsIgnoreCase("screen_close")) {
            client.execute(() -> {
                if (client.currentScreen != null)
                    client.currentScreen.close();
            });
            Thread.sleep(50);
            return ActionResult.normal();
        }

        // screen_click(slot) or screen_click(slot, left|right|middle)
        // Left click on a slot — picks up or places item
        if (action.startsWith("screen_click(") && action.endsWith(")")) {
            String inner = action.substring(13, action.length() - 1).trim();
            String[] parts = inner.split(",");
            try {
                int slot   = Integer.parseInt(parts[0].trim());
                int button = 0; // default: left
                if (parts.length >= 2) {
                    button = switch (parts[1].trim().toLowerCase()) {
                        case "right"  -> 1;
                        case "middle" -> 2;
                        default       -> 0;
                    };
                }
                final int finalSlot   = slot;
                final int finalButton = button;
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            finalSlot, finalButton,
                            SlotActionType.PICKUP,
                            client.player);
                });
                Thread.sleep(100);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid screen_click args: " + inner);
                TaskBlocksNotifier.error("Invalid screen_click args: §f" + inner);
            }
            return ActionResult.normal();
        }

        // screen_shift_click(slot) — shift+click a slot
        // Moves entire stack to the other inventory section
        if (action.startsWith("screen_shift_click(") && action.endsWith(")")) {
            String inner = action.substring(19, action.length() - 1).trim();
            try {
                int slot = Integer.parseInt(inner.trim());
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            slot, 0,
                            SlotActionType.QUICK_MOVE,
                            client.player);
                });
                Thread.sleep(100);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid screen_shift_click args: " + inner);
                TaskBlocksNotifier.error("Invalid screen_shift_click args: §f" + inner);
            }
            return ActionResult.normal();
        }

        // screen_move(fromSlot, toSlot) — move item from one slot to another
        // Does: left click fromSlot (pick up), left click toSlot (place)
        if (action.startsWith("screen_move(") && action.endsWith(")")) {
            String inner = action.substring(12, action.length() - 1).trim();
            String[] parts = inner.split(",");
            if (parts.length == 2) {
                try {
                    int fromSlot = Integer.parseInt(parts[0].trim());
                    int toSlot   = Integer.parseInt(parts[1].trim());
                    int syncId   = client.player != null
                        ? client.player.currentScreenHandler.syncId : 0;

                    // Pick up from source
                    client.execute(() -> {
                        if (client.player != null && client.player.currentScreenHandler != null)
                            client.interactionManager.clickSlot(
                                syncId, fromSlot, 0,
                                SlotActionType.PICKUP,
                                client.player);
                    });
                    Thread.sleep(100);

                    // Place at destination
                    client.execute(() -> {
                        if (client.player != null && client.player.currentScreenHandler != null)
                            client.interactionManager.clickSlot(
                                syncId, toSlot, 0,
                                SlotActionType.PICKUP,
                                client.player);
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

        // screen_move_stack(fromSlot, toSlot) — move entire stack one item at a time
        // Uses right-click drag to split, or just shift-click for efficiency
        // Actually uses QUICK_MOVE (shift-click behavior) from source then places at dest
        if (action.startsWith("screen_move_stack(") && action.endsWith(")")) {
            String inner = action.substring(18, action.length() - 1).trim();
            String[] parts = inner.split(",");
            if (parts.length == 2) {
                try {
                    int fromSlot = Integer.parseInt(parts[0].trim());
                    int toSlot   = Integer.parseInt(parts[1].trim());
                    int syncId   = client.player != null
                        ? client.player.currentScreenHandler.syncId : 0;

                    // Pick up entire stack from source
                    client.execute(() -> {
                        if (client.player != null && client.player.currentScreenHandler != null)
                            client.interactionManager.clickSlot(
                                syncId, fromSlot, 0,
                                SlotActionType.PICKUP,
                                client.player);
                    });
                    Thread.sleep(100);

                    // Place entire stack at destination
                    client.execute(() -> {
                        if (client.player != null && client.player.currentScreenHandler != null)
                            client.interactionManager.clickSlot(
                                syncId, toSlot, 0,
                                SlotActionType.PICKUP,
                                client.player);
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

        // screen_drop(slot) — drop item from slot out of inventory
        // Slot -999 = click outside = drop held item
        if (action.startsWith("screen_drop(") && action.endsWith(")")) {
            String inner = action.substring(12, action.length() - 1).trim();
            try {
                int slot = Integer.parseInt(inner.trim());
                client.execute(() -> {
                    if (client.player != null && client.player.currentScreenHandler != null)
                        client.interactionManager.clickSlot(
                            client.player.currentScreenHandler.syncId,
                            slot, 0,
                            SlotActionType.THROW,
                            client.player);
                });
                Thread.sleep(100);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid screen_drop args: " + inner);
                TaskBlocksNotifier.error("Invalid screen_drop args: §f" + inner);
            }
            return ActionResult.normal();
        }

        return null; // not handled
    }
}