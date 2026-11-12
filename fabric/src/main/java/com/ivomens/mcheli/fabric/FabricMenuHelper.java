package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.platform.MenuHelper;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Menus with extra data through Fabric's {@link ExtendedScreenHandlerType}. The extra data travels as
 * the raw bytes the server wrote, so the client's {@link MenuFactory} reads exactly what NeoForge would
 * hand it.
 */
public class FabricMenuHelper implements MenuHelper {

    /** The bytes {@code extraData} wrote, length-prefixed, read back into a buffer of their own. */
    private static final StreamCodec<RegistryFriendlyByteBuf, RegistryFriendlyByteBuf> EXTRA_DATA = StreamCodec.of(
            (out, data) -> {
                out.writeVarInt(data.readableBytes());
                out.writeBytes(data, data.readerIndex(), data.readableBytes());
            },
            in -> {
                int length = in.readVarInt();
                RegistryFriendlyByteBuf data = new RegistryFriendlyByteBuf(Unpooled.buffer(length), in.registryAccess());
                in.readBytes(data, length);
                return data;
            });

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> createMenuType(MenuFactory<T> factory) {
        return new ExtendedScreenHandlerType<>(factory::create, EXTRA_DATA);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData) {
        RegistryFriendlyByteBuf data = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        extraData.accept(data);
        player.openMenu(new WithExtraData(provider, data));
    }

    private record WithExtraData(MenuProvider provider, RegistryFriendlyByteBuf data)
            implements ExtendedScreenHandlerFactory<RegistryFriendlyByteBuf> {

        @Override
        public RegistryFriendlyByteBuf getScreenOpeningData(ServerPlayer player) {
            return this.data;
        }

        @Override
        public @NotNull Component getDisplayName() {
            return this.provider.getDisplayName();
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player player) {
            return this.provider.createMenu(containerId, inventory, player);
        }
    }
}
