package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.sound.client.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** {@link LocalClient} on a physical client, answered by the running game. */
public final class LocalClientService implements LocalClient {

    @Override
    public Player player() {
        return Minecraft.getInstance().player;
    }

    @Override
    public boolean isFirstPerson() {
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    @Override
    public boolean isSingleplayer() {
        return Minecraft.getInstance().isSingleplayer();
    }

    @Override
    public void playUiSound(ResourceLocation sound, float volume, float pitch) {
        UiSounds.play(sound, volume, pitch);
    }

    @Override
    public void playClick() {
        UiSounds.click();
    }

    @Override
    public void useVisionEffect(@Nullable ResourceLocation postEffect) {
        McheliClient.session().postEffects().use(postEffect);
    }

    @Override
    public void kickView(float pitch, float yaw, float control) {
        McheliClient.session().viewRecoil().kick(pitch, yaw, control);
    }

    @Override
    public void skipBlockBreakDelay() {
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (gameMode != null) {
            gameMode.destroyDelay = 0;
        }
    }

    @Override
    public PlayingSound playSound(ResourceLocation sound, float volume, float pitch, Vec3 at) {
        SoundInstance instance = new SimpleSoundInstance(ModSounds.getSound(sound), SoundSource.PLAYERS, volume, pitch,
                RandomSource.create(), at.x, at.y, at.z);
        Minecraft.getInstance().getSoundManager().play(instance);
        return () -> Minecraft.getInstance().getSoundManager().stop(instance);
    }
}
