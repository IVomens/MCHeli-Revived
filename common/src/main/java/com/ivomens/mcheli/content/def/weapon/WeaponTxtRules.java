package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.Values;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

import static com.ivomens.mcheli.core.content.schema.Codecs.splitTxt;

/**
 * Legacy TXT weapon lines that set more than one value, add to a list, or change another value on the side.
 * They are applied in line order, so their quirks are kept exactly:
 * <ul>
 *   <li>{@code Type = bomb} or {@code dispenser} sets both gravities to -0.03, so a {@code Gravity} line
 *       before it is lost and one after it wins;</li>
 *   <li>only the first three valid {@code Item} lines count;</li>
 *   <li>a line with too few values is ignored.</li>
 * </ul>
 */
final class WeaponTxtRules {

    /** The gravity legacy TXT gives bombs and dispensers when their type is read. */
    private static final float DROPPED_GRAVITY = -0.03F;
    private static final int MAX_RECOIL_BUFFER = 10_000;
    private static final int MAX_ITEM_DAMAGE = 100_000_000;
    private static final int MAX_SMOKE_PUFFS = 1000;

    private WeaponTxtRules() {
    }

    /** {@code Type = bomb}; see the class comment for the gravity side effect. */
    static void type(Values values, String text, ReadContext context) {
        String type = text.toLowerCase(Locale.ROOT);
        values.set(WeaponSchema.TYPE, type);
        if (type.equals("bomb") || type.equals("dispenser")) {
            values.set(BallisticsSchema.GRAVITY, DROPPED_GRAVITY);
            values.set(BallisticsSchema.GRAVITY_IN_WATER, DROPPED_GRAVITY);
        }
    }

    /**
     * {@code RecoilBufCount = count, speed}: the speed is read one lower than written, only for a buffer
     * longer than two ticks, and is at most half the count.
     */
    static void recoilBuffer(Values values, String text, ReadContext context) {
        String[] parts = splitTxt(text);
        int count = Codecs.clamp(Codecs.parseInt(parts[0]), 1, MAX_RECOIL_BUFFER, context);
        values.set(RecoilSchema.BUFFER_COUNT, count);
        if (parts.length >= 2 && count > 2) {
            int speed = Codecs.clamp(Codecs.parseInt(parts[1]), 1, MAX_RECOIL_BUFFER, context) - 1;
            values.set(RecoilSchema.BUFFER_SPEED, Math.min(speed, count / 2));
        }
    }

    /** {@code Item = count, name[, damage]}: adds an ammo item, up to three. */
    static void ammoItem(Values values, String text, ReadContext context) {
        String[] parts = text.split("\\s*,\\s*");
        boolean full = values.get(AmmoSchema.ITEMS).size() >= AmmoItem.MAX_TXT_ITEMS;
        if (parts.length < 2 || parts[1].isEmpty() || full) {
            return;
        }
        int count = Codecs.clamp(Codecs.parseInt(parts[0]), 1, 64, context);
        int damage = parts.length >= 3 ? Codecs.clamp(Codecs.parseInt(parts[2]), 0, MAX_ITEM_DAMAGE, context) : 0;
        values.append(AmmoSchema.ITEMS, AmmoItem.of(count, parts[1].toLowerCase(Locale.ROOT).trim(), damage));
    }

    /** {@code AddMuzzleFlash = distance, size, age, a, r, g, b} with channels from 0 to 255. */
    static void muzzleFlash(Values values, String text, ReadContext context) {
        String[] parts = splitTxt(text);
        if (parts.length >= 7) {
            values.append(RenderSchema.MUZZLE_FLASHES, new MuzzleFlashSpec(number(parts[0]), number(parts[1]), 0.0F,
                    Codecs.parseInt(parts[2]), channel(parts[3]), channel(parts[4]), channel(parts[5]),
                    channel(parts[6]), 1));
        }
    }

