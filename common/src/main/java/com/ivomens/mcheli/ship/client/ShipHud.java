package com.ivomens.mcheli.ship.client;

import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import com.ivomens.mcheli.plane.client.AbstractPlaneHud;
import com.ivomens.mcheli.ship.ShipEntity;
import net.minecraft.client.Minecraft;

public class ShipHud extends AbstractPlaneHud<ShipEntity> {

    public ShipHud(Minecraft minecraft, HudStyle seatHud, ScreenProjection projection) {
        super(minecraft, seatHud, projection, ShipEntity.class, true);
    }
}
