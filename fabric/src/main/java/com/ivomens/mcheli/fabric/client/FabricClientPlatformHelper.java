package com.ivomens.mcheli.fabric.client;

import com.ivomens.mcheli.platform.client.ClientPlatformHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/**
 * Fabric has no key conflict contexts or modifiers, so a key matches its mapping as in vanilla: it is
 * the bound key. NeoForge additionally checks the mapping's context and modifier.
 */
public class FabricClientPlatformHelper implements ClientPlatformHelper {

    @Override
    public boolean isActiveAndMatches(KeyMapping mapping, InputConstants.Key key) {
        return !key.equals(InputConstants.UNKNOWN) && key.equals(mapping.key);
    }
}
