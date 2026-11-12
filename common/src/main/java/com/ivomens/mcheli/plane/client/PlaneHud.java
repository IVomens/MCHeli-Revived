package com.ivomens.mcheli.plane.client;

import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import com.ivomens.mcheli.plane.PlaneEntity;
import net.minecraft.client.Minecraft;

public class PlaneHud extends AbstractPlaneHud<PlaneEntity> {

    public PlaneHud(Minecraft minecraft, HudStyle seatHud, ScreenProjection projection) {
        super(minecraft, seatHud, projection, PlaneEntity.class, false);
    }
}
