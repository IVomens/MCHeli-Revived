package com.ivomens.mcheli.content.source;

import com.ivomens.mcheli.content.bind.TxtReader;
import com.ivomens.mcheli.content.bind.YamlLineLocator;
import com.ivomens.mcheli.content.bind.YamlReader;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.def.vehicle.VehicleLinks.Linked;
import com.ivomens.mcheli.content.def.vehicle.VehicleLinks.Unresolved;
import com.ivomens.mcheli.content.def.vehicle.VehicleLinks;
import com.ivomens.mcheli.content.def.vehicle.VehicleSchema;
import com.ivomens.mcheli.content.registry.ContentRegistries;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;

import java.util.List;
import java.util.function.Predicate;

/**
 * Reads a vehicle file in either format and links it to the weapons and HUDs it names. The formats treat a
 * weapon that is not loaded differently, as they always have: a YAML file that mounts one fails to load, a
 * legacy TXT file loads without that mount. A HUD that is not loaded leaves its seat without a HUD.
 */
public final class VehicleFiles {

    private VehicleFiles() {
    }

    public static VehicleFile readYaml(VehicleKind kind, String name, String file, List<String> lines) {
        Linked linked = link(YamlReader.read(VehicleSchema.forKind(kind), name, file, lines));
        if (!linked.weapons().isEmpty()) {
            Unresolved weapon = linked.weapons().getFirst();
            throw new ContentParseException("Unknown weapon type '" + weapon.name()
                    + "' (no file with that name in a weapons folder)", weapon.path(),
                    YamlLineLocator.find(lines, weapon.path()), null);
        }
        warnAboutHuds(linked, file);
        return linked.file();
    }

    /** @param reload true when the file is read again in game, which reads only the reloadable lines */
    public static VehicleFile readTxt(VehicleKind kind, String name, String file, List<String> lines,
                                      boolean reload) {
        Linked linked = link(TxtReader.read(VehicleSchema.forKind(kind), name, file, lines, reload));
        for (Unresolved weapon : linked.weapons()) {
            ModLogger.get().warn("{}: weapon '{}' is not loaded, so its AddWeapon line is left out", file,
                    weapon.name());
        }
        warnAboutHuds(linked, file);
        return linked.file();
    }

    private static Linked link(VehicleFile file) {
        Predicate<String> hudLoaded = Services.PLATFORM.isClient()
                ? hud -> ContentRegistries.contains(ContentType.HUD, hud) : null;
        return VehicleLinks.link(file, weapon -> ContentRegistries.contains(ContentType.WEAPON, weapon), hudLoaded);
    }

    private static void warnAboutHuds(Linked linked, String file) {
        for (Unresolved hud : linked.huds()) {
            ModLogger.get().warn("{} {}, so the seat shows none", file, hud.describe());
        }
    }
}
