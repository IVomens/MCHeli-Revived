package com.ivomens.mcheli.livery;

import com.ivomens.mcheli.config.LiveryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LiveryStoreTest {

    private static final LiveryPolicy POLICY = new LiveryPolicy(true, 1024 * 1024, 512, 2, 10L * 1024 * 1024, false);

    private static byte[] png(int w, int h, int rgb) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, rgb);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void acceptsAndReencodesPng() throws Exception {
        LiveryImages.Sanitized s = LiveryImages.sanitize(png(64, 32, 0xFF336699), POLICY);
        assertEquals(64, s.width());
        assertEquals(32, s.height());
        assertTrue(Livery.isValidId(s.id()));
    }

    @Test
    void rejectsNonPngAndOversize() throws Exception {
        assertThrows(LiveryImages.RejectedException.class, () -> LiveryImages.sanitize("not an image".getBytes(), POLICY));
        assertThrows(LiveryImages.RejectedException.class, () -> LiveryImages.sanitize(png(1024, 16, 0xFFFFFFFF), POLICY));
        assertThrows(LiveryImages.RejectedException.class, () -> LiveryImages.sanitize(png(8, 8, 0xFFFFFFFF), POLICY));
    }

    @Test
    void storesEnforcesQuotaAndPersists(@TempDir Path dir) throws Exception {
        UUID alice = UUID.randomUUID();
        LiveryStore store = new LiveryStore(dir);
        Livery a = store.add(LiveryImages.sanitize(png(32, 32, 0xFF000001), POLICY), "ah-64", "Desert §cCamo", alice, "Alice", POLICY);
        store.add(LiveryImages.sanitize(png(32, 32, 0xFF000002), POLICY), "ah-64", "Navy", alice, "Alice", POLICY);
        assertEquals("Desert cCamo", a.name());
        assertThrows(LiveryImages.RejectedException.class,
                () -> store.add(LiveryImages.sanitize(png(32, 32, 0xFF000003), POLICY), "ah-64", "Third", alice, "Alice", POLICY));

        LiveryStore reloaded = new LiveryStore(dir);
        assertEquals(2, reloaded.all().size());
        assertTrue(reloaded.readImage(a.id()).isPresent());

        assertFalse(reloaded.remove(a.id(), UUID.randomUUID(), false));
        assertTrue(reloaded.remove(a.id(), alice, false));
        assertEquals(1, new LiveryStore(dir).all().size());
    }
}
