package com.ivomens.mcheli.vehicle.component;

/**
 * How loud and how high a vehicle type's engine sounds, worked out from its throttle and keys. The
 * vehicle type picks one; {@link EngineSoundComponent} plays it.
 */
public interface EngineSound {

    /** Until the vehicle type picks its sound. */
    EngineSound NONE = new EngineSound() {
        @Override
        public float volume() {
            return 0.0F;
        }

        @Override
        public float pitch() {
            return 1.0F;
        }

        @Override
        public String defaultName() {
            return "";
        }
    };

    /** Volume factor, multiplied with the definition's sound volume. */
    float volume();

    /** Pitch factor, multiplied with the definition's sound pitch. */
    float pitch();

    /** The sound played when the definition names none. */
    String defaultName();

    /** Client, once per tick, for sounds that fade towards their target. */
    default void tick() {
    }
}
