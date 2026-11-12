package com.ivomens.mcheli.combat.projectile.behaviour;

import com.ivomens.mcheli.api.weapon.GuidanceLaw;
import com.ivomens.mcheli.api.weapon.ProjectileEffect;
import com.ivomens.mcheli.api.weapon.WeaponBehaviourProvider;
import com.ivomens.mcheli.api.weapon.WeaponBehaviourRegistrar;
import com.ivomens.mcheli.content.def.weapon.BehaviourRef;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * The guidance laws and projectile effects other mods register through the API's {@link WeaponBehaviourProvider},
 * and how a weapon file's {@code Guidance} and {@code ProjectileEffects} become the behaviours of its projectiles.
 * The providers are asked once, the first time a weapon file is built, which is while MCHeli loads its content.
 */
public final class WeaponBehaviours {

    private final Map<ResourceLocation, GuidanceLaw.Factory> guidance;
    private final Map<ResourceLocation, ProjectileEffect.Factory> effects;

    WeaponBehaviours(Map<ResourceLocation, GuidanceLaw.Factory> guidance,
                     Map<ResourceLocation, ProjectileEffect.Factory> effects) {
        this.guidance = Map.copyOf(guidance);
        this.effects = Map.copyOf(effects);
    }

    /** The behaviours of every provider on the class path. */
    public static WeaponBehaviours installed() {
        return Installed.INSTANCE;
    }

    /** Asks every provider for its behaviours; a provider that fails is logged and skipped. */
    static WeaponBehaviours discover(Iterator<WeaponBehaviourProvider> providers) {
        Map<ResourceLocation, GuidanceLaw.Factory> guidance = new LinkedHashMap<>();
        Map<ResourceLocation, ProjectileEffect.Factory> effects = new LinkedHashMap<>();
        while (true) {
            WeaponBehaviourProvider provider;
            try {
                if (!providers.hasNext()) {
                    break;
                }
                provider = providers.next();
            } catch (ServiceConfigurationError e) {
                ModLogger.error("A weapon behaviour provider could not be created", e);
                continue;
            }
            try {
                provider.registerBehaviours(new Registrar(guidance, effects));
            } catch (RuntimeException e) {
                ModLogger.error("Weapon behaviour provider " + provider.getClass().getName() + " failed", e);
            }
        }
        if (!guidance.isEmpty() || !effects.isEmpty()) {
            ModLogger.info("Addon weapon behaviours: guidance {}, projectile effects {}", guidance.keySet(),
                    effects.keySet());
        }
        return new WeaponBehaviours(guidance, effects);
    }

    /**
     * The behaviours of a weapon file, or null when it names none (every bundled weapon), so its projectiles carry
     * nothing extra. Ids nobody registered and factories that reject their settings are reported (into the content
     * report, as the file is being read) and left out: the weapon keeps its built-in guidance or simply runs fewer
     * effects.
     */
    @Nullable
    public WeaponBehaviourSet resolve(WeaponDefinition definition) {
        BehaviourRef guidanceRef = definition.guidance();
        List<BehaviourRef> effectRefs = definition.projectileEffects();
        if (guidanceRef == null && effectRefs.isEmpty()) {
            return null;
        }
        GuidanceLaw law = guidanceRef == null ? null : this.createGuidance(guidanceRef);
        List<ResourceLocation> effectIds = new ArrayList<>();
        List<ProjectileEffect> effects = new ArrayList<>();
        for (BehaviourRef ref : effectRefs) {
            if (effects.size() == WeaponBehaviourSet.MAX_EFFECTS) {
                ModLogger.warn("A weapon runs at most {} projectile effects; '{}' and those after it are left out",
                        WeaponBehaviourSet.MAX_EFFECTS, ref.id());
                break;
            }
            ProjectileEffect effect = this.createEffect(ref);
            if (effect != null) {
                effectIds.add(ref.id());
                effects.add(effect);
            }
        }
        List<BehaviourRef> named = new ArrayList<>(effectRefs);
        if (guidanceRef != null) {
            named.addFirst(guidanceRef);
        }
        return new WeaponBehaviourSet(law != null ? guidanceRef.id() : null, law, effectIds, effects, named);
    }

    @Nullable
    private GuidanceLaw createGuidance(BehaviourRef ref) {
        GuidanceLaw.Factory factory = this.guidance.get(ref.id());
        if (factory == null) {
            ModLogger.warn("Guidance '{}' is not registered by any installed mod; the weapon uses the guidance of "
                    + "its type", ref.id());
            return null;
        }
        try {
            return requireMade(factory.create(BehaviourSettings.toTag(ref.settings())), ref);
        } catch (RuntimeException e) {
            ModLogger.warn("Guidance '{}' rejected its settings ({}); the weapon uses the guidance of its type",
                    ref.id(), describe(e));
            return null;
        }
    }

    @Nullable
    private ProjectileEffect createEffect(BehaviourRef ref) {
        ProjectileEffect.Factory factory = this.effects.get(ref.id());
        if (factory == null) {
            ModLogger.warn("Projectile effect '{}' is not registered by any installed mod; it is left out", ref.id());
            return null;
        }
        try {
            return requireMade(factory.create(BehaviourSettings.toTag(ref.settings())), ref);
        } catch (RuntimeException e) {
            ModLogger.warn("Projectile effect '{}' rejected its settings ({}); it is left out", ref.id(),
                    describe(e));
            return null;
        }
    }

    private static <T> T requireMade(@Nullable T made, BehaviourRef ref) {
        if (made == null) {
            throw new IllegalStateException("the factory of " + ref.id() + " made nothing");
        }
        return made;
    }

    private static String describe(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** What a provider registers with; refuses an id registered twice. */
    private record Registrar(Map<ResourceLocation, GuidanceLaw.Factory> guidance,
                             Map<ResourceLocation, ProjectileEffect.Factory> effects)
            implements WeaponBehaviourRegistrar {

        @Override
        public void guidance(ResourceLocation id, GuidanceLaw.Factory factory) {
            if (this.guidance.putIfAbsent(id, factory) != null) {
                throw new IllegalArgumentException("guidance " + id + " is already registered");
            }
        }

        @Override
        public void projectileEffect(ResourceLocation id, ProjectileEffect.Factory factory) {
            if (this.effects.putIfAbsent(id, factory) != null) {
                throw new IllegalArgumentException("projectile effect " + id + " is already registered");
            }
        }
    }

    /** Found on first use, which is while the content loads, after every mod's classes are on the class path. */
    private static final class Installed {
        static final WeaponBehaviours INSTANCE = discover(ServiceLoader.load(WeaponBehaviourProvider.class,
                WeaponBehaviours.class.getClassLoader()).iterator());
    }
}
