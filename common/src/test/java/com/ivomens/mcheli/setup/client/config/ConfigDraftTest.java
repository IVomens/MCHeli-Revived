package com.ivomens.mcheli.setup.client.config;

import com.ivomens.mcheli.config.ControlSettings;
import com.ivomens.mcheli.config.DebugSettings;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.config.RenderSettings;
import com.ivomens.mcheli.setup.client.config.ConfigDraft.HitMarkChannel;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDraftTest {

    private static McheliSettings defaults;

    /** The default config names blocks, so the registries must exist. */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        defaults = McheliSettings.defaults();
    }

    @Test
    void sliderValuesAreSavedAsTheSlidersShowThem() {
        McheliSettings config = defaults
                .with(ControlSettings.MOUSE_SENSITIVITY, 10.07)
                .with(RenderSettings.ENTITY_MARKER_SIZE, 12.6)
                .with(RenderSettings.HIT_MARK_COLOR, "200, 250, 10, 0");
        McheliSettings saved = new ConfigDraft(config, false).applyTo(defaults);

        // On the 0.1 step, then cut to one decimal in float, as the options screen always did.
        assertEquals((double) ((float) (int) (0.1F * 101 * 10) / 10), saved.controls().mouseSensitivity());
        assertEquals(13.0, saved.render().entityMarkerSize());
        // Colour channels sit on steps of 16, clamped to 255.
        assertEquals("208, 255, 16, 0", saved.get(RenderSettings.HIT_MARK_COLOR));
    }

    @Test
    void savingWritesWhatTheScreenShowsOntoTheLatestConfig() {
        ConfigDraft draft = new ConfigDraft(defaults, false);
        draft.set(ControlSettings.INVERT_MOUSE, true);
        draft.setKey(KeySettings.FLARE, 34);
        McheliSettings changedMeanwhile = defaults.with(KeySettings.CHAFF, 20).with(DebugSettings.TEXTURE_ALPHA, 0.5);

        McheliSettings saved = draft.applyTo(changedMeanwhile);

        assertTrue(saved.controls().invertMouse());
        assertEquals(34, saved.get(KeySettings.FLARE));
        assertEquals(20, saved.get(KeySettings.CHAFF), "a key the screen does not show is left alone");
        assertEquals(0.5, saved.debug().textureAlpha(), "the texture alpha is only saved in single player");
    }

    @Test
    void theTextureAlphaSliderWorksInZeroTo255() {
        ConfigDraft draft = new ConfigDraft(defaults.with(DebugSettings.TEXTURE_ALPHA, 0.5), true);
        assertEquals(128.0F, draft.textureAlpha(), 0.5F);
        draft.setTextureAlpha(51.0F);
        assertEquals(0.2, draft.applyTo(defaults).debug().textureAlpha(), 1e-6);
    }

    @Test
    void theHitMarkPreviewFollowsTheSliders() {
        ConfigDraft draft = new ConfigDraft(defaults, false);
        draft.setHitMark(HitMarkChannel.GREEN, 128.0F);
        assertEquals(0xFFFF8000, draft.hitMarkArgb());
        assertFalse(draft.get(ControlSettings.INVERT_MOUSE));
    }
}
