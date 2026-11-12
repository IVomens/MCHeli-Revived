package com.ivomens.mcheli.livery.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.livery.net.LiveryApplyMessage;
import com.ivomens.mcheli.livery.net.LiveryDeleteMessage;
import com.ivomens.mcheli.livery.net.LiveryListMessage;
import com.ivomens.mcheli.livery.net.LiveryListRequestMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Browses, applies, uploads and deletes liveries for the vehicle the player is in.
 * <p>
 * "This vehicle" lists every livery made for the vehicle type, "My liveries" the player's own
 * uploads for any type, and "Upload" the PNG files in {@code <gameDir>/mcheli_liveries/}. Files
 * dropped onto the window are copied into that folder. The server checks every action again.
 */
public final class LiveryScreen extends McScreen {

    private enum Tab {VEHICLE, MINE, UPLOAD}

    private static final int MAX_CONTENT_WIDTH = 420;
    private static final int GAP = 4;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 42;
    private static final int NAME_MAX_LENGTH = 32;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int DETAIL_COLOR = 0xA0A0A0;
    private static final int APPLIED_COLOR = 0x80FF80;
    private static final int ERROR_COLOR = 0xFF7070;
    private static final int OK_COLOR = 0x80FF80;
    private static final int PROGRESS_COLOR = 0xFF55AA55;
    private static final int PROGRESS_BACKGROUND = 0xFF303030;

    private final LiveryClient client;
    private final AircraftEntity aircraft;
    private final String vehicleType;
    private final Component vehicleName;

    private Tab tab = Tab.VEHICLE;
    @Nullable
    private LiveryListMessage list;
    private List<LiveryFiles.LocalImage> files = List.of();
    @Nullable
    private Path selectedFile;
    @Nullable
    private String selectedLivery;
    private String pendingName = "";
    private Component status = Component.empty();
    private int statusColor = DETAIL_COLOR;
    private boolean exporting;

    private RowList rows;
    private EditBox nameField;
    private Button applyButton;
    private Button defaultButton;
    private Button deleteButton;
    private Button uploadButton;
    private Button templateButton;
    private final List<Button> tabButtons = new ArrayList<>();

    public LiveryScreen(AircraftEntity aircraft, LiveryClient client) {
        super(Component.translatable("key.mcheli.livery"));
        this.client = client;
        this.aircraft = aircraft;
        this.vehicleType = aircraft.identity().typeName();
        VehicleSpec info = aircraft.spec();
        String name = info != null && info.file().displayName().name() != null
                && !info.file().displayName().name().isEmpty() ? info.file().displayName().name() : this.vehicleType;
        this.vehicleName = Component.literal(name);
    }

    @Override
    protected void build() {
        int contentWidth = Math.min(this.width - 16, MAX_CONTENT_WIDTH);
        int left = (this.width - contentWidth) / 2;
        int third = (contentWidth - 2 * GAP) / 3;
        int rowB = this.height - 34;
        int rowA = rowB - BUTTON_HEIGHT - GAP;

        this.tabButtons.clear();
        Layout tabs = this.row(left, 18).gap(GAP);
        for (Tab target : Tab.values()) {
            this.tabButtons.add(tabs.add(Widgets.button(tabLabel(target), third, BUTTON_HEIGHT, () -> this.switchTab(target))));
        }

        this.rows = this.add(new RowList(left, LIST_TOP, contentWidth, Math.max(ROW_HEIGHT, rowA - GAP - LIST_TOP)));

        Layout actions = this.row(left, rowA).gap(GAP);
        this.applyButton = actions.add(Widgets.button(Component.translatable("mcheli.livery.apply"), third, BUTTON_HEIGHT,
                this::applySelected));
        this.defaultButton = actions.add(Widgets.button(Component.translatable("mcheli.livery.default"), third,
                BUTTON_HEIGHT, () -> Network.sendToServer(new LiveryApplyMessage(this.aircraft.getId(), ""))));
        this.deleteButton = actions.add(Widgets.button(Component.translatable("mcheli.livery.delete"), third,
                BUTTON_HEIGHT, this::confirmDelete));

        this.nameField = this.add(Widgets.text(contentWidth - 2, BUTTON_HEIGHT - 2, NAME_MAX_LENGTH,
                Component.translatable("mcheli.livery.name")));
        this.nameField.setPosition(left + 1, rowA + 1);
        this.nameField.setHint(Component.translatable("mcheli.livery.name").withStyle(ChatFormatting.DARK_GRAY));
        this.nameField.setValue(this.pendingName);
        this.nameField.setResponder(value -> this.pendingName = value);

        Layout files = this.row(left, rowB).gap(GAP);
        this.uploadButton = files.add(Widgets.button(Component.empty(), third, BUTTON_HEIGHT, this::onUploadPressed));
        files.add(Widgets.button(Component.translatable("mcheli.livery.open_folder"), third, BUTTON_HEIGHT,
                () -> LiveryFiles.open(LiveryFiles.root())));
        this.templateButton = files.add(Widgets.button(Component.translatable("mcheli.livery.template"), third,
                BUTTON_HEIGHT, this::exportTemplate));

        if (this.list == null) {
            this.setStatus(Component.translatable("mcheli.livery.loading"), DETAIL_COLOR);
            Network.sendToServer(new LiveryListRequestMessage(this.vehicleType));
        }
        this.reloadFiles(null);
        this.rebuildRows();
    }

