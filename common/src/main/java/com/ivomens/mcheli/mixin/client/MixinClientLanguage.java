package com.ivomens.mcheli.mixin.client;

import com.ivomens.mcheli.content.registry.ContentTranslations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Names defined by content files (aircraft, weapons, throwables...) are only known at runtime. When
 * the loaded lang files have no entry for a key, fall back to {@link ContentTranslations}. Lang files
 * from resource packs always win.
 */
@Mixin(ClientLanguage.class)
public abstract class MixinClientLanguage {

    @Shadow
    @Final
    private Map<String, String> storage;

    private static String mcheli$selectedLanguage() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getLanguageManager() != null ? mc.getLanguageManager().getSelected() : ContentTranslations.DEFAULT_LANG;
    }

    /** The runtime name for a key the lang files do not define, or null. */
    private String mcheli$runtimeLookup(String key) {
        if (key == null || ContentTranslations.isEmpty() || this.storage.containsKey(key)) {
            return null;
        }
        return ContentTranslations.lookup(mcheli$selectedLanguage(), key);
    }

    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
    private void mcheli$runtimeName(String key, String defaultValue, CallbackInfoReturnable<String> cir) {
        String name = mcheli$runtimeLookup(key);
        if (name != null) {
            cir.setReturnValue(name);
        }
    }

    @Inject(method = "has", at = @At("HEAD"), cancellable = true)
    private void mcheli$hasRuntimeName(String key, CallbackInfoReturnable<Boolean> cir) {
        if (mcheli$runtimeLookup(key) != null) {
            cir.setReturnValue(true);
        }
    }
}
