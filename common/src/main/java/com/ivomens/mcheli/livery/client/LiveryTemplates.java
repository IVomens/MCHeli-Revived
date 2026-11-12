package com.ivomens.mcheli.livery.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.BakedPart;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the painting templates of a vehicle to {@code <gameDir>/mcheli_liveries/templates/}:
 * {@code <type>_texture.png}, the original texture byte for byte, and {@code <type>_guide.png},
 * the {@link UvGuideImage} that shows which area of the texture belongs to which part.
 */
public final class LiveryTemplates {

    private static final int VERTICES_PER_QUAD = 4;

    static {
        // the guide is drawn with java.awt; it never needs a window
        System.setProperty("java.awt.headless", "true");
    }

    private LiveryTemplates() {
    }

    /** What to export, captured on the render thread so the work itself can run anywhere. */
    private record Source(String vehicleType, String title, String modelDirectory, String modelName,
                          ResourceLocation texture) {
    }

    /**
     * Exports the templates of the aircraft's current texture on the IO pool.
     *
     * @return the template folder once both files are written
     */
    public static CompletableFuture<Path> export(AircraftEntity aircraft) {
        VehicleSpec info = aircraft.spec();
        if (info == null) {
            return CompletableFuture.failedFuture(new IOException("Unknown vehicle"));
        }
        String textureName = aircraft.appearance().texture().isEmpty() ? info.name() : aircraft.appearance().texture();
        String directory = info.kind().directory();
        ResourceLocation texture = ResourceLocation.tryBuild(Ids.MOD_ID,
                "textures/" + directory + "/" + textureName + ".png");
        if (texture == null) {
            return CompletableFuture.failedFuture(new IOException("Bad texture name " + textureName));
        }
        String title = info.file().displayName().name() == null || info.file().displayName().name().isEmpty()
                ? info.name() : info.file().displayName().name();
        Source source = new Source(info.name(), title, directory, info.name(), texture);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return write(source);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Util.ioPool());
    }

    private static Path write(Source source) throws IOException {
        byte[] png = readResource(source.texture());
        BufferedImage texture = ImageIO.read(new ByteArrayInputStream(png));
        if (texture == null) {
            throw new IOException("Unreadable texture " + source.texture());
        }
        Path folder = LiveryFiles.templates();
        Files.createDirectories(folder);
        String baseName = fileName(source.vehicleType());
        Files.write(folder.resolve(baseName + "_texture.png"), png);

        BufferedImage guide = UvGuideImage.draw(texture, source.title(), modelParts(source));
        ImageIO.write(guide, "png", folder.resolve(baseName + "_guide.png").toFile());
        return folder;
    }

    private static byte[] readResource(ResourceLocation location) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            throw new IOException("Missing texture " + location);
        }
        try (InputStream in = resource.get().open()) {
            return in.readAllBytes();
        }
    }

    /** UVs of every part of the body model, in file order. */
    private static List<UvGuideImage.Part> modelParts(Source source) {
        IModelCustom model = ModelCache.load(source.modelDirectory(), source.modelName(), true);
        List<UvGuideImage.Part> parts = new ArrayList<>();
        if (!(model instanceof BakedModelBase baked)) {
            return parts;
        }
        for (BakedPart part : baked.topLevelParts()) {
            // placeholder parts carry no paintable surface worth labelling
            if (part.name().toLowerCase(Locale.ROOT).contains("dummy")) continue;
            parts.add(new UvGuideImage.Part(part.name(), uvs(part.bakedData())));
        }
        return parts;
    }

    /** Picks the u/v pair out of each baked vertex; whole quads only. */
    private static float[] uvs(float[] baked) {
        int quads = baked.length / (LegacyGL.BAKED_STRIDE * VERTICES_PER_QUAD);
        int vertices = quads * VERTICES_PER_QUAD;
        float[] uvs = new float[vertices * 2];
        for (int vertex = 0; vertex < vertices; vertex++) {
            uvs[vertex * 2] = baked[vertex * LegacyGL.BAKED_STRIDE + 3];
            uvs[vertex * 2 + 1] = baked[vertex * LegacyGL.BAKED_STRIDE + 4];
        }
        return uvs;
    }

    /** Lower-case, file-name-safe version of a vehicle type. */
    static String fileName(String vehicleType) {
        String safe = vehicleType.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return safe.isEmpty() ? "vehicle" : safe;
    }
}
