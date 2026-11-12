package com.ivomens.mcheli.admin;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/** The pieces the admin commands' messages are made of. Every sentence is a translation key. */
final class AdminText {

    private AdminText() {
    }

    static MutableComponent text(String key, Object... args) {
        return Component.translatable("commands.mcheli." + key, args);
    }

    static MutableComponent error(String key, Object... args) {
        return text(key, args).withStyle(ChatFormatting.RED);
    }

    /** {@code x y z} in the entity's dimension; clicking it teleports there (for operators). */
    static MutableComponent position(Entity entity) {
        String coordinates = String.format(Locale.ROOT, "%d %d %d", entity.getBlockX(), entity.getBlockY(),
                entity.getBlockZ());
        String dimension = entity.level().dimension().location().toString();
        String command = "/execute in " + dimension + " run tp @s " + coordinates;
        return Component.literal(dimension + " " + coordinates).withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("teleport_hint"))));
    }

    /** The vehicle's type name, kind and owner. */
    static MutableComponent describe(AircraftEntity vehicle) {
        String owner = vehicle.ownership().hasOwner() ? vehicle.ownership().ownerName() : "-";
        MutableComponent line = text("vehicle", vehicle.getName(), kind(vehicle), owner);
        if (vehicle.ownership().isLocked()) {
            line.append(" ").append(text("locked_tag").withStyle(ChatFormatting.GOLD));
        }
        return line;
    }

    static MutableComponent kind(AircraftEntity vehicle) {
        return Component.translatable("mcheli.kind." + vehicle.kind().directory());
    }

    static String millis(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
