package com.ivomens.mcheli.vehicle.client;

import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelLoader;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.vehicle.definition.PartDef;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses a vehicle's body and part models and publishes them in {@link VehicleModelRegistry}. Pure
 * CPU work, so it runs on loader threads.
 */
public final class VehicleModelLoader {

    /** Upper bound when counting the numbered frames of an exhaust flame texture. */
    private static final int MAX_EXHAUST_FLAME_FRAMES = 100;

    private VehicleModelLoader() {
    }

    /**
     * Loads the models of {@code info}, which may be a freshly reloaded definition that is not
     * published yet, so its parts are read from it rather than from the published definition.
     *
     * @param reload parse the files again even if they are cached
     */
    public static void load(VehicleSpec info, boolean reload) {
        ModelLoader models = ModelLoader.of(reload);
        VehicleKind kind = info.kind();
        String dir = kind.directory();
        IModelCustom body = models.load(dir, info.name());
        if (kind == VehicleKind.PLANE) {
            loadExhaustFlames(info, models);
        }
        Map<String, IModelCustom> parts = new ConcurrentHashMap<>();
        partNames(info.definition().parts()).parallelStream().forEach(name -> {
            IModelCustom model = loadPartModel(models, dir, info.name(), body, name);
            if (model != null) {
                parts.put(name, model);
            }
        });
        VehicleModelRegistry.put(kind, info.name(), new VehicleModels(body, parts));
    }

    private static Set<String> partNames(List<PartDef> parts) {
        Set<String> names = new LinkedHashSet<>();
        addPartNames(parts, names);
        return names;
    }

    private static void addPartNames(List<PartDef> parts, Set<String> names) {
        for (PartDef part : parts) {
            if (part.kind().mayHaveOwnModel() && part.modelName() != null) {
                names.add(part.modelName());
            }
            addPartNames(part.children(), names);
        }
    }

    /**
     * A part's model: none when the body already contains the part as a {@code $name} group,
     * otherwise the separate {@code <vehicle>_<part>} model file, if there is one.
     */
    @Nullable
    private static IModelCustom loadPartModel(ModelLoader models, String dir, String vehicle, @Nullable IModelCustom body, String part) {
        if (body instanceof BakedModelBase baked && baked.containsPart("$" + part)) {
            return null;
        }
        return models.load(dir, vehicle + "_" + part, true);
    }

    private static void loadExhaustFlames(VehicleSpec plane, ModelLoader models) {
        for (VehicleParts.ExhaustFlame flame : plane.kindParts().exhaustFlames()) {
            models.load("exhaustflames", flame.modelName);
            ExhaustFlameTextures.countOnce(flame.texturePrefix, VehicleModelLoader::countExhaustFlameFrames);
        }
    }

    /** Number of consecutive {@code <prefix>0.png, <prefix>1.png, ...} exhaust flame textures. */
    private static int countExhaustFlameFrames(String texturePrefix) {
        int count = 0;
        while (count < MAX_EXHAUST_FLAME_FRAMES
                && resourceExists("textures/exhaustflames/" + texturePrefix + count + ".png")) {
            count++;
        }
        return count;
    }

    private static boolean resourceExists(String path) {
        ResourceLocation resource = ResourceLocation.tryBuild(Ids.MOD_ID, path);
        return resource != null && Minecraft.getInstance().getResourceManager().getResource(resource).isPresent();
    }
}
