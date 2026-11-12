package com.ivomens.mcheli.mixin;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileSync;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * How the server updates the players tracking an MCHeli projectile.
 * <p>
 * A projectile takes its weapon's name (synced entity data) before it enters the level, so the value is in
 * the spawn packets already; vanilla would send it once more on the first update, as data that changed. The
 * first update sends only what changed after the spawn packets were made.
 * <p>
 * The projectiles clients fly themselves ({@link ProjectileSync}) get no movement updates from the tracker:
 * the projectile sends its corrections itself, as absolute positions (teleports), since each client's copy
 * starts from the position it was given when it began to track the projectile, not from one shared delta
 * base. Changed entity data is sent as vanilla sends it.
 */
@Mixin(ServerEntity.class)
public abstract class MixinServerEntity implements ProjectileSync.Tracker {

    @Shadow
    @Final
    private Entity entity;
    @Shadow
    @Final
    private Consumer<Packet<?>> broadcast;
    @Shadow
    @Final
    private VecDeltaCodec positionCodec;
    @Shadow
    @Final
    @Mutable
    private boolean trackDelta;
    @Shadow
    private Vec3 lastSentMovement;
    @Shadow
    private int lastSentYRot;
    @Shadow
    private int lastSentXRot;
    @Shadow
    private int tickCount;
    @Shadow
    @Nullable
    private List<SynchedEntityData.DataValue<?>> trackedDataValues;

    /**
     * A projectile clients fly themselves gets its motion with the spawn packet; vanilla would send it once
     * more in a motion packet of the pairing, and the tracker sends no other.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mcheli$motionInSpawnPacket(CallbackInfo ci) {
        if (ProjectileSync.isFlownByClients(this.entity)) {
            this.trackDelta = false;
        }
    }

    @Inject(method = "sendChanges", at = @At("HEAD"), cancellable = true)
    private void mcheli$sendProjectileChanges(CallbackInfo ci) {
        if (!(this.entity instanceof ProjectileEntity)) {
            return;
        }
        if (this.tickCount == 0) {
            this.mcheli$sendDataChangedSinceSpawn();
        }
        if (!ProjectileSync.isFlownByClients(this.entity)) {
            return;
        }
        ci.cancel();
        Entity projectile = this.entity;
        this.mcheli$sendChangedData();
        projectile.hasImpulse = false;
        this.tickCount++;
        if (projectile.hurtMarked) {
            projectile.hurtMarked = false;
            this.broadcast.accept(new ClientboundSetEntityMotionPacket(projectile));
        }
    }

    @Override
    public void mcheli$takeCurrentAsSent() {
        this.positionCodec.setBase(this.entity.trackingPosition());
        this.lastSentMovement = this.entity.getDeltaMovement();
        this.lastSentYRot = Mth.floor(this.entity.getYRot() * 256.0F / 360.0F);
        this.lastSentXRot = Mth.floor(this.entity.getXRot() * 256.0F / 360.0F);
    }

    /**
     * The first update: of the values that changed since the projectile was made, those whose new value
     * the spawn packets already carried are not sent again.
     */
    @Unique
    private void mcheli$sendDataChangedSinceSpawn() {
        SynchedEntityData data = this.entity.getEntityData();
        if (!data.isDirty()) {
            return;
        }
        List<SynchedEntityData.DataValue<?>> dirty = data.packDirty();
        List<SynchedEntityData.DataValue<?>> sentAtSpawn = this.trackedDataValues;
        List<SynchedEntityData.DataValue<?>> changed = new ArrayList<>();
        if (dirty != null) {
            for (SynchedEntityData.DataValue<?> value : dirty) {
                if (sentAtSpawn == null || !sentAtSpawn.contains(value)) {
                    changed.add(value);
                }
            }
        }
        this.trackedDataValues = data.getNonDefaultValues();
        if (!changed.isEmpty()) {
            this.broadcast.accept(new ClientboundSetEntityDataPacket(this.entity.getId(), changed));
        }
    }

    /** The changed entity data, as vanilla sends it. */
    @Unique
    private void mcheli$sendChangedData() {
        SynchedEntityData data = this.entity.getEntityData();
        List<SynchedEntityData.DataValue<?>> dirty = data.packDirty();
        if (dirty != null) {
            this.trackedDataValues = data.getNonDefaultValues();
            this.broadcast.accept(new ClientboundSetEntityDataPacket(this.entity.getId(), dirty));
        }
    }
}
