package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.client.camera.CameraAngles;
import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * NeoForge's camera events: {@code ComputeCameraAngles} with its roll, and
 * {@code CalculateDetachedCameraDistanceEvent}. Vanilla's camera has no roll, so the roll MCHeli asks
 * for is added to the rotation the camera builds, the way NeoForge's camera does it.
 */
@Mixin(Camera.class)
abstract class CameraMixin {

    @Unique
    private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);

    @Unique
    private final CameraAngles mcheli$angles = new CameraAngles();

    /** The roll of the current view, mirrored for the reverse third-person view as NeoForge does. */
    @Unique
    private float mcheli$roll;

    /** The roll the next {@code setRotation} applies; zero for any call but the ones below, as on NeoForge. */
    @Unique
    private float mcheli$nextRoll;

    @WrapOperation(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0))
    private void mcheli$viewAngles(Camera camera, float yaw, float pitch, Operation<Void> original) {
        this.mcheli$angles.set(yaw, pitch, 0.0F);
        ClientEventHooks.adjustCameraAngles(this.mcheli$angles);
        this.mcheli$roll = this.mcheli$angles.roll();
        this.mcheli$rotate(camera, this.mcheli$angles.yaw(), this.mcheli$angles.pitch(), this.mcheli$roll, original);
    }

    @WrapOperation(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
    private void mcheli$reverseView(Camera camera, float yaw, float pitch, Operation<Void> original) {
        this.mcheli$roll = -this.mcheli$roll;
        this.mcheli$rotate(camera, yaw, pitch, this.mcheli$roll, original);
    }

    @Unique
    private void mcheli$rotate(Camera camera, float yaw, float pitch, float roll, Operation<Void> original) {
        this.mcheli$nextRoll = roll;
        try {
            original.call(camera, yaw, pitch);
        } finally {
            this.mcheli$nextRoll = 0.0F;
        }
    }

    @ModifyArg(method = "setRotation", at = @At(value = "INVOKE",
            target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"), index = 2)
    private float mcheli$roll(float vanillaRoll) {
        return vanillaRoll - this.mcheli$nextRoll * DEGREES_TO_RADIANS;
    }

    @ModifyExpressionValue(method = "setup", at = @At(value = "CONSTANT", args = "floatValue=4.0"))
    private float mcheli$detachedDistance(float distance) {
        return ClientEventHooks.adjustCameraDistance(distance);
    }
}
