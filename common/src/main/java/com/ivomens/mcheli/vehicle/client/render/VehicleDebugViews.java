package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.PartBoundingBox;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.content.def.vehicle.HitBoxType;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.tank.WheelProbe;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.HullPose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Test mode views of a vehicle's collision: the hit boxes with their damage factors, the pilot seat
 * and a tank's suspension wheels. They show what the server simulates, so unlike the model they read
 * the entity directly.
 */
final class VehicleDebugViews {

    private static final ResourceLocation TEX_HIT_BOX = McheliEntityRenderer.texture("textures/hit_box.png");
    private static final ResourceLocation TEX_BOUNDING_BOX = McheliEntityRenderer.texture("textures/bounding_box.png");
    private static final ResourceLocation TEX_SEAT_PILOT = McheliEntityRenderer.texture("textures/seat_pilot.png");
    private static final String BOX_MODEL = "box";
    private static final float HIT_BOX_LABEL_SCALE = 0.08F;
    private static final float WHEEL_SIZE = 1.0F;
    private static final float LINK_ALPHA = 192 / 255.0F;

    private VehicleDebugViews() {
    }

    /** Draws the views in test mode; does nothing otherwise. */
    static void render(AircraftEntity vehicle, VehicleRenderSnapshot snapshot) {
        IModelCustom box = ModelCache.find(BOX_MODEL);
        if (!McheliConfig.debug().testMode() || box == null) {
            return;
        }
        if (vehicle instanceof TankEntity tank) {
            renderWheels(tank, box);
        }
        renderHitBoxes(vehicle, box);
        renderPilotSeat(vehicle, snapshot.hull(), box);
    }

