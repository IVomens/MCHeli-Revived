package com.ivomens.mcheli.combat.projectile.client;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The models projectiles are drawn with: the weapon's own model (its bomblet model for sub-munitions),
 * or the default model of the projectile's kind. The client shell loads the defaults with the other
 * models ({@link #setDefault}).
 */
public final class ProjectileModels {

    private static final Map<ProjectileKind, BulletModel> DEFAULTS = new EnumMap<>(ProjectileKind.class);
    /** The weapons' own models, by weapon name. */
    private static final Map<String, BulletModel> BULLET_MODELS = new ConcurrentHashMap<>();
    private static final Map<String, BulletModel> BOMBLET_MODELS = new ConcurrentHashMap<>();


    private ProjectileModels() {
    }

    /** The model file each kind falls back to; anti-tank and TV missiles share the cruise missile's. */
    public static String defaultModelName(ProjectileKind kind) {
        return switch (kind) {
            case BULLET -> "bullet";
            case ROCKET, MARKER_ROCKET -> "rocket";
            case AA_MISSILE -> "aamissile";
            case AS_MISSILE, AT_MISSILE, TV_MISSILE -> "asmissile";
            case BOMB, DISPENSED_ITEM -> "bomb";
            case TORPEDO -> "torpedo";
        };
    }

    public static void setDefault(ProjectileKind kind, @Nullable BulletModel model) {
        if (model == null) {
            DEFAULTS.remove(kind);
        } else {
            DEFAULTS.put(kind, model);
        }
    }

    /** Records the bullet model a weapon names, unless the model is missing. */
    public static void setBulletModel(String weapon, @Nullable BulletModel model) {
        put(BULLET_MODELS, weapon, model);
    }

    /** Records the bomblet model a weapon names, unless the model is missing. */
    public static void setBombletModel(String weapon, @Nullable BulletModel model) {
        put(BOMBLET_MODELS, weapon, model);
    }

    private static void put(Map<String, BulletModel> models, String weapon, @Nullable BulletModel model) {
        if (model != null) {
            models.put(weapon, model);
        }
    }

    /** The model to draw the projectile with, or null while its weapon is not known yet. */
    @Nullable
    static BulletModel of(ProjectileEntity projectile) {
        WeaponSpec info = projectile.spec();
        if (info == null) {
            return null;
        }
        BulletModel own = (projectile.syncedBombletFlag() == 1 ? BOMBLET_MODELS : BULLET_MODELS).get(info.name());
        return own != null ? own : DEFAULTS.get(projectile.kind());
    }
}
