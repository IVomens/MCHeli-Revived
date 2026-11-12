package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.config.wingman.WingmanSettings;
import com.ivomens.mcheli.wingman.roster.WingmanAccess;
import net.minecraft.server.level.ServerPlayer;

/** Applies new formation settings. They are global, so only operators may change them. */
public final class FormationSettingsHandler implements ServerMessageHandler<FormationSettingsMessage> {

    private static final int MAX_WINGMEN = 64;

    @Override
    public void handle(FormationSettingsMessage message, ServerPlayer sender) {
        FormationSettings settings = message.settings();
        if (!WingmanAccess.requireOperator(sender) || !settings.isFinite()) {
            return;
        }
        WingmanConfig.update(current -> current
                .withFormation(settings.sideDist(), settings.altOffset(), settings.rearDist())
                .with(WingmanSettings.MAX_WINGMEN, Math.max(1, Math.min(MAX_WINGMEN, settings.maxWingmen())))
                .withAttackAltitudes(Math.max(0, settings.minAttackAltitude()),
                        Math.max(0, settings.maxAttackAltitude())));
    }
}
