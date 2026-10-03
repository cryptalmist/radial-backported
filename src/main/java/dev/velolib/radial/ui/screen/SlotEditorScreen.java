package dev.velolib.radial.ui.screen;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotMode;
import dev.velolib.radial.api.SlotModeRegistry;
import dev.velolib.radial.config.RadialConfig;
import dev.velolib.radial.render.SlotRenderHelper;
import dev.velolib.radial.ui.widget.DropdownButtonWidget;
import dev.velolib.radial.ui.widget.DropdownMenuWidget;
import java.util.List;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.SimplePositioningWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class SlotEditorScreen extends Screen {

    private static final Identifier SLOT_TEXTURE = Identifier.of("minecraft", "gamemode_switcher/slot");
    private static final int SLOT_SIZE = 26;

    // LAYOUT CONSTANTS
    private static final int ROW_HEIGHT = 20;
    private static final int HORIZ_GAP = 5;

    private final RadialSlot slot;

    // State for reverting changes on cancel
    private final String oldName, oldValue, oldId, oldIconColor;
    private final SlotMode oldMode;
    private final int oldChildCount;
    private final List<RadialSlot> oldChildren;
    private final List<RadialSlot.Macro> oldMacros;

    private boolean isSaved = false;

    // Universal Widgets
    private TextFieldWidget nameField;
    private DropdownButtonWidget<SlotMode> modeDropdown;

    public SlotEditorScreen(RadialSlot slot) {
        super(Text.translatable("screen.radial.editor.title"));
        this.slot = slot;

        this.oldName = slot.name;
        this.oldValue = slot.value;
        this.oldId = slot.itemId;
        this.oldIconColor = slot.iconColor;
        this.oldMode = slot.mode;
        this.oldChildCount = slot.childSlotCount;
        this.oldChildren = slot.children != null ? new java.util.ArrayList<>(slot.children) : null;
        this.oldMacros = slot.macros != null ? new java.util.ArrayList<>(slot.macros) : null;
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(300, (int) (width * 0.9));

        DirectionalLayoutWidget mainLayout = DirectionalLayoutWidget.vertical().spacing(8);

        // --- ROW 1: Name ---
        DirectionalLayoutWidget nameGroup = DirectionalLayoutWidget.vertical().spacing(2);
        TextWidget nameLabel = new TextWidget(Text.translatable("screen.radial.editor.name"), textRenderer);
        nameGroup.add(nameLabel);

        nameField = new TextFieldWidget(
                textRenderer, 0, 0, contentWidth, ROW_HEIGHT, Text.translatable("screen.radial.editor.name"));
        nameField.setMaxLength(Integer.MAX_VALUE);
        nameField.setText(slot.name);
        nameField.setChangedListener(v -> slot.name = v);
        nameGroup.add(nameField);

        mainLayout.add(nameGroup);

        // --- ROW 2: Mode ---
        DirectionalLayoutWidget modeGroup = DirectionalLayoutWidget.vertical().spacing(2);
        TextWidget modeLabel = new TextWidget(Text.translatable("screen.radial.editor.mode"), textRenderer);
        modeGroup.add(modeLabel);

        List<SlotMode> availableModes = SlotModeRegistry.getRegisteredModes().values().stream()
                .filter(SlotMode::isAvailable)
                .filter(mode -> !mode.isMacroOnly())
                .toList();

        modeDropdown =
                new DropdownButtonWidget<>(
                        0,
                        0,
                        contentWidth,
                        ROW_HEIGHT,
                        availableModes,
                        slot.mode,
                        SlotMode::getTranslatedName,
                        selectedMode -> {
                            slot.mode = selectedMode;
                            selectedMode.onInitialize(slot);
                            this.clearAndInit();
                        },
                        this::addDrawableChild) {
                    @Override
                    public void closeMenu() {
                        if (this.isMenuOpen()) {
                            SlotEditorScreen.this.remove(this.getActiveMenu());
                        }
                        super.closeMenu();
                    }
                };
        modeGroup.add(modeDropdown);
        mainLayout.add(modeGroup);

        // --- ROW 3: Dynamic Container ---
        DirectionalLayoutWidget dynamicLayoutContainer =
                DirectionalLayoutWidget.vertical().spacing(8);
        slot.mode.buildEditorWidgets(this, slot, contentWidth, dynamicLayoutContainer);
        mainLayout.add(dynamicLayoutContainer);

        // --- ROW 4: Action Buttons ---
        DirectionalLayoutWidget actionGroup =
                DirectionalLayoutWidget.horizontal().spacing(HORIZ_GAP);
        int actionBtnWidth = (contentWidth - HORIZ_GAP) / 2;

        ButtonWidget saveButton = ButtonWidget.builder(Text.translatable("screen.radial.editor.save"), unused -> {
                    this.isSaved = true;
                    RadialConfig.save();
                    close();
                })
                .dimensions(0, 0, actionBtnWidth, ROW_HEIGHT)
                .build();
        actionGroup.add(saveButton);

        ButtonWidget cancelButton = ButtonWidget.builder(
                        Text.translatable("screen.radial.editor.cancel"), unused -> close())
                .dimensions(0, 0, actionBtnWidth, ROW_HEIGHT)
                .build();
        actionGroup.add(cancelButton);

        mainLayout.add(actionGroup);

        // --- ROW 5: Slot Management ---
        DirectionalLayoutWidget managementGroup =
                DirectionalLayoutWidget.horizontal().spacing(HORIZ_GAP);
        int mgmtBtnWidth = (contentWidth - HORIZ_GAP * 2) / 3;

        ButtonWidget copyBtn = ButtonWidget.builder(
                        Text.translatable("screen.radial.editor.copy"), unused -> copyToClipboard())
                .dimensions(0, 0, mgmtBtnWidth, ROW_HEIGHT)
                .build();
        managementGroup.add(copyBtn);

        ButtonWidget pasteBtn = ButtonWidget.builder(
                        Text.translatable("screen.radial.editor.paste"), unused -> pasteFromClipboard())
                .dimensions(0, 0, mgmtBtnWidth, ROW_HEIGHT)
                .build();
        managementGroup.add(pasteBtn);

        ButtonWidget resetBtn = ButtonWidget.builder(
                        Text.translatable("screen.radial.editor.reset"), unused -> resetSlot())
                .dimensions(0, 0, mgmtBtnWidth, ROW_HEIGHT)
                .build();
        managementGroup.add(resetBtn);

        mainLayout.add(managementGroup);

        // --- FINAL ASSEMBLY ---
        mainLayout.refreshPositions();

        // Center in (0, 20, width, height) to match upstream FrameLayout.centerInRectangle:
        // leaves 20px top room for the icon, form vertically centered (not top-pinned).
        SimplePositioningWidget.setPos(mainLayout, 0, 20, width, height, 0.5f, 0.5f);
        mainLayout.forEachChild(this::addDrawableChild);
    }

    private void copyToClipboard() {
        try {
            String json = RadialConfig.GSON.toJson(this.slot);
            this.client.keyboard.setClipboard(json);
            SystemToast.add(
                    this.client.getToastManager(),
                    SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.translatable("screen.radial.editor.toast.copied"),
                    Text.translatable("screen.radial.editor.toast.copied.desc"));
        } catch (Exception e) {
            SystemToast.add(
                    this.client.getToastManager(),
                    SystemToast.Type.PACK_COPY_FAILURE,
                    Text.translatable("screen.radial.editor.toast.copy_failed"),
                    Text.literal(
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : Text.translatable("screen.radial.editor.error.unknown").getString()));
        }
    }

    private void pasteFromClipboard() {
        try {
            String json = this.client.keyboard.getClipboard();
            if (json.trim().isEmpty()) {
                throw new IllegalArgumentException(Text.translatable("screen.radial.editor.error.empty_clipboard")
                        .getString());
            }

            RadialSlot pasted = RadialConfig.GSON.fromJson(json, RadialSlot.class);
            if (pasted == null) {
                throw new IllegalArgumentException(Text.translatable("screen.radial.editor.error.invalid_json")
                        .getString());
            }

            pasted.sanitize();

            // Apply directly onto current slot reference
            this.slot.name = pasted.name;
            this.slot.mode = pasted.mode;
            this.slot.value = pasted.value;
            this.slot.itemId = pasted.itemId;
            this.slot.iconColor = pasted.iconColor;
            this.slot.childSlotCount = pasted.childSlotCount;
            this.slot.children = pasted.children;
            this.slot.macros = pasted.macros;
            this.slot.clearCache();

            // Screen native rebuild function refreshes UI with new values
            this.clearAndInit();

            SystemToast.add(
                    this.client.getToastManager(),
                    SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.translatable("screen.radial.editor.toast.pasted"),
                    Text.translatable("screen.radial.editor.toast.pasted.desc"));
        } catch (Exception e) {
            SystemToast.add(
                    this.client.getToastManager(),
                    SystemToast.Type.PACK_COPY_FAILURE,
                    Text.translatable("screen.radial.editor.toast.paste_failed"),
                    Text.literal(
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : Text.translatable("screen.radial.editor.error.unknown").getString()));
        }
    }

    private void resetSlot() {
        this.slot.name = "";
        this.slot.mode = SlotModeRegistry.getDefaultMode();
        this.slot.value = "";
        this.slot.itemId = "minecraft:air";
        this.slot.iconColor = null;
        this.slot.childSlotCount = 8;
        this.slot.children = null;
        this.slot.macros = null;
        this.slot.clearCache();

        this.clearAndInit();
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        int centerX = width / 2;

        // DYNAMIC ICON POSITIONING:
        // By calculating the icon's Y coordinate based on the `nameField` widget's actual Y coordinate,
        // the icon will perfectly hover 20 pixels above the form regardless of screen size!
        int iconY = (nameField != null) ? nameField.getY() - SLOT_SIZE - 20 : height / 2 - 110;

        // Draw background slot
        graphics.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, centerX - 13, iconY, SLOT_SIZE, SLOT_SIZE);

        SlotRenderHelper.renderSlotIcon(graphics, slot, centerX - 13, iconY);

        // --- THE MOUSE SPOOFING TRICK ---
        boolean hoveringMenu = this.modeDropdown != null
                && this.modeDropdown.isMenuOpen()
                && this.modeDropdown.getActiveMenu().isMouseOver(mouseX, mouseY);

        int passMouseX = hoveringMenu ? -999 : mouseX;
        int passMouseY = hoveringMenu ? -999 : mouseY;

        super.render(graphics, passMouseX, passMouseY, delta);

        if (hoveringMenu) {
            this.modeDropdown.getActiveMenu().render(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public void close() {
        if (!this.isSaved) {
            slot.name = oldName;
            slot.value = oldValue;
            slot.itemId = oldId;
            slot.iconColor = oldIconColor;
            slot.mode = oldMode;
            slot.childSlotCount = oldChildCount;

            if (oldChildren != null) {
                slot.children = new java.util.ArrayList<>(oldChildren);
            } else {
                slot.children = null;
            }

            if (oldMacros != null) {
                slot.macros = new java.util.ArrayList<>(oldMacros);
            } else {
                slot.macros = null;
            }

            slot.clearCache();
        }

        client.setScreen(null);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.modeDropdown != null && this.modeDropdown.isMenuOpen()) {
            DropdownMenuWidget<SlotMode> floatingMenu = this.modeDropdown.getActiveMenu();

            if (floatingMenu.isMouseOver(click.x(), click.y())) {
                floatingMenu.mouseClicked(click, doubled);
                return true;
            } else //noinspection StatementWithEmptyBody
            if (this.modeDropdown.isMouseOver(click.x(), click.y())) {
                // Let the click fall through so the button can close itself
            } else {
                this.modeDropdown.closeMenu();
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // The floating menu overlaps other widgets, which would otherwise receive the scroll first
        if (this.modeDropdown != null && this.modeDropdown.isMenuOpen()) {
            DropdownMenuWidget<SlotMode> floatingMenu = this.modeDropdown.getActiveMenu();

            if (floatingMenu.isMouseOver(mouseX, mouseY)) {
                return floatingMenu.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
