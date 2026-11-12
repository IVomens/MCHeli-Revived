package com.ivomens.mcheli.vehicle.client.debug;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.client.render.RenderLib;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Test-mode overlay: weapon muzzle points and weapon / vehicle part parent-child links. */
public class WeaponPointRenderer {

    private static final float[][] WEAPON_COLORS = {
            {1.0F, 0.0F, 0.0F, 1.0F},
            {0.0F, 1.0F, 0.0F, 1.0F},
            {0.0F, 0.0F, 1.0F, 1.0F},
            {1.0F, 1.0F, 0.0F, 1.0F},
            {1.0F, 0.0F, 1.0F, 1.0F},
            {0.0F, 1.0F, 1.0F, 1.0F},
            {0.95686275F, 0.6431373F, 0.3764706F, 1.0F},
            {0.5411765F, 0.16862746F, 0.42477876F, 1.0F}
    };
    private static final float[] LINK_COLOR = {0.85F, 0.85F, 0.85F, 1.0F};

    /**
     * Must be called inside a {@link LegacyGL} frame whose pose is at the aircraft's render origin.
     * Each weapon set gets a colour, lightened per barrel; muzzles sharing a position are stacked.
     */
    public static void renderWeaponPoints(AircraftEntity ac, VehicleSpec info, double x, double y, double z) {
        int setIndex = 0;
        Map<Vec3, Integer> sharedPositions = new HashMap<>();

        LegacyGL.pushMatrix();
        LegacyGL.translate(x, y, z);

        VertexConsumer boxes = LegacyGL.colorBuffer(false, false);
        for (VehicleParts.WeaponSet setInfo : info.armament().weaponSets()) {
            WeaponGroup weaponSet = ac.weapons().byName(setInfo.type);
            if (weaponSet == null) {
                continue;
            }
            float[] color = WEAPON_COLORS[setIndex % WEAPON_COLORS.length];
            for (int i = 0; i < weaponSet.mountCount(); i++) {
                WeaponMount weapon = weaponSet.mount(i);
                if (weapon == null) {
                    continue;
                }
                int stack = sharedPositions.containsKey(weapon.position()) ? sharedPositions.get(weapon.position()) + 1 : 0;
                sharedPositions.put(weapon.position(), stack);
                Vec3 muzzle = ac.weapons().shooter(null).muzzleOffset(weapon);
                float tint = i * 0.1F;
                RenderLib.drawPoint(boxes, muzzle.x, muzzle.y + stack * 0.04, muzzle.z, 0.05F,
                        clampColor(color[0] + tint), clampColor(color[1] + tint), clampColor(color[2] + tint), color[3]);
            }
            setIndex++;
        }

        renderWeaponChildLinks(ac, info);
        if (info.kind() == VehicleKind.VEHICLE) {
            renderVehiclePartChildLinks(ac, info);
        }

        LegacyGL.popMatrix();
    }

    private static void renderWeaponChildLinks(AircraftEntity ac, VehicleSpec info) {
        VertexConsumer lines = LegacyGL.lineBuffer(0.0F, false);
        int id = 0;
        for (VehicleParts.PartWeapon part : info.parts().weapons()) {
            float[] c = WEAPON_COLORS[id % WEAPON_COLORS.length];
            Vec3 parentLocal = part.pos;
            Vec3 parentWorld = getRelativePos(ac, parentLocal);
            for (VehicleParts.PartWeaponChild child : part.child) {
                Vec3 childWorldPos = getRelativePos(ac, parentLocal.add(child.pos));
                drawLine(lines, parentWorld, childWorldPos, c);
            }
            id++;
        }
    }

    private static void renderVehiclePartChildLinks(AircraftEntity ac, VehicleSpec info) {
        VertexConsumer lines = LegacyGL.lineBuffer(0.0F, false);
        for (VehicleParts.VPart part : info.kindParts().turretParts()) {
            drawVehicleChildLinesRecursive(ac, lines, part, part.pos);
        }
    }

    private static void drawVehicleChildLinesRecursive(AircraftEntity ac, VertexConsumer lines,
                                                       VehicleParts.VPart parent, Vec3 parentLocalPos) {
        if (parent.child == null) {
            return;
        }

        Vec3 parentWorldPos = getRelativePos(ac, parentLocalPos);
        for (VehicleParts.VPart child : parent.child) {
            Vec3 childLocalPos = parentLocalPos.add(child.pos);
            Vec3 childWorldPos = getRelativePos(ac, childLocalPos);
            drawLine(lines, parentWorldPos, childWorldPos, LINK_COLOR);
            drawVehicleChildLinesRecursive(ac, lines, child, childLocalPos);
        }
    }

    private static Vec3 getRelativePos(AircraftEntity ac, Vec3 localPos) {
        return ac.body().toWorld(localPos.x, localPos.y, localPos.z, 0, 0, 0);
    }

    private static void drawLine(VertexConsumer lines, Vec3 start, Vec3 end, float[] c) {
        LegacyGL.line(lines, start.x, start.y, start.z, end.x, end.y, end.z, c[0], c[1], c[2], c[3]);
    }

    private static float clampColor(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }
}
