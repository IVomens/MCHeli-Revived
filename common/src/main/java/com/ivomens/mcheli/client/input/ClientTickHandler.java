package com.ivomens.mcheli.client.input;

import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.sound.client.UiSounds;
import net.minecraft.client.Minecraft;

/**
 * The keys and per-tick client logic of one kind of control: a vehicle type, a seat, a held item. The
 * client's tick loop calls {@link #onTick} once per client tick, in a fixed order, and
 * {@link #updateKeybind} whenever the key settings change.
 */
public abstract class ClientTickHandler {

    protected final Minecraft mc;

    protected ClientTickHandler(Minecraft minecraft) {
        this.mc = minecraft;
    }

    protected static void playSoundOK() {
        UiSounds.click();
    }

    protected static void playSoundNG() {
        UiSounds.play("ng", 1.0F, 1.0F);
    }

    protected static void playSound(String name) {
        UiSounds.play(name, 1.0F, 1.0F);
    }

    protected static void playSound(String name, float volume, float pitch) {
        UiSounds.play(name, volume, pitch);
    }

    /** Rebinds the handler's keys from the settings. */
    public abstract void updateKeybind(KeySettings bindings);

    /**
     * One client tick.
     *
     * @param inGUI whether a screen is open; most keys are ignored then
     */
    public abstract void onTick(boolean inGUI);
}
