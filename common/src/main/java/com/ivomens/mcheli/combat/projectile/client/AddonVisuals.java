package com.ivomens.mcheli.combat.projectile.client;

import com.ivomens.mcheli.api.client.weapon.ProjectileVisual;
import com.ivomens.mcheli.api.client.weapon.ProjectileVisualProvider;
import com.ivomens.mcheli.api.client.weapon.ProjectileVisualRegistrar;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.behaviour.BehaviourSettings;
import com.ivomens.mcheli.combat.projectile.behaviour.ProjectileVisualHooks;
import com.ivomens.mcheli.combat.projectile.behaviour.WeaponBehaviourSet;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.content.def.weapon.BehaviourRef;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * The client visuals other mods register for their weapon behaviours through the API's
 * {@link ProjectileVisualProvider}. The providers are asked the first time a projectile with addon behaviours
 * appears; each weapon's visuals are made once and made again when its file is reloaded.
 */
public final class AddonVisuals implements ProjectileVisualHooks.Hook {

    /** One visual of a weapon, with the behaviour id it was registered for. */
    record Entry(ResourceLocation id, ProjectileVisual visual) {
    }

    /** The visuals made for one loaded weapon. */
    private record Made(WeaponSpec weapon, List<Entry> entries) {
    }

    @Nullable
    private Map<ResourceLocation, ProjectileVisual.Factory> factories;
    /** By weapon name; replaced when the weapon's file is reloaded. */
    private final Map<String, Made> made = new HashMap<>();

    @Override
    @Nullable
    public ProjectileVisualHooks.Visuals attach(ProjectileEntity projectile, WeaponSpec weapon) {
        WeaponBehaviourSet behaviours = weapon.behaviours();
        if (behaviours == null) {
            return null;
        }
        Made visuals = this.made.get(weapon.name());
        if (visuals == null || visuals.weapon() != weapon) {
            visuals = new Made(weapon, this.make(weapon, behaviours));
            this.made.put(weapon.name(), visuals);
        }
        return visuals.entries().isEmpty() ? null : new AddonVisualRunner(projectile, weapon, visuals.entries());
    }

    private List<Entry> make(WeaponSpec weapon, WeaponBehaviourSet behaviours) {
        Map<ResourceLocation, ProjectileVisual.Factory> registered = this.factories();
        List<Entry> entries = new ArrayList<>();
        for (BehaviourRef ref : behaviours.named()) {
            ProjectileVisual.Factory factory = registered.get(ref.id());
            if (factory == null) {
                continue;
            }
            try {
                ProjectileVisual visual = factory.create(BehaviourSettings.toTag(ref.settings()));
                if (visual != null) {
                    entries.add(new Entry(ref.id(), visual));
                }
            } catch (RuntimeException e) {
                ModLogger.warn("The visual of {} for weapon {} rejected its settings: {}", ref.id(), weapon.name(),
                        e.getMessage());
            }
        }
        return List.copyOf(entries);
    }

    private Map<ResourceLocation, ProjectileVisual.Factory> factories() {
        if (this.factories == null) {
            this.factories = discover(ServiceLoader.load(ProjectileVisualProvider.class,
                    AddonVisuals.class.getClassLoader()).iterator());
        }
        return this.factories;
    }

    /** Asks every provider for its visuals; a provider that fails is logged and skipped. */
    static Map<ResourceLocation, ProjectileVisual.Factory> discover(Iterator<ProjectileVisualProvider> providers) {
        Map<ResourceLocation, ProjectileVisual.Factory> found = new LinkedHashMap<>();
        ProjectileVisualRegistrar registrar = (id, factory) -> {
            if (found.putIfAbsent(id, factory) != null) {
                throw new IllegalArgumentException("a projectile visual for " + id + " is already registered");
            }
        };
        while (true) {
            ProjectileVisualProvider provider;
            try {
                if (!providers.hasNext()) {
                    break;
                }
                provider = providers.next();
            } catch (ServiceConfigurationError e) {
                ModLogger.error("A projectile visual provider could not be created", e);
                continue;
            }
            try {
                provider.registerVisuals(registrar);
            } catch (RuntimeException e) {
                ModLogger.error("Projectile visual provider " + provider.getClass().getName() + " failed", e);
            }
        }
        return Map.copyOf(found);
    }
}
