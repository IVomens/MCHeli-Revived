package com.ivomens.mcheli.content.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What loaded content is bound to once the game's registries exist, kept apart from the definitions so those
 * stay immutable: the item each vehicle and throwable registered, the item stacks a weapon is reloaded with,
 * and the item a dispenser weapon uses. Keyed by {@link ContentId}, so a reloaded definition keeps its
 * bindings.
 */
public final class ContentBindings {

    private static final Map<ContentId, Item> ITEMS = new ConcurrentHashMap<>();
    private static final Map<Item, ContentId> CONTENT_BY_ITEM = new ConcurrentHashMap<>();
    private static final Map<ContentId, List<RoundStack>> ROUNDS = new ConcurrentHashMap<>();
    private static final Map<ContentId, Item> DISPENSED = new ConcurrentHashMap<>();

    /**
     * One item a weapon is reloaded with.
     *
     * @param count how many of it one reload takes
     * @param stack one of the item, or an empty stack when the item does not exist
     */
    public record RoundStack(int count, ItemStack stack) {
    }

    private ContentBindings() {
    }

    /** Records the item registered for a vehicle or throwable. */
    public static void bindItem(ContentId id, Item item) {
        ITEMS.put(id, item);
        CONTENT_BY_ITEM.put(item, id);
    }

    /** The item registered for a vehicle or throwable, if items are registered. */
    public static Optional<Item> item(ContentId id) {
        return Optional.ofNullable(ITEMS.get(id));
    }

    /** The content an item was registered for, among the given type. */
    public static Optional<ContentId> contentOf(ContentType type, Item item) {
        return Optional.ofNullable(CONTENT_BY_ITEM.get(item)).filter(id -> id.type() == type);
    }

    public static void bindRounds(ContentId weapon, List<RoundStack> rounds) {
        ROUNDS.put(weapon, List.copyOf(rounds));
    }

    /** The items a weapon is reloaded with; empty until items are bound. */
    public static List<RoundStack> rounds(ContentId weapon) {
        return ROUNDS.getOrDefault(weapon, List.of());
    }

    public static void bindDispensedItem(ContentId weapon, Item item) {
        DISPENSED.put(weapon, item);
    }

    /** The item a dispenser weapon uses, if it is bound. */
    public static Optional<Item> dispensedItem(ContentId weapon) {
        return Optional.ofNullable(DISPENSED.get(weapon));
    }
}
