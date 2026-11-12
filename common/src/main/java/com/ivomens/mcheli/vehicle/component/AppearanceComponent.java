package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Which of the definition's textures the vehicle wears, and the player-made livery painted over it. */
public final class AppearanceComponent implements VehicleComponent {

    private final Supplier<VehicleSpec> info;
    private final SyncedValue<String> texture;
    private final SyncedValue<String> livery;

    public AppearanceComponent(Supplier<VehicleSpec> info, SyncedValue<String> texture, SyncedValue<String> livery) {
        this.info = info;
        this.texture = texture;
        this.livery = livery;
    }

    public String texture() {
        return this.texture.get();
    }

    /** Sets the texture; an empty name keeps the current one. */
    public void setTexture(@Nullable String name) {
        if (name != null && !name.isEmpty()) {
            this.texture.set(name);
        }
    }

    /** Moves on to the definition's next texture, as the wrench does. */
    public void nextTexture() {
        VehicleSpec info = this.info.get();
        if (info != null) {
            setTexture(info.getNextTextureName(texture()));
        }
    }

    /** Id of the player-made livery painted on the vehicle; empty for the default texture. */
    public String liveryId() {
        return this.livery.get();
    }

    @Override
    public void describe(StateSink state) {
        state.put("texture", texture().isEmpty() ? "-" : texture());
        state.put("livery", liveryId().isEmpty() ? "-" : liveryId());
    }

    /** Sets the livery id. Anything that is not a valid livery id resets to the default texture. */
    public void setLiveryId(@Nullable String id) {
        this.livery.set(LiveryId.isValid(id) ? id : "");
    }

    public void save(CompoundTag tag) {
        tag.putString(VehicleNbtCodec.TEXTURE_NAME, texture());
        if (!liveryId().isEmpty()) {
            tag.putString(VehicleNbtCodec.LIVERY, liveryId());
        }
    }

    public void load(CompoundTag tag) {
        setTexture(tag.getString(VehicleNbtCodec.TEXTURE_NAME));
        setLiveryId(tag.getString(VehicleNbtCodec.LIVERY));
    }
}
