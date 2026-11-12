package com.ivomens.mcheli.vehicle.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Keeps the section renderer in sync while the camera is detached from the player's body, i.e.
 * piloting a far UAV or flying a TV missile.
 * <p>
 * When the view origin jumps far away, sections around the new origin may hold geometry built while
 * the grid was centred elsewhere. On detaching, re-attaching or switching camera entity, the box
 * around the new origin is marked dirty once; while detached, only the ring of sections entered when
 * the origin crosses a section boundary is marked. Runs at the end of the frame, after the level pass
 * has re-centred the grid. A normal third person vehicle camera stays close and needs none of this.
 */
public class DetachedViewRefresh {

    /** Beyond this horizontal distance from the body, in blocks squared, the view counts as detached. */
    private static final double DETACH_DIST_SQ = 48.0 * 48.0;

    private Entity lastViewEntity;
    private int lastChunkX;
    private int lastChunkZ;

    public void onRenderTickEnd() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            this.lastViewEntity = null;
            return;
        }

        Entity view = mc.getCameraEntity();
        int radius = mc.options.getEffectiveRenderDistance();
        LevelRenderer renderer = mc.levelRenderer;
        Level level = mc.level;

        double dx = view == null ? 0.0 : view.getX() - mc.player.getX();
        double dz = view == null ? 0.0 : view.getZ() - mc.player.getZ();
        boolean detached = view != null && view != mc.player && (dx * dx + dz * dz) > DETACH_DIST_SQ;

        if (!detached) {
            if (this.lastViewEntity != null) {
                markBox(renderer, level, Mth.floor(mc.player.getX()) >> 4, Mth.floor(mc.player.getZ()) >> 4, radius);
                this.lastViewEntity = null;
            }
            return;
        }

        int chunkX = Mth.floor(view.getX()) >> 4;
        int chunkZ = Mth.floor(view.getZ()) >> 4;

        if (view != this.lastViewEntity) {
            markBox(renderer, level, chunkX, chunkZ, radius);
        } else if (chunkX != this.lastChunkX || chunkZ != this.lastChunkZ) {
            int jump = 2 * radius + 1;
            if (Math.abs(chunkX - this.lastChunkX) > jump || Math.abs(chunkZ - this.lastChunkZ) > jump) {
                markBox(renderer, level, chunkX, chunkZ, radius);
            } else {
                markRing(renderer, level, this.lastChunkX, this.lastChunkZ, chunkX, chunkZ, radius);
            }
        }

        this.lastViewEntity = view;
        this.lastChunkX = chunkX;
        this.lastChunkZ = chunkZ;
    }

    private static void markBox(LevelRenderer renderer, Level level, int chunkX, int chunkZ, int radius) {
        renderer.setBlocksDirty((chunkX - radius) << 4, level.getMinBuildHeight(), (chunkZ - radius) << 4,
                ((chunkX + radius) << 4) + 15, level.getMaxBuildHeight() - 1, ((chunkZ + radius) << 4) + 15);
    }

    /** Marks the sections around the new centre that were outside the radius around the old one. */
    private static void markRing(LevelRenderer renderer, Level level, int oldX, int oldZ, int chunkX, int chunkZ,
                                 int radius) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                if (Math.abs(x - oldX) > radius || Math.abs(z - oldZ) > radius) {
                    renderer.setBlocksDirty(x << 4, minY, z << 4, (x << 4) + 15, maxY, (z << 4) + 15);
                }
            }
        }
    }
}
