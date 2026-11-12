package com.ivomens.mcheli.neoforge.client;

import com.ivomens.mcheli.platform.client.ClientPlatformHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Key checks honour NeoForge's key conflict contexts and modifiers. */
public class NeoForgeClientPlatformHelper implements ClientPlatformHelper {

    @Override
    public boolean isActiveAndMatches(KeyMapping mapping, InputConstants.Key key) {
        return mapping.isActiveAndMatches(key);
    }
}