    private static Component tabLabel(Tab tab) {
        return Component.translatable(switch (tab) {
            case VEHICLE -> "mcheli.livery.tab.vehicle";
            case MINE -> "mcheli.livery.tab.mine";
            case UPLOAD -> "mcheli.livery.tab.upload";
        });
    }

    private void switchTab(Tab target) {
        this.tab = target;
        this.rebuildRows();
    }

    // Server replies

    void onList(LiveryListMessage received) {
        boolean first = this.list == null;
        this.list = received;
        if (!received.message().isEmpty()) {
            this.setStatus(Component.literal(received.message()), received.messageIsError() ? ERROR_COLOR : OK_COLOR);
        } else if (!received.permissions().enabled()) {
            this.setStatus(Component.translatable("mcheli.livery.disabled"), ERROR_COLOR);
        } else if (first) {
            this.setStatus(Component.empty(), DETAIL_COLOR);
        }
        this.rebuildRows();
    }

    void onUploadFinished(boolean success, String message) {
        this.setStatus(Component.literal(message), success ? OK_COLOR : ERROR_COLOR);
        if (success) {
            this.tab = Tab.MINE;
            this.pendingName = "";
            this.nameField.setValue("");
            this.rebuildRows();
        }
    }

    private void setStatus(Component text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    // Rows

    private void rebuildRows() {
        if (this.rows == null) {
            return;
        }
        List<Row> entries = new ArrayList<>();
        Row selected = null;
        if (this.tab == Tab.UPLOAD) {
            for (LiveryFiles.LocalImage file : this.files) {
                Row row = Row.forFile(this, file);
                entries.add(row);
                if (file.path().equals(this.selectedFile)) {
                    selected = row;
                }
            }
        } else if (this.list != null) {
            for (LiveryListMessage.Entry entry : this.list.entries()) {
                boolean shown = this.tab == Tab.VEHICLE ? entry.vehicleType().equalsIgnoreCase(this.vehicleType) : entry.own();
                if (shown) {
                    Row row = Row.forLivery(this, entry);
                    entries.add(row);
                    if (entry.id().equals(this.selectedLivery)) {
                        selected = row;
                    }
                }
            }
        }
        this.rows.setRows(entries, selected);
        this.updateWidgets();
    }

    private void reloadFiles(@Nullable Path select) {
        CompletableFuture.supplyAsync(LiveryFiles::listImages, Util.ioPool())
                .thenAcceptAsync(found -> {
                    this.files = found;
                    if (select != null) {
                        this.selectFile(select);
                    }
                    if (this.tab == Tab.UPLOAD) {
                        this.rebuildRows();
                    }
                }, this.minecraft);
    }

    private void selectFile(Path file) {
        this.selectedFile = file;
        String name = file.getFileName().toString();
        this.pendingName = name.substring(0, name.length() - ".png".length());
        this.nameField.setValue(this.pendingName);
    }

    @Nullable
    private LiveryListMessage.Entry selectedLiveryEntry() {
        Row row = this.rows == null ? null : this.rows.getSelected();
        return row == null ? null : row.livery;
    }

    // Buttons

    @Override
    public void tick() {
        super.tick();
        this.updateWidgets();
    }

    private void updateWidgets() {
        if (this.applyButton == null) {
            return;
        }
        boolean upload = this.tab == Tab.UPLOAD;
        for (int i = 0; i < this.tabButtons.size(); i++) {
            this.tabButtons.get(i).active = i != this.tab.ordinal();
        }
        this.applyButton.visible = !upload;
        this.defaultButton.visible = !upload;
        this.deleteButton.visible = !upload;
        this.nameField.visible = upload;

        boolean ready = this.list != null && this.list.permissions().enabled();
        boolean mayChange = this.list != null
                && (this.aircraft.seats().seatIdOf(this.minecraft.player) == 0 || this.list.permissions().operator());
        LiveryListMessage.Entry selected = this.selectedLiveryEntry();
        String applied = this.aircraft.appearance().liveryId();
        this.applyButton.active = ready && mayChange && selected != null
                && selected.vehicleType().equalsIgnoreCase(this.vehicleType) && !selected.id().equals(applied);
        this.defaultButton.active = mayChange && !applied.isEmpty();
        this.deleteButton.active = ready && selected != null && (selected.own() || this.list.permissions().operator());

        if (upload) {
            this.uploadButton.setMessage(Component.translatable("mcheli.livery.upload.send"));
            Row row = this.rows.getSelected();
            this.uploadButton.active = ready && this.list.permissions().canUpload() && row != null && row.file != null
                    && !this.nameField.getValue().isBlank() && !this.client.isUploading();
        } else {
            this.uploadButton.setMessage(Component.translatable("mcheli.livery.upload"));
            this.uploadButton.active = true;
        }
        this.templateButton.active = !this.exporting;
    }

    private void applySelected() {
        LiveryListMessage.Entry selected = this.selectedLiveryEntry();
        if (selected != null) {
            Network.sendToServer(new LiveryApplyMessage(this.aircraft.getId(), selected.id()));
        }
    }

    private void confirmDelete() {
        LiveryListMessage.Entry selected = this.selectedLiveryEntry();
        if (selected == null) {
            return;
        }
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                Network.sendToServer(new LiveryDeleteMessage(selected.id(), this.vehicleType));
            }
            this.minecraft.setScreen(this);
        }, Component.translatable("mcheli.livery.delete.confirm", selected.name()),
                Component.translatable("mcheli.livery.delete.warning")));
    }

    private void onUploadPressed() {
        if (this.tab != Tab.UPLOAD) {
            this.switchTab(Tab.UPLOAD);
            this.reloadFiles(null);
            if (this.list != null && !this.list.permissions().canUpload()) {
                this.setStatus(Component.translatable("mcheli.livery.operators_only"), ERROR_COLOR);
            }
            return;
        }
        Row row = this.rows.getSelected();
        if (row == null || row.file == null || this.list == null) {
            return;
        }
        Path file = row.file;
        String name = this.nameField.getValue().strip();
        int maxBytes = this.list.limits().maxBytes();
        CompletableFuture.supplyAsync(() -> readUpload(file, maxBytes), Util.ioPool())
                .thenAcceptAsync(png -> {
                    if (png == null) {
                        this.setStatus(Component.translatable("mcheli.livery.read_failed", file.getFileName().toString()), ERROR_COLOR);
                    } else if (png.length > maxBytes) {
                        this.setStatus(Component.translatable("mcheli.livery.too_large", maxBytes / 1024), ERROR_COLOR);
                    } else {
                        this.setStatus(Component.empty(), DETAIL_COLOR);
                        this.client.startUpload(this.vehicleType, name, png);
                    }
                }, this.minecraft);
    }

    /** The file's bytes, or null if unreadable. Reads at most one byte past the limit. */
    @Nullable
    private static byte[] readUpload(Path file, int maxBytes) {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(maxBytes + 1);
        } catch (IOException e) {
            ModLogger.warn("Could not read {}: {}", file, e.getMessage());
            return null;
        }
    }

    private void exportTemplate() {
        this.exporting = true;
        this.setStatus(Component.translatable("mcheli.livery.template.working"), DETAIL_COLOR);
        LiveryTemplates.export(this.aircraft).whenCompleteAsync((folder, error) -> {
            this.exporting = false;
            if (error != null) {
                ModLogger.warn("Could not write the livery templates of {}: {}", this.vehicleType, error.toString());
                this.setStatus(Component.translatable("mcheli.livery.template.failed"), ERROR_COLOR);
                return;
            }
            this.setStatus(Component.translatable("mcheli.livery.template.done"), OK_COLOR);
            LiveryFiles.open(folder);
        }, this.minecraft);
    }

    @Override
    public void onFilesDrop(List<Path> dropped) {
        List<Path> images = dropped.stream().filter(LiveryFiles::isPng).toList();
        if (images.isEmpty()) {
            this.setStatus(Component.translatable("mcheli.livery.png_only"), ERROR_COLOR);
            return;
        }
        this.tab = Tab.UPLOAD;
        this.rebuildRows();
        CompletableFuture.supplyAsync(() -> {
            Path first = null;
            for (Path image : images) {
                try {
                    Path copy = LiveryFiles.importFile(image);
                    first = first == null ? copy : first;
                } catch (IOException e) {
                    ModLogger.warn("Could not copy {}: {}", image, e.getMessage());
                }
            }
            return first;
        }, Util.ioPool()).thenAcceptAsync(this::reloadFiles, this.minecraft);
    }

    // Drawing

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(this.font, Component.translatable("mcheli.livery.title", this.vehicleName),
                this.width / 2, 6, TEXT_COLOR);
        if (this.rows.children().isEmpty()) {
            graphics.drawCenteredString(this.font, this.emptyText(), this.width / 2, LIST_TOP + 10, DETAIL_COLOR);
        }
        this.renderStatusLine(graphics);
    }

    private Component emptyText() {
        if (this.list == null && this.tab != Tab.UPLOAD) {
            return Component.translatable("mcheli.livery.loading");
        }
        return Component.translatable(switch (this.tab) {
            case VEHICLE -> "mcheli.livery.empty.vehicle";
            case MINE -> "mcheli.livery.empty.mine";
            case UPLOAD -> "mcheli.livery.empty.files";
        });
    }

    private void renderStatusLine(GuiGraphics graphics) {
        int y = this.height - 11;
        if (this.client.isUploading()) {
            int barWidth = Math.min(this.width - 16, 200);
            int left = (this.width - barWidth) / 2;
            float progress = this.client.uploadProgress();
            graphics.fill(left, y - 1, left + barWidth, y + 9, PROGRESS_BACKGROUND);
            graphics.fill(left, y - 1, left + Math.round(barWidth * progress), y + 9, PROGRESS_COLOR);
            graphics.drawCenteredString(this.font, Component.translatable("mcheli.livery.uploading",
                    Math.round(progress * 100)), this.width / 2, y, TEXT_COLOR);
            return;
        }
        Component line = this.status;
        int color = this.statusColor;
        if (line.getString().isEmpty() && this.tab == Tab.UPLOAD && this.list != null) {
            LiveryListMessage.Limits limits = this.list.limits();
            line = Component.translatable("mcheli.livery.limits", limits.owned(), limits.perPlayer(),
                    limits.maxBytes() / 1024, limits.maxDimension());
            color = DETAIL_COLOR;
        }
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(line.getString(), this.width - 8),
                this.width / 2, y, color);
    }

    static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, bytes / 1024) + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // List widgets

    private final class RowList extends ObjectSelectionList<Row> {

        RowList(int x, int y, int width, int height) {
            super(LiveryScreen.this.minecraft, width, height, y, ROW_HEIGHT);
            this.setX(x);
        }

        void setRows(List<Row> entries, @Nullable Row selected) {
            this.replaceEntries(entries);
            this.setSelected(selected);
            this.clampScrollAmount();
        }

        @Override
        public int getRowWidth() {
            return this.width - 14;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.width - 6;
        }

        @Override
        public void setSelected(@Nullable Row row) {
            super.setSelected(row);
            if (row != null && row.livery != null) {
                LiveryScreen.this.selectedLivery = row.livery.id();
            } else if (row != null && row.file != null && !row.file.equals(LiveryScreen.this.selectedFile)) {
                LiveryScreen.this.selectFile(row.file);
            }
        }
    }

    private static final class Row extends ObjectSelectionList.Entry<Row> {

        private final LiveryScreen screen;
        @Nullable
        final LiveryListMessage.Entry livery;
        @Nullable
        final Path file;
        private final String title;
        private final String detail;

        private Row(LiveryScreen screen, @Nullable LiveryListMessage.Entry livery, @Nullable Path file, String title,
                    String detail) {
            this.screen = screen;
            this.livery = livery;
            this.file = file;
            this.title = title;
            this.detail = detail;
        }

        static Row forLivery(LiveryScreen screen, LiveryListMessage.Entry entry) {
            StringBuilder detail = new StringBuilder(Component.translatable("mcheli.livery.by", entry.ownerName()).getString());
            if (screen.tab == Tab.MINE) {
                detail.append("  ·  ").append(entry.vehicleType());
            }
            detail.append("  ·  ").append(entry.image().width()).append('×').append(entry.image().height())
                    .append("  ·  ").append(formatSize(entry.image().bytes()));
            return new Row(screen, entry, null, entry.name(), detail.toString());
        }

        static Row forFile(LiveryScreen screen, LiveryFiles.LocalImage image) {
            return new Row(screen, null, image.path(), image.path().getFileName().toString(), formatSize(image.size()));
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                           int mouseY, boolean hovering, float partialTick) {
            Font font = this.screen.font;
            boolean applied = this.livery != null && this.livery.id().equals(this.screen.aircraft.appearance().liveryId());
            String name = applied
                    ? this.title + "  (" + Component.translatable("mcheli.livery.applied").getString() + ")"
                    : this.title;
            graphics.drawString(font, font.plainSubstrByWidth(name, width - 4), left + 2, top + 1,
                    applied ? APPLIED_COLOR : TEXT_COLOR);
            graphics.drawString(font, font.plainSubstrByWidth(this.detail, width - 4), left + 2, top + 11, DETAIL_COLOR);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            this.screen.rows.setSelected(this);
            this.screen.updateWidgets();
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.title);
        }
    }
}
