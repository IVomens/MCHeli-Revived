package com.ivomens.mcheli.livery.client;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UvGuideImageTest {

    @Test
    void partNamesAreReadable() {
        assertEquals("Rotor 0", UvGuideImage.friendlyName("$rotor0", 1));
        assertEquals("Body 1", UvGuideImage.friendlyName("body1", 1));
        assertEquals("Weapon 0 1", UvGuideImage.friendlyName("$weapon0_1", 1));
        assertEquals("E2(M)", UvGuideImage.friendlyName("E2(M)", 1));
        assertEquals("Part 7", UvGuideImage.friendlyName("\u0081\u0090é", 7));
    }

    @Test
    void labelsNeverOverlapAndStayInRange() {
        int[] tops = UvGuideImage.spread(new int[]{5, 6, 7, 300, 990, 995}, 20, 30, 1000);
        for (int i = 1; i < tops.length; i++) {
            assertTrue(tops[i] >= tops[i - 1] + 20, "labels " + (i - 1) + " and " + i + " overlap");
        }
        assertTrue(tops[0] >= 30);
        assertTrue(tops[tops.length - 1] + 20 <= 1000);
        assertEquals(300, tops[3]);
    }

    @Test
    void guideHasRoomForTextureAndLabels() {
        BufferedImage texture = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        List<UvGuideImage.Part> parts = List.of(
                new UvGuideImage.Part("$body", new float[]{0, 0, 0.5F, 0, 0.5F, 0.5F, 0, 0.5F}),
                new UvGuideImage.Part("$rotor0", new float[]{0.5F, 0.5F, 1, 0.5F, 1, 1, 0.5F, 1}),
                new UvGuideImage.Part("empty", new float[]{0, 0, 0, 0, 0, 0, 0, 0}));
        BufferedImage guide = UvGuideImage.draw(texture, "Test", parts);
        assertTrue(guide.getWidth() > 1024, "texture is scaled up and a label margin added");
        assertTrue(guide.getHeight() >= 1024);
    }
}
