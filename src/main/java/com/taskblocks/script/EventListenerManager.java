package com.taskblocks.script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;
import com.taskblocks.script.actions.ActionContext;
import com.taskblocks.script.actions.ActionRegistry;
import com.taskblocks.script.actions.ActionResult;
import com.taskblocks.script.actions.ConditionEvaluator;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;

import org.lwjgl.glfw.GLFW;

// ============================================================
// Registry + evaluator for listen() event listeners.
//
// tick() is called every game tick from ScriptRunner.tick(), which
// already runs on the client thread — condition checks read player
// state directly, no client.execute()/latch needed.
//
// Firing an action is handed off to its own short-lived daemon
// thread so a blocking listener action (e.g. block_break) can't
// freeze the game, and so it can fire even while the main script
// thread is itself mid-action. Flow control (goto/end) from a fired
// action is routed back to ScriptRunner via requestListenerControl(),
// which the main script loop picks up between lines.
// ============================================================
public class EventListenerManager {

    private static final Map<String, EventListener> listeners = new ConcurrentHashMap<>();

    public static void register(String id, String condition, String action, int intervalTicks) {
        listeners.put(id, new EventListener(id, condition, action, intervalTicks));
    }

    public static boolean remove(String id) {
        return listeners.remove(id) != null;
    }

    public static void clear() {
        listeners.clear();
    }

