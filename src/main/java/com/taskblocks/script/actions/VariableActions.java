package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public class VariableActions {

    public static void register() {
        ActionRegistry.register(VariableActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx)
            throws InterruptedException {
        MinecraftClient client = MinecraftClient.getInstance();

        // ============================================================
        // Variable assignment: varName=get(source)
        // Examples:
        //   var1=get(item_id(9))
        //   myHealth=get(health)
        //   pos=get(position)
        // ============================================================
        if (action.contains("=get(") && action.endsWith(")")) {
            int eqIdx = action.indexOf("=get(");
            String varName = action.substring(0, eqIdx).trim();
            String source  = action.substring(eqIdx + 5, action.length() - 1).trim();

            if (varName.isEmpty()) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Variable name cannot be empty");
                TaskBlocksNotifier.error("Variable name cannot be empty");
                return ActionResult.normal();
            }

            String value = resolveGet(client, source);

            if (value != null) {
                ctx.variables.put(varName, value);
                TaskBlocks.LOGGER.info("[TaskBlocks] VAR SET: " + varName + " = " + value + " | map size: " + ctx.variables.size());
            } else {
                TaskBlocks.LOGGER.error("[TaskBlocks] Could not resolve get(" + source + ")");
                TaskBlocksNotifier.error("Could not resolve get(" + source + ")");
            }

            return ActionResult.normal();
        }

        return null;
    }

    // ============================================================
    // Resolves a get() source to a string value
    // ============================================================
    private static String resolveGet(MinecraftClient client, String source)
            throws InterruptedException {
        String[] result = {null};

        // --- item_id(slot) — registry ID of item in slot ---
        if (source.startsWith("item_id(") && source.endsWith(")")) {
            int slot = parseSlot(source, "item_id(");
            if (slot < 0) return null;
            client.execute(() -> {
                if (client.player != null) {
                    ItemStack stack = client.player.getInventory().getStack(slot);
                    if (stack.isEmpty()) {
                        result[0] = "air";
                    } else {
                        result[0] = Registries.ITEM.getId(stack.getItem()).toString();
                    }
                }
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- item_count(slot) — stack count in slot ---
        if (source.startsWith("item_count(") && source.endsWith(")")) {
            int slot = parseSlot(source, "item_count(");
            if (slot < 0) return null;
            client.execute(() -> {
                if (client.player != null) {
                    ItemStack stack = client.player.getInventory().getStack(slot);
                    result[0] = String.valueOf(stack.getCount());
                }
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- item_durability(slot) — remaining durability in slot ---
        if (source.startsWith("item_durability(") && source.endsWith(")")) {
            int slot = parseSlot(source, "item_durability(");
            if (slot < 0) return null;
            client.execute(() -> {
                if (client.player != null) {
                    ItemStack stack = client.player.getInventory().getStack(slot);
                    if (stack.isEmpty() || !stack.isDamageable()) {
                        result[0] = "-1";
                    } else {
                        int remaining = stack.getMaxDamage() - stack.getDamage();
                        result[0] = String.valueOf(remaining);
                    }
                }
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- item_max_durability(slot) — max durability in slot ---
        if (source.startsWith("item_max_durability(") && source.endsWith(")")) {
            int slot = parseSlot(source, "item_max_durability(");
            if (slot < 0) return null;
            client.execute(() -> {
                if (client.player != null) {
                    ItemStack stack = client.player.getInventory().getStack(slot);
                    if (stack.isEmpty() || !stack.isDamageable()) {
                        result[0] = "-1";
                    } else {
                        result[0] = String.valueOf(stack.getMaxDamage());
                    }
                }
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- health — current player health ---
        if (source.equalsIgnoreCase("health")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.valueOf(client.player.getHealth());
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- max_health — player max health ---
        if (source.equalsIgnoreCase("max_health")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.valueOf(client.player.getMaxHealth());
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- hunger — current food level ---
        if (source.equalsIgnoreCase("hunger")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.valueOf(client.player.getHungerManager().getFoodLevel());
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- saturation — current food saturation ---
        if (source.equalsIgnoreCase("saturation")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.valueOf(
                        client.player.getHungerManager().getSaturationLevel());
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- position — current X Y Z as "x,y,z" ---
        if (source.equalsIgnoreCase("position")) {
            client.execute(() -> {
                if (client.player != null) {
                    double x = client.player.getX();
                    double y = client.player.getY();
                    double z = client.player.getZ();
                    result[0] = String.format("%.2f,%.2f,%.2f", x, y, z);
                }
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- pos_x / pos_y / pos_z — individual coordinates ---
        if (source.equalsIgnoreCase("pos_x")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.format("%.2f", client.player.getX());
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("pos_y")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.format("%.2f", client.player.getY());
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("pos_z")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.format("%.2f", client.player.getZ());
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- look_yaw — current yaw (horizontal angle) ---
        if (source.equalsIgnoreCase("look_yaw")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.format("%.2f", client.player.getYaw());
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- look_pitch — current pitch (vertical angle) ---
        if (source.equalsIgnoreCase("look_pitch")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.format("%.2f", client.player.getPitch());
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- selected_slot — current hotbar slot (1-based) ---
        if (source.equalsIgnoreCase("selected_slot")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.valueOf(
                        client.player.getInventory().getSelectedSlot() + 1);
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- dimension — current dimension ID ---
        if (source.equalsIgnoreCase("dimension")) {
            client.execute(() -> {
                if (client.player != null && client.world != null)
                    result[0] = client.world.getRegistryKey().getValue().toString();
            });
            Thread.sleep(30);
            return result[0];
        }

        // --- game_time — current world time in ticks ---
        if (source.equalsIgnoreCase("game_time")) {
            client.execute(() -> {
                if (client.world != null)
                    result[0] = String.valueOf(client.world.getTime());
            });
            Thread.sleep(30);
            return result[0];
        }

        TaskBlocks.LOGGER.error("[TaskBlocks] Unknown get() source: " + source);
        return null;
    }

    private static int parseSlot(String source, String prefix) {
        try {
            String inner = source.substring(prefix.length(), source.length() - 1).trim();
            return Integer.parseInt(inner);
        } catch (NumberFormatException e) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Invalid slot in: " + source);
            TaskBlocksNotifier.error("Invalid slot in: " + source);
            return -1;
        }
    }
}