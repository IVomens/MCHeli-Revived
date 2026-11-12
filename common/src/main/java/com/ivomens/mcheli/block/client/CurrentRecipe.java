package com.ivomens.mcheli.block.client;

import com.ivomens.mcheli.aircraft.VehicleRecipeList;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.recipe.RecipeList;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** The recipe shown on the drafting table's main page, with its description pages and aircraft info. */
public class CurrentRecipe {

    /** Description pages are looked up as {@code <item>_0.png} up to this count (exclusive). */
    private static final int MAX_DESC_TEXTURES = 20;

    public final RecipeHolder<?> recipe;
    public final int index;
    public final String displayName;
    public final List<ResourceLocation> descTexture;
    private final VehicleSpec acInfo;
    private List<String> infoItem;
    private List<String> infoData;
    private int modelRot;
    private int descMaxPage;
    private int descPage;
    private BakedModelBase model;
    private ResourceLocation modelTexture;

    public List<String> infoData() {
        return this.infoData;
    }

    public List<String> infoItem() {
        return this.infoItem;
    }

    public int modelRot() {
        return this.modelRot;
    }

    public CurrentRecipe(RecipeList list, int index) {
        this.recipe = index >= 0 && index < list.getRecipeListSize() ? list.getRecipe(index) : null;
        this.index = index;
        ItemStack output = getOutput(this.recipe);
        this.displayName = !output.isEmpty() ? output.getHoverName().getString() : "None";
        this.descTexture = findDescTextures(output);
        this.descPage = 0;
        this.descMaxPage = this.descTexture.size();
        VehicleSpec info = null;
        if (list instanceof VehicleRecipeList page && this.recipe != null) {
            info = page.vehicleMadeBy(this.recipe).orElse(null);
            if (info != null) {
                this.descMaxPage++;
                String dir = info.kind().directory();
                this.model = ModelCache.get(dir, info.name());
                if (this.model != null) {
                    this.modelTexture = Ids.of("textures/" + dir + "/" + info.name() + ".png");
                    this.descMaxPage++;
                    // Planes and ships are shown from the side, everything else turns in place.
                    this.modelRot = page.kind() == VehicleKind.PLANE || page.kind() == VehicleKind.SHIP ? 0 : 1;
                }
            }
        }
        this.acInfo = info;
        buildInfoLines(info);
    }

    private static RegistryAccess registryAccess() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : RegistryAccess.EMPTY;
    }

    /** The result item of a recipe (empty for null). */
    public static ItemStack getOutput(@Nullable RecipeHolder<?> recipe) {
        return recipe != null ? recipe.value().getResultItem(registryAccess()) : ItemStack.EMPTY;
    }

    /** The label/value lines of the aircraft info page. */
    private void buildInfoLines(@Nullable VehicleSpec info) {
        this.infoItem = new ArrayList<>();
        this.infoData = new ArrayList<>();
        if (info == null) {
            return;
        }
        addInfoLine("Name", new ItemStack(VehicleTypes.item(info).orElse(null)).getHoverName().getString());
        addInfoLine("HP", String.valueOf(info.file().maxHp()));
        // A UAV's first seat is the remote operator, not a seat on board.
        addInfoLine("Num of Seat",
                String.valueOf(info.isUAV() ? info.crew().riderSeats() - 1 : info.crew().riderSeats()));
        addInfoLine("GunnerMode", yesNo(info.file().features().gunnerMode()));
        addInfoLine("NightVision", yesNo(info.file().features().nightVision()));
        addInfoLine("Radar", yesNo(info.file().features().entityRadar()));
        addInfoLine("Inventory", String.valueOf(info.file().features().inventorySize()));
        if (info.kind() == VehicleKind.PLANE) {
            addInfoLine("VTOL", yesNo(info.file().kindFeatures().vtol().enabled()));
        }
        if (info.kind() == VehicleKind.SHIP) {
            addInfoLine("Submersible", yesNo(info.file().kindFeatures().vtol().enabled()));
        }
        if (info.armament().getWeaponCount() > 0) {
            this.infoItem.add("Armed----------------");
            this.infoData.add("");
            for (int i = 0; i < info.armament().getWeaponCount(); i++) {
                WeaponSpec weaponInfo = Weapons.get(info.armament().getWeaponSetById(i).type).orElse(null);
                if (weaponInfo != null) {
                    addInfoLine(weaponInfo.getWeaponTypeName(), weaponInfo.definition().displayName());
                } else {
                    addInfoLine("ERROR", "Not found weapon " + (i + 1));
                }
            }
        }
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private void addInfoLine(String label, String value) {
        this.infoItem.add(label + " :");
        this.infoData.add(value);
    }

    public void switchNextPage() {
        if (this.descMaxPage >= 2) {
            this.descPage = (this.descPage + 1) % this.descMaxPage;
        } else {
            this.descPage = 0;
        }
    }

    public void switchPrevPage() {
        this.descPage--;
        if (this.descPage < 0) {
            this.descPage = this.descMaxPage >= 2 ? this.descMaxPage - 1 : 0;
        }
    }

    public int getDescCurrentPage() {
        return this.descPage;
    }

    public void setDescCurrentPage(int page) {
        if (this.descMaxPage > 0) {
            this.descPage = page < this.descMaxPage ? page : this.descMaxPage - 1;
        } else {
            this.descPage = 0;
        }
    }

    public int getDescMaxPage() {
        return this.descMaxPage;
    }

    @Nullable
    public ResourceLocation getCurrentPageTexture() {
        return this.descPage < this.descTexture.size() ? this.descTexture.get(this.descPage) : null;
    }

    public BakedModelBase getModel() {
        return this.model;
    }

    public ResourceLocation getModelTexture() {
        return this.modelTexture;
    }

    @Nullable
    public VehicleSpec vehicleSpec() {
        return this.acInfo;
    }

    public boolean isCurrentPageTexture() {
        return this.descPage >= 0 && this.descPage < this.descTexture.size();
    }

    public boolean isCurrentPageModel() {
        return this.acInfo != null && this.model != null && this.descPage == this.descTexture.size();
    }

    public boolean isCurrentPageAcInfo() {
        return this.acInfo != null && this.descPage == this.descMaxPage - 1;
    }

    /**
     * Description pages {@code textures/drafting_table_desc/<item>_<n>.png}, looked up through the
     * resource manager so resource and addon packs can provide them.
     */
    private static List<ResourceLocation> findDescTextures(ItemStack output) {
        List<ResourceLocation> textures = new ArrayList<>();
        if (output.isEmpty()) {
            return textures;
        }
        String itemName = output.getItem().getDescriptionId();
        itemName = itemName.substring(itemName.lastIndexOf('.') + 1);
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        for (int i = 0; i < MAX_DESC_TEXTURES; i++) {
            ResourceLocation texture = ResourceLocation.tryBuild(Ids.MOD_ID,
                    "textures/drafting_table_desc/" + itemName + "_" + i + ".png");
            if (texture != null && resourceManager.getResource(texture).isPresent()) {
                textures.add(texture);
            }
        }
        return textures;
    }
}
