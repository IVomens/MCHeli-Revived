package com.ivomens.mcheli.equipment.throwable;

import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.ContentId;
import com.ivomens.mcheli.content.registry.ContentRegistries;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.core.content.schema.ArgbColor;
import com.ivomens.mcheli.util.Color4f;
import net.minecraft.world.item.Item;

import java.util.Optional;

/** The loaded hand grenades and smoke grenades by file name; each has an item of its own. */
public final class Throwables {

    private static final ContentRegistry<ThrowableDefinition, ThrowableDefinition> THROWABLES =
            new ContentRegistry<>(ContentType.THROWABLE, ThrowableDefinition.class, ContentEntry::definition);

    private Throwables() {
    }

    /** Hands the throwable registry to the content pipeline; call once at start-up, before content loads. */
    public static void registerContent() {
        ContentRegistries.register(THROWABLES);
    }

    public static ContentRegistry<ThrowableDefinition, ThrowableDefinition> registry() {
        return THROWABLES;
    }

    public static Optional<ThrowableDefinition> get(String name) {
        return THROWABLES.get(name);
    }

    /** The throwable an item was registered for. */
    public static Optional<ThrowableDefinition> get(Item item) {
        return ContentBindings.contentOf(ContentType.THROWABLE, item).map(ContentId::name).flatMap(Throwables::get);
    }

    public static ContentId id(ThrowableDefinition throwable) {
        return new ContentId(ContentType.THROWABLE, throwable.name());
    }

    /** The colour of the grenade's smoke. */
    public static Color4f smokeColor(ThrowableDefinition throwable) {
        ArgbColor color = throwable.smoke().color();
        return new Color4f(color.a(), color.r(), color.g(), color.b());
    }
}
