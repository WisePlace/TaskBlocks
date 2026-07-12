package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public class VariableActions {

    private static final java.util.Random RANDOM = new java.util.Random();

    public static void register() {
        ActionRegistry.register(VariableActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx)
            throws InterruptedException {
        MinecraftClient client = MinecraftClient.getInstance();

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
            } else {
                TaskBlocks.LOGGER.error("[TaskBlocks] Could not resolve get(" + source + ")");
                TaskBlocksNotifier.error("Could not resolve get(" + source + ")");
            }

            return ActionResult.normal();
        }

        if (action.contains("=random(") && action.endsWith(")")) {
            int eqIdx = action.indexOf("=random(");
            String varName = action.substring(0, eqIdx).trim();
            String inner = action.substring(eqIdx + 8, action.length() - 1).trim();

            if (varName.isEmpty()) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Variable name cannot be empty");
                TaskBlocksNotifier.error("Variable name cannot be empty");
                return ActionResult.normal();
            }

            String[] parts = inner.split(",");
            if (parts.length != 2) {
                TaskBlocks.LOGGER.error("[TaskBlocks] random() needs 2 args: random(min, max)");
                TaskBlocksNotifier.error("random() needs 2 args: random(min, max)");
                return ActionResult.normal();
            }

            try {
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                if (min > max) {
                    int tmp = min;
                    min = max;
                    max = tmp;
                }
                int value = min + RANDOM.nextInt(max - min + 1);
                ctx.variables.put(varName, String.valueOf(value));
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid random() args: " + inner);
                TaskBlocksNotifier.error("Invalid random() args: §f" + inner);
            }

            return ActionResult.normal();
        }

        return null;
    }

    private static String resolveGet(MinecraftClient client, String source)
            throws InterruptedException {
        String[] result = {null};

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

        if (source.startsWith("enchant(") && source.endsWith(")")) {
            String inner = source.substring(8, source.length() - 1).trim();
            String[] parts = inner.split(",");
            String enchantName = parts[0].trim();
            Integer slot = null;
            if (parts.length >= 2) {
                try {
                    slot = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Invalid enchant() slot: " + parts[1]);
                    TaskBlocksNotifier.error("Invalid enchant() slot: §f" + parts[1]);
                    return null;
                }
            }

            Integer finalSlot = slot;
            client.execute(() -> {
                if (client.player == null || client.world == null) return;
                ItemStack stack = finalSlot != null
                    ? client.player.getInventory().getStack(finalSlot)
                    : client.player.getMainHandStack();
                if (stack.isEmpty()) {
                    result[0] = "0";
                    return;
                }
                Identifier id = enchantName.contains(":")
                    ? Identifier.of(enchantName)
                    : Identifier.of("minecraft", enchantName);
                java.util.Optional<net.minecraft.registry.Registry<Enchantment>> registryOpt =
                    client.world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
                if (registryOpt.isEmpty()) {
                    result[0] = "0";
                    return;
                }
                java.util.Optional<RegistryEntry.Reference<Enchantment>> entry =
                    registryOpt.get().getEntry(id);
                if (entry.isEmpty()) {
                    result[0] = "0";
                    return;
                }
                result[0] = String.valueOf(EnchantmentHelper.getLevel(entry.get(), stack));
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("speed")) {
            client.execute(() -> {
                if (client.player != null) {
                    net.minecraft.util.math.Vec3d velocity = client.player.getVelocity();
                    double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
                    result[0] = String.format(java.util.Locale.ROOT, "%.2f", speed);
                }
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("health")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.valueOf(client.player.getHealth());
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("max_health")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.valueOf(client.player.getMaxHealth());
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("hunger")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.valueOf(client.player.getHungerManager().getFoodLevel());
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("saturation")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.valueOf(
                        client.player.getHungerManager().getSaturationLevel());
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("position")) {
            client.execute(() -> {
                if (client.player != null) {
                    double x = client.player.getX();
                    double y = client.player.getY();
                    double z = client.player.getZ();
                    result[0] = String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", x, y, z);
                }
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("pos_x")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.format(java.util.Locale.ROOT, "%.2f", client.player.getX());
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("pos_y")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.format(java.util.Locale.ROOT, "%.2f", client.player.getY());
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("pos_z")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.format(java.util.Locale.ROOT, "%.2f", client.player.getZ());
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("look_yaw")) {
            client.execute(() -> {
                if (client.player != null) {
                    float yaw = client.player.getYaw() % 360f;
                    if (yaw > 180f) yaw -= 360f;
                    if (yaw < -180f) yaw += 360f;
                    result[0] = String.format(java.util.Locale.ROOT, "%.2f", yaw);
                }
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("look_pitch")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.format(java.util.Locale.ROOT, "%.2f", client.player.getPitch());
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("selected_slot")) {
            client.execute(() -> {
                if (client.player != null)
                    result[0] = String.valueOf(
                        client.player.getInventory().getSelectedSlot() + 1);
            });
            Thread.sleep(30);
            return result[0];
        }

        if (source.equalsIgnoreCase("dimension")) {
            client.execute(() -> {
                if (client.player != null && client.world != null)
                    result[0] = client.world.getRegistryKey().getValue().toString();
            });
            Thread.sleep(30);
            return result[0];
        }

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