    public static void tick(MinecraftClient client, long currentTick) {
        if (listeners.isEmpty() || client.player == null) return;

        for (EventListener listener : listeners.values()) {
            // Movement tracking runs every tick regardless of this
            // listener's own interval — still()/moving measure a
            // continuous duration, not a point-in-time sample.
            updateMovementTracking(listener, client);

            if (listener.lastCheckTick != -1
                    && currentTick - listener.lastCheckTick < listener.intervalTicks) {
                continue;
            }
            listener.lastCheckTick = currentTick;

            boolean result;
            try {
                result = evaluateExpression(listener.condition, listener, client);
            } catch (Exception e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Listener '" + listener.id
                    + "' condition error: " + e.getMessage());
                continue;
            }

            if (result && !listener.wasTrue) {
                listener.wasTrue = true;
                fire(listener);
            } else if (!result) {
                listener.wasTrue = false;
            }
        }
    }

    // ============================================================
    // Condition evaluation — splits on && / || (same precedence rules
    // as ConditionEvaluator) then dispatches each clause to either a
    // zero-arg keyword, a parameterized function-call condition, or
    // falls back to a pseudo-variable/script-variable comparison.
    // ============================================================

    private static boolean evaluateExpression(String condition, EventListener listener, MinecraftClient client) {
        java.util.List<String> clauses = new java.util.ArrayList<>();
        java.util.List<String> connectors = new java.util.ArrayList<>();

        String remaining = condition;
        while (true) {
            int andIdx = remaining.indexOf("&&");
            int orIdx  = remaining.indexOf("||");
            if (andIdx == -1 && orIdx == -1) {
                clauses.add(remaining.trim());
                break;
            }
            int splitIdx;
            String connector;
            if (andIdx != -1 && (orIdx == -1 || andIdx < orIdx)) {
                splitIdx = andIdx;
                connector = "&&";
            } else {
                splitIdx = orIdx;
                connector = "||";
            }
            clauses.add(remaining.substring(0, splitIdx).trim());
            connectors.add(connector);
            remaining = remaining.substring(splitIdx + 2);
        }

        boolean result = evaluateClause(clauses.get(0), listener, client);
        for (int i = 0; i < connectors.size(); i++) {
            boolean next = evaluateClause(clauses.get(i + 1), listener, client);
            result = connectors.get(i).equals("&&") ? (result && next) : (result || next);
        }
        return result;
    }

    private static final Pattern ITEM_COUNT_PATTERN = Pattern.compile(
        "^item_count\\(([^)]+)\\)\\s*(>=|<=|==|!=|=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)$",
        Pattern.CASE_INSENSITIVE);

    private static boolean evaluateClause(String clause, EventListener listener, MinecraftClient client) {
        String trimmed = clause.trim();
        String lower = trimmed.toLowerCase();

        // --- zero-arg keywords ---
        switch (lower) {
            case "inventory_full":  return isInventoryFull(client);
            case "inventory_empty": return isInventoryEmpty(client);
            case "teleported":      return checkTeleported(listener, client);
            case "hand_empty":      return client.player.getMainHandStack().isEmpty();
            case "hand_not_empty":  return !client.player.getMainHandStack().isEmpty();
            case "hand_full":       return isHandFull(client);
            case "offhand_empty":     return client.player.getOffHandStack().isEmpty();
            case "offhand_not_empty": return !client.player.getOffHandStack().isEmpty();
            case "sneaking":        return client.player.isSneaking();
            case "sprinting":       return client.player.isSprinting();
            case "moving":          return listener.ticksSinceMoved <= 1;
            case "screen_open":     return client.currentScreen != null;
            case "death":           return client.player.isDead();
            case "block_broken":    return checkBlockTransition(listener, client, false);
            case "block_placed":    return checkBlockTransition(listener, client, true);
        }

        // --- still(seconds) ---
        if (lower.startsWith("still(") && trimmed.endsWith(")")) {
            try {
                double seconds = Double.parseDouble(trimmed.substring(6, trimmed.length() - 1).trim());
                return listener.ticksSinceMoved >= seconds * 20.0;
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid still() argument: " + trimmed);
                return false;
            }
        }

        // --- entity_nearby(type, radius) ---
        if (lower.startsWith("entity_nearby(") && trimmed.endsWith(")")) {
            String inner = trimmed.substring(trimmed.indexOf('(') + 1, trimmed.length() - 1);
            String[] parts = inner.split(",");
            if (parts.length == 2) {
                try {
                    double radius = Double.parseDouble(parts[1].trim());
                    return isEntityNearby(client, parts[0].trim(), radius);
                } catch (NumberFormatException e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Invalid entity_nearby() radius: " + trimmed);
                }
            } else {
                TaskBlocks.LOGGER.error("[TaskBlocks] entity_nearby() needs 2 args: entity_nearby(type, radius)");
            }
            return false;
        }

        // --- player_nearby(radius) ---
        if (lower.startsWith("player_nearby(") && trimmed.endsWith(")")) {
            try {
                double radius = Double.parseDouble(trimmed.substring(14, trimmed.length() - 1).trim());
                return isPlayerNearby(client, radius);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid player_nearby() radius: " + trimmed);
                return false;
            }
        }

        // --- item_durability(percent) — main hand only ---
        if (lower.startsWith("item_durability(") && trimmed.endsWith(")")) {
            try {
                double percent = Double.parseDouble(trimmed.substring(17, trimmed.length() - 1).trim());
                return isDurabilityLow(client, percent);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid item_durability() argument: " + trimmed);
                return false;
            }
        }

        // --- key(keyName) — single key currently down ---
        if (lower.startsWith("key(") && trimmed.endsWith(")")) {
            String key = trimmed.substring(4, trimmed.length() - 1).trim();
            return isKeyPressed(client, key);
        }

        // --- mouse(left|right|middle) — single button currently down ---
        if (lower.startsWith("mouse(") && trimmed.endsWith(")")) {
            String button = trimmed.substring(6, trimmed.length() - 1).trim();
            return isMousePressed(client, button);
        }

        // --- item_count(itemId) >= N (and other comparators) ---
        Matcher itemCountMatch = ITEM_COUNT_PATTERN.matcher(trimmed);
        if (itemCountMatch.matches()) {
            String itemId = itemCountMatch.group(1).trim();
            String op = itemCountMatch.group(2);
            double threshold = Double.parseDouble(itemCountMatch.group(3));
            return compareNumeric(countItem(client, itemId), op, threshold);
        }

        // --- fall back: pseudo-variable / script-variable comparison ---
        Map<String, String> scriptVars = ScriptRunner.getCurrentVariables();
        Map<String, String> merged = scriptVars != null
            ? new HashMap<>(scriptVars) : new HashMap<>();
        populateLiveVariables(merged, client);
        return ConditionEvaluator.evaluate(trimmed, merged);
    }

    private static void populateLiveVariables(Map<String, String> merged, MinecraftClient client) {
        if (client.player != null) {
            merged.putIfAbsent("x", String.valueOf(client.player.getX()));
            merged.putIfAbsent("y", String.valueOf(client.player.getY()));
            merged.putIfAbsent("z", String.valueOf(client.player.getZ()));
            merged.putIfAbsent("yaw", String.valueOf(client.player.getYaw()));
            merged.putIfAbsent("pitch", String.valueOf(client.player.getPitch()));
            merged.putIfAbsent("health", String.valueOf(client.player.getHealth()));
            merged.putIfAbsent("hunger", String.valueOf(client.player.getHungerManager().getFoodLevel()));

            if (client.world != null) {
                BlockPos below = client.player.getBlockPos().down();
                merged.putIfAbsent("block_below",
                    Registries.BLOCK.getId(client.world.getBlockState(below).getBlock()).toString());
            }
        }
        if (client.world != null && client.crosshairTarget instanceof BlockHitResult hit) {
            merged.putIfAbsent("block_target",
                Registries.BLOCK.getId(client.world.getBlockState(hit.getBlockPos()).getBlock()).toString());
        }
    }

    private static boolean compareNumeric(double left, String op, double right) {
        return switch (op) {
            case ">="       -> left >= right;
            case "<="       -> left <= right;
            case "==", "="  -> left == right;
            case "!="       -> left != right;
            case ">"        -> left > right;
            case "<"        -> left < right;
            default         -> false;
        };
    }

    // Hotbar + main storage is slots 0-35 in PlayerInventory; armor and
    // offhand live past that and aren't relevant to "can this item fit".
    private static final int MAIN_INVENTORY_SIZE = 36;

    private static boolean isInventoryFull(MinecraftClient client) {
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            if (client.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private static boolean isInventoryEmpty(MinecraftClient client) {
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            if (!client.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private static boolean isHandFull(MinecraftClient client) {
        ItemStack stack = client.player.getMainHandStack();
        return !stack.isEmpty() && stack.getCount() >= stack.getMaxCount();
    }

    // Heuristic: flags a position jump too large to be normal movement
    // between two checks of this listener. Threshold loosely scales with
    // the check interval to reduce false positives from sprinting/elytra;
    // tune the listener's intervalTicks if you get false positives.
    private static boolean checkTeleported(EventListener listener, MinecraftClient client) {
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();

        if (!listener.hasBaseline) {
            listener.lastX = x;
            listener.lastY = y;
            listener.lastZ = z;
            listener.hasBaseline = true;
            return false;
        }

        double dx = x - listener.lastX;
        double dy = y - listener.lastY;
        double dz = z - listener.lastZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        listener.lastX = x;
        listener.lastY = y;
        listener.lastZ = z;

        double threshold = Math.max(8.0, listener.intervalTicks * 1.5);
        return distance > threshold;
    }

    // Updates ticksSinceMoved every game tick (see tick()), independent
    // of the listener's own check interval, so still(N)/moving reflect
    // a genuine elapsed-time measurement rather than a single sample.
    private static final double MOVEMENT_EPSILON = 0.01;

    private static void updateMovementTracking(EventListener listener, MinecraftClient client) {
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();

        if (!listener.moveTrackBaseline) {
            listener.moveTrackX = x;
            listener.moveTrackY = y;
            listener.moveTrackZ = z;
            listener.moveTrackBaseline = true;
            listener.ticksSinceMoved = 0;
            return;
        }

        double dx = x - listener.moveTrackX;
        double dy = y - listener.moveTrackY;
        double dz = z - listener.moveTrackZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance > MOVEMENT_EPSILON) {
            listener.ticksSinceMoved = 0;
            listener.moveTrackX = x;
            listener.moveTrackY = y;
            listener.moveTrackZ = z;
        } else {
            listener.ticksSinceMoved++;
        }
    }

    // Watches the relevant position — for breaking, the block your
    // crosshair is directly on; for placing, the empty space just in
    // front of that block's face (where a new block actually lands,
    // not the reference block you're aiming at to place against it).
    // Reports a solid<->air transition of that position since this
    // listener's last check. Aim at the relevant spot for reliable
    // detection — same heuristic caveat as teleported: use a low
    // intervalTicks for momentary events like this.
    private static boolean checkBlockTransition(EventListener listener, MinecraftClient client, boolean detectPlaced) {
        if (client.world == null || !(client.crosshairTarget instanceof BlockHitResult hit)) {
            return false;
        }

        BlockPos pos = detectPlaced
            ? hit.getBlockPos().offset(hit.getSide())
            : hit.getBlockPos();
        boolean solidNow = !client.world.getBlockState(pos).isAir();

        boolean transitioned = false;
        if (listener.blockTrackBaseline && pos.equals(listener.trackedBlockPos)) {
            if (detectPlaced && !listener.trackedBlockSolid && solidNow) transitioned = true;
            if (!detectPlaced && listener.trackedBlockSolid && !solidNow) transitioned = true;
        }

        listener.trackedBlockPos = pos;
        listener.trackedBlockSolid = solidNow;
        listener.blockTrackBaseline = true;

        return transitioned;
    }

    // Supports a specific entity type ("zombie", "minecraft:cow") or one
    // of the broad categories "monsters"/"animals" — categorized by the
    // entity's vanilla spawn group (MONSTER covers all hostile mobs,
    // CREATURE covers typical passive farm animals; a few neutral mobs
    // like wolves/bees may not fall cleanly into either).
    private static boolean isEntityNearby(MinecraftClient client, String typeArg, double radius) {
        if (client.world == null || client.player == null) return false;

        String normalized = typeArg.trim().toLowerCase();
        boolean matchMonsters = normalized.equals("monsters") || normalized.equals("monster") || normalized.equals("hostile");
        boolean matchAnimals  = normalized.equals("animals") || normalized.equals("animal");
        if (!matchMonsters && !matchAnimals && !normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }

        Box box = client.player.getBoundingBox().expand(radius);
        double radiusSq = radius * radius;
        for (Entity entity : client.world.getOtherEntities(client.player, box)) {
            if (entity.squaredDistanceTo(client.player) > radiusSq) continue;

            if (matchMonsters) {
                if (entity.getType().getSpawnGroup() == SpawnGroup.MONSTER) return true;
                continue;
            }
            if (matchAnimals) {
                if (entity.getType().getSpawnGroup() == SpawnGroup.CREATURE) return true;
                continue;
            }

            String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (entityId.equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }

    private static boolean isPlayerNearby(MinecraftClient client, double radius) {
        if (client.world == null || client.player == null) return false;

        Box box = client.player.getBoundingBox().expand(radius);
        double radiusSq = radius * radius;
        for (PlayerEntity other : client.world.getEntitiesByClass(
                PlayerEntity.class, box, p -> p != client.player)) {
            if (other.squaredDistanceTo(client.player) <= radiusSq) return true;
        }
        return false;
    }

    // Backs item_durability(percent) — main hand only, as requested.
    // percent is remaining durability (100 = brand new, 0 = about to break).
    private static boolean isDurabilityLow(MinecraftClient client, double percentThreshold) {
        ItemStack stack = client.player.getMainHandStack();
        if (stack.isEmpty() || !stack.isDamageable()) return false;

        int max = stack.getMaxDamage();
        int damage = stack.getDamage();
        double remainingPercent = 100.0 * (max - damage) / max;
        return remainingPercent <= percentThreshold;
    }

    private static int countItem(MinecraftClient client, String itemId) {
        String normalized = itemId.trim();
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;

        int total = 0;
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String stackId = Registries.ITEM.getId(stack.getItem()).toString();
            if (stackId.equalsIgnoreCase(normalized)) total += stack.getCount();
        }
        return total;
    }

    private static boolean isKeyPressed(MinecraftClient client, String keyArg) {
        return com.taskblocks.client.KeyComboUtil.isComboDown(client, keyArg);
    }

    private static boolean isMousePressed(MinecraftClient client, String buttonArg) {
        int button = switch (buttonArg.trim().toLowerCase()) {
            case "left"   -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case "right"  -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case "middle" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            default -> -1;
        };
        if (button == -1) return false;
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
    }

    // ============================================================
    // Firing
    // ============================================================

    private static void fire(EventListener listener) {
        TaskBlocksNotifier.info("Listener fired: §f" + listener.id);
        TaskBlocks.LOGGER.info("[TaskBlocks] Listener '" + listener.id
            + "' fired -> " + listener.action);

        Thread fireThread = new Thread(() -> {
            try {
                List<String> allLines = ScriptRunner.getCurrentActions();
                Map<Integer, Integer> loopCounters = ScriptRunner.getCurrentLoopCounters();
                Map<String, String> variables = ScriptRunner.getCurrentVariables();
                Set<Integer> dispatchedBranches = ScriptRunner.getCurrentDispatchedBranches();
                int cursor = ScriptRunner.getCurrentCursor();

                if (allLines == null || variables == null) return;

                ActionContext ctx = new ActionContext(
                    cursor, loopCounters, variables, allLines, dispatchedBranches);
                ActionResult result = ActionRegistry.execute(listener.action, ctx);

                if (result.type == ActionResult.Type.JUMP
                        || result.type == ActionResult.Type.END
                        || result.type == ActionResult.Type.CHAIN) {
                    ScriptRunner.requestListenerControl(result);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Listener '" + listener.id
                    + "' action failed: " + e.getMessage());
            }
        }, "TaskBlocks-Listener-" + listener.id);

        fireThread.setDaemon(true);
        fireThread.start();
    }
}