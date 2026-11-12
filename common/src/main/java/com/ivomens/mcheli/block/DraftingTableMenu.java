package com.ivomens.mcheli.block;

import com.ivomens.mcheli.aircraft.VehicleRecipeList;
import com.ivomens.mcheli.config.DebugLog;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.recipe.ContentRecipes;
import com.ivomens.mcheli.content.recipe.InventoryIngredients;
import com.ivomens.mcheli.content.recipe.RecipeList;
import com.ivomens.mcheli.registry.McheliBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/** Drafting table menu: the player inventory plus one output slot. */
public class DraftingTableMenu extends AbstractContainerMenu {

    private static final double MAX_DISTANCE_SQ = 12.0 * 12.0;
    private static final int PLAYER_SLOTS = 36;

    public final Player player;
    public final int posX;
    public final int posY;
    public final int posZ;
    public final int outputSlotIndex;
    private final ResultContainer outputSlot = new ResultContainer();

    public DraftingTableMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModBlockEntities.DRAFTING_TABLE_MENU.get(), containerId);
        this.player = inventory.player;
        this.posX = pos.getX();
        this.posY = pos.getY();
        this.posZ = pos.getZ();

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlot(new Slot(inventory, 9 + x + y * 9, 30 + x * 18, 140 + y * 18));
            }
        }

        for (int x = 0; x < 9; x++) {
            this.addSlot(new Slot(inventory, x, 30 + x * 18, 198));
        }

        this.outputSlotIndex = this.slots.size();
        this.addSlot(new Slot(this.outputSlot, 0, 178, 90) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        Block block = player.level().getBlockState(new BlockPos(this.posX, this.posY, this.posZ)).getBlock();
        return (block == McheliBlocks.DRAFTING_TABLE.get() || block == McheliBlocks.LIT_DRAFTING_TABLE.get())
                && player.distanceToSqr(this.posX + 0.5, this.posY + 0.5, this.posZ + 0.5) <= MAX_DISTANCE_SQ;
    }

    /** Only the output can be shift-clicked, into the player inventory. */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem() || slotIndex != this.outputSlotIndex) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!this.moveItemStackTo(stack, 0, PLAYER_SLOTS, true)) {
            return ItemStack.EMPTY;
        }
        slot.onQuickCraft(stack, original);
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (player.level().isClientSide) return;
        // Give back a created item that was not taken out.
        ItemStack stack = this.outputSlot.removeItemNoUpdate(0);
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false, false);
        }
    }

    public void createRecipeItem(@Nullable RecipeHolder<?> recipe) {
        boolean creative = this.player.getAbilities().instabuild;
        if (recipe == null || (this.getSlot(this.outputSlotIndex).hasItem() && !creative)) return;

        ItemStack output = recipe.value().getResultItem(this.player.level().registryAccess());
        if (output.isEmpty() || !(creative || InventoryIngredients.consume(this.player, recipe.value()))) {
            DebugLog.log(this.player.level(), "Drafting table cannot create " + recipe.id());
            return;
        }
        this.getSlot(this.outputSlotIndex).set(output.copy());
    }

    /** The recipe lists the table shows, items page first. */
    public static List<RecipeList> recipeLists() {
        return List.of(
                ContentRecipes.getInstance(),
                VehicleRecipeList.of(VehicleKind.HELICOPTER),
                VehicleRecipeList.of(VehicleKind.PLANE),
                VehicleRecipeList.of(VehicleKind.VEHICLE),
                VehicleRecipeList.of(VehicleKind.TANK),
                VehicleRecipeList.of(VehicleKind.SHIP));
    }

    /**
     * The recipe with this id if the table lists it, else null. The client names the recipe, so
     * anything else in the recipe manager (vanilla crafting, smelting...) must not be craftable here.
     */
    @Nullable
    public static RecipeHolder<?> findListedRecipe(ResourceLocation id) {
        for (RecipeList list : recipeLists()) {
            for (int i = 0; i < list.getRecipeListSize(); i++) {
                RecipeHolder<?> recipe = list.getRecipe(i);
                if (recipe.id().equals(id)) {
                    return recipe;
                }
            }
        }
        return null;
    }
}