    /** The collision box and every extra hit box with its damage factor. */
    private static void renderHitBoxes(AircraftEntity e, IModelCustom box) {
        LegacyGL.pushMatrix();
        LegacyGL.translate(0.0, McheliEntity.GLOBAL_Y_OFFSET, 0.0);
        LegacyGL.scale(e.getBbWidth(), e.getBbHeight(), e.getBbWidth());
        LegacyGL.bindTexture(TEX_HIT_BOX);
        box.renderAll();
        LegacyGL.popMatrix();

        LegacyGL.pushMatrix();
        LegacyGL.translate(0.0, McheliEntity.GLOBAL_Y_OFFSET, 0.0);
        for (int i = 0; i < e.partBoxes().boxes().length; i++) {
            PartBoundingBox part = e.partBoxes().boxes()[i];
            LegacyGL.pushMatrix();
            if (part.rotatedOffset() != null) {
                LegacyGL.translate(part.rotatedOffset().x, part.rotatedOffset().y, part.rotatedOffset().z);
            }
            float boxYaw = part.rotationYaw();
            float boxPitch = part.rotationPitch();
            float boxRoll = part.rotationRoll();
            if (part.boundingBoxType() == HitBoxType.TURRET) {
                boxYaw += part.localRotYaw();
                boxPitch += part.localRotPitch();
                boxRoll += part.localRotRoll();
            }
            LegacyGL.rotate(-boxYaw, 0.0F, 1.0F, 0.0F);
            LegacyGL.rotate(boxPitch, 1.0F, 0.0F, 0.0F);
            LegacyGL.rotate(boxRoll, 0.0F, 0.0F, 1.0F);
            LegacyGL.scale(part.width(), part.height(), part.widthZ());
            LegacyGL.bindTexture(TEX_BOUNDING_BOX);
            box.renderAll();
            LegacyGL.popMatrix();
            drawHitBoxLabel(part, i + 1);
        }
        LegacyGL.popMatrix();
        LegacyGL.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawHitBoxLabel(PartBoundingBox box, int index) {
        String label = String.format("[%d] %.2f", index, box.damageFactor());
        LegacyGL.pushMatrix();
        Vec3 offset = box.rotatedOffset() == null ? Vec3.ZERO : box.rotatedOffset();
        LegacyGL.translate(offset.x, offset.y, offset.z);
        LegacyGL.translate(0.0F, 0.5F + box.height(), 0.0F);
        LegacyGL.rotateToCamera();
        LegacyGL.scale(-HIT_BOX_LABEL_SCALE, -HIT_BOX_LABEL_SCALE, HIT_BOX_LABEL_SCALE);
        int color = box.damageFactor() > 1.0F ? 0xFFFF0000 : 0xFFFFFFFF;
        LegacyGL.drawString(label, -LegacyGL.stringWidth(label) / 2.0F, 0, color, false, 0x66000000);
        LegacyGL.popMatrix();
    }

    /** A box at the pilot seat. */
    private static void renderPilotSeat(AircraftEntity e, HullPose hull, IModelCustom box) {
        SeatInfo seat = e.seats().info(0);
        if (seat == null) {
            return;
        }
        LegacyGL.pushMatrix();
        LegacyGL.translate(0.0, McheliEntity.GLOBAL_Y_OFFSET, 0.0);
        LegacyGL.rotate(hull.yaw(), 0.0F, -1.0F, 0.0F);
        LegacyGL.rotate(hull.pitch(), 1.0F, 0.0F, 0.0F);
        LegacyGL.rotate(hull.roll(), 0.0F, 0.0F, 1.0F);
        LegacyGL.translate(seat.pos.x, seat.pos.y, seat.pos.z);
        LegacyGL.bindTexture(TEX_SEAT_PILOT);
        box.renderAll();
        LegacyGL.popMatrix();
    }

    /** A box per suspension wheel, plus each wheel pair linked to the weighted centre. */
    private static void renderWheels(TankEntity tank, IModelCustom box) {
        double posY = McheliEntity.GLOBAL_Y_OFFSET;
        LegacyGL.color(0.75F, 0.75F, 0.75F, 0.5F);
        for (WheelProbe wheel : tank.wheels().wheels()) {
            LegacyGL.pushMatrix();
            LegacyGL.translate(wheel.getX() - tank.getX(), wheel.getY() - tank.getY() + posY + 0.25,
                    wheel.getZ() - tank.getZ());
            LegacyGL.scale(WHEEL_SIZE, WHEEL_SIZE / 2.0F, WHEEL_SIZE);
            LegacyGL.bindTexture(TEX_SEAT_PILOT);
            box.renderAll();
            LegacyGL.popMatrix();
        }

        LegacyGL.color(0.75F, 0.75F, 0.75F, 1.0F);
        VertexConsumer lines = LegacyGL.lineBuffer(0.0F, false);
        Vec3 center = tank.body().toWorld(tank.wheels().weightedCenter())
                .subtract(tank.getX(), tank.getY(), tank.getZ());
        double cx = center.x, cy = posY + center.y, cz = center.z;

        for (int i = 0; i < tank.wheels().wheels().length / 2; i++) {
            // colour each pair by the low three bits of its index
            float r = (i & 4) > 0 ? 1.0F : 0.0F;
            float g = (i & 2) > 0 ? 1.0F : 0.0F;
            float b = (i & 1) > 0 ? 1.0F : 0.0F;
            WheelProbe first = tank.wheels().wheels()[i * 2];
            WheelProbe second = tank.wheels().wheels()[i * 2 + 1];
            double x1 = first.getX() - tank.getX(), y1 = first.getY() - tank.getY() + posY;
            double z1 = first.getZ() - tank.getZ();
            double x2 = second.getX() - tank.getX(), y2 = second.getY() - tank.getY() + posY;
            double z2 = second.getZ() - tank.getZ();
            if (first.isFrontOfCenter()) {
                LegacyGL.line(lines, x2, y2, z2, x1, y1, z1, r, g, b, LINK_ALPHA);
                LegacyGL.line(lines, x1, y1, z1, cx, cy, cz, r, g, b, LINK_ALPHA);
                LegacyGL.line(lines, cx, cy, cz, x2, y2, z2, r, g, b, LINK_ALPHA);
            } else {
                LegacyGL.line(lines, x1, y1, z1, x2, y2, z2, r, g, b, LINK_ALPHA);
                LegacyGL.line(lines, x2, y2, z2, cx, cy, cz, r, g, b, LINK_ALPHA);
                LegacyGL.line(lines, cx, cy, cz, x1, y1, z1, r, g, b, LINK_ALPHA);
            }
        }
        LegacyGL.resetColor();
    }
}
