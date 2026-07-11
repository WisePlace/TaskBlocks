package com.taskblocks.script;

import net.minecraft.util.math.BlockPos;

// ============================================================
// State for a single listen() registration.
// wasTrue implements edge-detection (fires on false->true only).
// lastX/Y/Z + hasBaseline back the 'teleported' built-in condition.
// moveTrack* + ticksSinceMoved back 'still(N)'/'moving', updated every
// game tick regardless of this listener's own check interval, since
// stillness is a continuous measurement, not a point-in-time one.
// trackedBlock* back 'block_broken'/'block_placed': whatever block the
// crosshair is on when this listener checks, watched for a solid<->air
// transition since its previous check.
// ============================================================
public class EventListener {
    public final String id;
    public final String condition;
    public final String action;
    public final int intervalTicks;

    public long lastCheckTick = -1;
    public boolean wasTrue = false;

    public boolean hasBaseline = false;
    public double lastX;
    public double lastY;
    public double lastZ;

    public boolean moveTrackBaseline = false;
    public double moveTrackX;
    public double moveTrackY;
    public double moveTrackZ;
    public long ticksSinceMoved = 0;

    public boolean blockTrackBaseline = false;
    public BlockPos trackedBlockPos = null;
    public boolean trackedBlockSolid = false;

    public EventListener(String id, String condition, String action, int intervalTicks) {
        this.id = id;
        this.condition = condition;
        this.action = action;
        this.intervalTicks = Math.max(1, intervalTicks);
    }
}