    /** {@code AddMuzzleFlashSmoke = distance, count, size, range, age, a, r, g, b}. */
    static void muzzleSmoke(Values values, String text, ReadContext context) {
        String[] parts = splitTxt(text);
        if (parts.length >= 9) {
            int count = Codecs.clamp(Codecs.parseInt(parts[1]), 1, MAX_SMOKE_PUFFS, context);
            values.append(RenderSchema.MUZZLE_SMOKE, new MuzzleFlashSpec(number(parts[0]), number(parts[2]),
                    number(parts[3]), Codecs.parseInt(parts[4]), channel(parts[5]), channel(parts[6]),
                    channel(parts[7]), channel(parts[8]), count));
        }
    }

    /** {@code SetCartridge = model[, acceleration, yaw, pitch, scale, gravity, bound]}. */
    static void cartridge(Values values, String text, ReadContext context) {
        String[] parts = text.split("\\s*,\\s*");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }
        values.set(RenderSchema.SPENT_CASINGS, new CasingSpec(parts[0].toLowerCase(Locale.ROOT),
                optionalNumber(parts, 1, 0.0F), optionalNumber(parts, 2, 0.0F), optionalNumber(parts, 3, 0.0F),
                optionalNumber(parts, 6, CasingSpec.DEFAULT_BOUND),
                optionalNumber(parts, 5, CasingSpec.DEFAULT_GRAVITY),
                optionalNumber(parts, 4, CasingSpec.DEFAULT_SCALE)));
    }

    /** {@code BulletColor} and {@code SmokeColor}: {@code a, r, g, b} from 0 to 255. */
    static void color(Values values, String text, ReadContext context) {
        String[] parts = text.split("\\s*,\\s*");
        if (parts.length >= 4) {
            values.set(RenderSchema.BULLET_COLOR, RenderSchema.txtColor(parts, context));
        }
    }

    /** {@code BulletColorInWater}: {@code a, r, g, b} from 0 to 255. */
    static void colorInWater(Values values, String text, ReadContext context) {
        String[] parts = text.split("\\s*,\\s*");
        if (parts.length >= 4) {
            values.set(RenderSchema.BULLET_COLOR_IN_WATER, RenderSchema.txtColor(parts, context));
        }
    }

    /** {@code DispenseItem = item[, damage]}. */
    static void dispenseItem(Values values, String text, ReadContext context) {
        String[] parts = text.split("\\s*,\\s*");
        if (parts.length >= 2) {
            values.set(WeaponTypeSchema.DISPENSE_META,
                    Codecs.clamp(Codecs.parseInt(parts[1]), 0, MAX_ITEM_DAMAGE, context));
        }
        values.set(WeaponTypeSchema.DISPENSE_LOCATION, parts[0].toLowerCase(Locale.ROOT).trim());
    }

    /** {@code DamageFactor = target, factor}; see {@link DamageFactors}. */
    static void damageFactor(Values values, String text, ReadContext context) {
        values.set(WeaponSchema.DAMAGE_FACTOR, DamageFactors.withTxtLine(values.get(WeaponSchema.DAMAGE_FACTOR),
                text, context));
    }

    /** {@code AddPotionEffect = potion, duration, amplifier[, start, end]}; an unknown potion is ignored. */
    static void potionEffect(Values values, String text, ReadContext context) {
        String[] parts = text.split("\\s*,\\s*");
        if (parts.length < 3) {
            return;
        }
        Optional<ResourceLocation> effect = PotionEffectSpec.lookUp(parts[0]);
        if (effect.isEmpty()) {
            return;
        }
        boolean banded = parts.length >= 5;
        values.append(EffectsSchema.POTION_EFFECTS, new PotionEffectSpec(effect.get(), Codecs.parseInt(parts[1]),
                Codecs.parseInt(parts[2]), banded ? Codecs.parseInt(parts[3]) : -1,
                banded ? Codecs.parseInt(parts[4]) : -1));
    }

    /** {@code BulletDecay = Segmented, distance, factor, ...}; each line adds a curve. */
    static void bulletDecay(Values values, String text, ReadContext context) {
        values.append(EffectsSchema.BULLET_DECAY, DecayCurve.fromTxt(text));
    }

    private static float number(String text) {
        return Codecs.parseFloat(text);
    }

    private static float channel(String text) {
        return Codecs.parseFloat(text) / 255.0F;
    }

    private static float optionalNumber(String[] parts, int index, float fallback) {
        return parts.length > index ? Codecs.parseFloat(parts[index]) : fallback;
    }
}
