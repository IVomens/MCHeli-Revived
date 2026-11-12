package com.ivomens.mcheli.block.client;

import com.ivomens.mcheli.aircraft.VehicleRecipeList;
import com.ivomens.mcheli.block.DraftingTableMenu;
import com.ivomens.mcheli.block.net.DraftingTableCraftMessage;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.recipe.ContentRecipes;
import com.ivomens.mcheli.content.recipe.InventoryIngredients;
import com.ivomens.mcheli.content.recipe.RecipeList;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.platform.client.ClientServices;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McContainerScreen;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.util.Ids;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The drafting table. The recipe page shows the selected recipe with its ingredients, the Create
 * button and a description, aircraft data or a turnable model preview on the right; the list page
 * browses a whole recipe list ({@link RecipeBrowser}). The search field filters every list by name.
 */
public final class DraftingTableScreen extends McContainerScreen<DraftingTableMenu> {

    private enum Page { RECIPE, LIST }

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Ids.MOD_ID,
            "textures/gui/drafting_table.png");
    private static final int TEXTURE_SIZE = 512;
    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 240;
    private static final int INFO_TEXT_COLOR = 0xFF6F2A00;
    /** Clicks are ignored for a few ticks after a button so a double click does not skip recipes. */
    private static final int CLICK_DELAY_TICKS = 3;
    private static final int SEARCH_MAX_LENGTH = 50;
    /** Default preview angles, and those of planes and ships, which are modelled facing another way. */
    private static final float[] SIDE_VIEW = {180.0F, 90.0F};
    private static final float[] TOP_VIEW = {90.0F, 180.0F};

    private final Player player;
    private final ModelPreview preview = new ModelPreview();
    private final RecipeBrowser browser;
    private final List<AbstractWidget> recipePage = new ArrayList<>();
    private Page page = Page.RECIPE;
    private CurrentRecipe current;
    private EditBox search;
    private Button create;
    private Button previousRecipe;
    private Button nextRecipe;
    private Button previousPage;
    private Button nextPage;
    private int clickDelay;

    public DraftingTableScreen(DraftingTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.player = inventory.player;
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        RecipeList first = defaultList();
        this.browser = new RecipeBrowser(first, this::pickFromList);
        this.preview.reset(SIDE_VIEW[0], SIDE_VIEW[1]);
        this.select(new CurrentRecipe(first, 0));
    }

    private static RecipeList defaultList() {
        for (RecipeList list : DraftingTableMenu.recipeLists()) {
            if (list.getRecipeListSize() > 0) {
                return list;
            }
        }
        return ContentRecipes.getInstance();
    }

    @Override
    protected void build() {
        this.recipePage.clear();
        Layout lists = Layout.column(this.leftPos + 20, this.topPos + 20, this::addRecipeWidget);
        lists.add(this.listButton("helicopters", VehicleRecipeList.of(VehicleKind.HELICOPTER), SIDE_VIEW));
        lists.add(this.listButton("planes", VehicleRecipeList.of(VehicleKind.PLANE), TOP_VIEW));
        lists.add(this.listButton("vehicles", VehicleRecipeList.of(VehicleKind.VEHICLE), SIDE_VIEW));
        lists.add(this.listButton("tanks", VehicleRecipeList.of(VehicleKind.TANK), SIDE_VIEW));
        lists.add(this.listButton("items", ContentRecipes.getInstance(), null));
        lists.add(this.listButton("ships", VehicleRecipeList.of(VehicleKind.SHIP), TOP_VIEW));

        this.create = this.addRecipeWidget(this.button("create", 50, this::craft));
        this.create.setPosition(this.leftPos + 120, this.topPos + 89);
        Layout steps = Layout.row(this.leftPos + 120, this.topPos + 111, this::addRecipeWidget).gap(-1);
        this.previousRecipe = steps.add(Widgets.button(Component.literal("<<"), 36, 20, () -> this.click(() -> this.step(-1))));
        this.nextRecipe = steps.add(Widgets.button(Component.literal(">>"), 35, 20, () -> this.click(() -> this.step(1))));
        Layout pages = Layout.row(this.leftPos + 210, this.topPos + 210, this::addRecipeWidget);
        this.previousPage = pages.add(this.button("previous_page", 60, () -> this.current.switchPrevPage()));
        this.nextPage = pages.add(this.button("next_page", 60, () -> this.current.switchNextPage()));

        this.browser.build(this::add, this.leftPos, this.topPos);

        String text = this.search == null ? "" : this.search.getValue();
        this.search = this.add(Widgets.text(100, 12, SEARCH_MAX_LENGTH, Component.translatable("mcheli.drafting.search")));
        this.search.setPosition(this.leftPos + 10, this.topPos + 5);
        this.search.setValue(text);
        this.search.setResponder(value -> this.applySearch(value.trim()));
        this.show(this.page);
    }

    private <W extends AbstractWidget> W addRecipeWidget(W widget) {
        this.recipePage.add(widget);
        return this.add(widget);
    }

    private Button button(String name, int width, Runnable onPress) {
        return Widgets.button(Component.translatable("mcheli.drafting." + name), width, 20, () -> this.click(onPress));
    }

    private Button listButton(String name, RecipeList list, float[] view) {
        Button button = this.button(name, 90, () -> {
            if (view != null) {
                this.preview.reset(view[0], view[1]);
            }
            this.openList(list);
        });
        button.active = list.getRecipeListSize() > 0;
        return button;
    }

    /** Runs a button's action unless another button was pressed a moment ago. */
    private void click(Runnable action) {
        if (this.clickDelay == 0) {
            this.clickDelay = CLICK_DELAY_TICKS;
            action.run();
        }
    }

    private void show(Page target) {
        this.page = target;
        for (AbstractWidget widget : this.recipePage) {
            widget.visible = target == Page.RECIPE;
        }
        this.browser.setVisible(target == Page.LIST);
        this.updatePageButtons();
    }

    private void updatePageButtons() {
        if (this.nextPage != null) {
            boolean shown = this.page == Page.RECIPE && this.current != null && this.current.getDescMaxPage() > 1;
            this.nextPage.visible = shown;
            this.previousPage.visible = shown;
        }
    }

    private void openList(RecipeList list) {
        this.switchList(list);
        this.show(Page.LIST);
    }

    private void pickFromList(int index) {
        this.click(() -> {
            if (index < this.browser.list().getRecipeListSize()) {
                this.select(new CurrentRecipe(this.browser.list(), index));
                this.show(Page.RECIPE);
            }
        });
    }

    private void applySearch(String text) {
        if (text.isEmpty()) {
            this.switchList(defaultList());
            return;
        }
        RecipeBrowser.Filtered filtered = new RecipeBrowser.Filtered(DraftingTableMenu.recipeLists(), text);
        if (filtered.getRecipeListSize() > 0) {
            this.switchList(filtered);
            this.show(Page.LIST);
        }
    }

    /** Browses another list from its first recipe, or scrolls the same list back to the selected one. */
    private void switchList(RecipeList list) {
        if (this.browser.list() != list) {
            this.select(new CurrentRecipe(list, 0));
            this.browser.setList(list);
        } else if (this.current != null) {
            this.browser.scrollTo(this.current.index);
        }
    }

    private void select(CurrentRecipe recipe) {
        boolean sameModel = this.current != null && this.current.recipe != null && recipe.recipe != null
                && ItemStack.isSameItem(CurrentRecipe.getOutput(this.current.recipe), CurrentRecipe.getOutput(recipe.recipe));
        this.preview.show(sameModel);
        this.current = recipe;
        this.updatePageButtons();
        this.updateCreateButton();
    }

    /** The next or previous recipe, on the same description page unless that page is a picture. */
    private void step(int direction) {
        int size = this.browser.list().getRecipeListSize();
        if (size <= 0) {
            return;
        }
        int descriptionPage = this.current.isCurrentPageTexture() ? 0 : this.current.getDescCurrentPage();
        this.select(new CurrentRecipe(this.browser.list(), Math.floorMod(this.current.index + direction, size)));
        this.current.setDescCurrentPage(descriptionPage);
    }

    private void craft() {
        Network.sendToServer(DraftingTableCraftMessage.of(this.current.recipe));
    }

    private void updateCreateButton() {
        if (this.create == null) {
            return;
        }
        boolean outputFree = !this.menu.getSlot(this.menu.outputSlotIndex).hasItem();
        this.create.active = this.current != null && this.current.recipe != null
                && (this.player.getAbilities().instabuild
                || outputFree && InventoryIngredients.canCraft(this.player, this.current.recipe.value()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.clickDelay > 0) {
            this.clickDelay--;
        }
        this.updateCreateButton();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.search.setFocused(false);
                return true;
            }
            return this.search.keyPressed(keyCode, scanCode, modifiers) || this.search.canConsumeInput();
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || ClientServices.CLIENT.isActiveAndMatches(this.minecraft.options.keyInventory,
                InputConstants.getKey(keyCode, scanCode))) {
            if (this.page == Page.RECIPE) {
                this.onClose();
            } else {
                this.show(Page.RECIPE);
            }
            return true;
        }
        if (this.page == Page.RECIPE && (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_LEFT)) {
            (keyCode == GLFW.GLFW_KEY_RIGHT ? this.nextRecipe : this.previousRecipe).onPress();
            return true;
        }
        if (this.page == Page.LIST && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            this.browser.scroll(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.page == Page.RECIPE && mouseX > this.width / 2.0) {
            this.preview.drag(button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (this.page == Page.LIST) {
            this.browser.scroll(scrollY > 0 ? -1 : 1);
        } else {
            this.click(() -> this.step(scrollY > 0 ? -1 : 1));
            this.clickDelay = 0;
        }
        return true;
    }

    @Override
    protected void slotClicked(@NotNull Slot slot, int slotId, int mouseButton, @NotNull ClickType type) {
        if (this.page == Page.RECIPE) {
            super.slotClicked(slot, slotId, mouseButton, type);
        }
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return this.page == Page.RECIPE && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    @Override
    protected void renderSlot(@NotNull GuiGraphics g, @NotNull Slot slot) {
        if (this.page == Page.RECIPE) {
            super.renderSlot(g, slot);
        }
    }

    private void blit(GuiGraphics g, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
        g.blit(TEXTURE, x, y, width, height, u, v, uWidth, vHeight, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int v = this.page == Page.RECIPE ? 0 : PANEL_HEIGHT;
        this.blit(g, this.leftPos, this.topPos, PANEL_WIDTH, PANEL_HEIGHT, 0, v, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        if (this.page == Page.LIST) {
            this.browser.renderCards(g, this.font, TEXTURE);
            return;
        }
        if (this.current == null) {
            return;
        }
        if (this.current.isCurrentPageTexture()) {
            ResourceLocation picture = this.current.getCurrentPageTexture();
            if (picture != null) {
                g.blit(picture, 210, 20, 170, 190, 0, 0, 340, 380, TEXTURE_SIZE, TEXTURE_SIZE);
            }
        } else if (this.current.isCurrentPageAcInfo()) {
            for (int i = 0; i < this.current.infoItem().size(); i++) {
                g.drawString(this.font, this.current.infoItem().get(i), 210, 40 + 10 * i, INFO_TEXT_COLOR, false);
                String data = this.current.infoData().get(i);
                if (!data.isEmpty()) {
                    g.drawString(this.font, data, 280, 40 + 10 * i, INFO_TEXT_COLOR, false);
                }
            }
        } else {
            this.blit(g, 340, 215, 45, 15, 400, 60, 90, 30);
            this.renderMouseHelp(g, mouseX - this.leftPos, mouseY - this.topPos);
        }
        g.drawString(this.font, this.current.displayName, 120, 20, -1, true);
        RecipeBrowser.drawRecipe(g, this.font, this.current.recipe, 121, 34);
    }

    /** Over the mouse icon, explains the preview's mouse buttons and lights up the one being held. */
    private void renderMouseHelp(GuiGraphics g, int x, int y) {
        if (x < 350 || x > 400 || y < 214 || y > 230) {
            return;
        }
        long window = this.minecraft.getWindow().getWindow();
        List<Component> lines = List.of(
                this.mouseHelp("rotate", window, GLFW.GLFW_MOUSE_BUTTON_LEFT),
                this.mouseHelp("zoom", window, GLFW.GLFW_MOUSE_BUTTON_RIGHT),
                this.mouseHelp("move", window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
        g.renderComponentTooltip(this.font, lines, x - 30, y);
    }

    private Component mouseHelp(String name, long window, int button) {
        Component line = Component.translatable("mcheli.drafting.mouse." + name);
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS
                ? line.copy().withStyle(ChatFormatting.AQUA) : line;
    }

    @Override
    protected void renderForeground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.page == Page.RECIPE && this.current != null && this.current.isCurrentPageModel()) {
            this.preview.render(g, this.current, this.leftPos + 300, this.topPos + 110);
        }
    }
}
