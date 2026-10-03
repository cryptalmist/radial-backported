package dev.velolib.radial.ui.screen;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotMode;
import dev.velolib.radial.api.SlotModeRegistry;
import dev.velolib.radial.config.RadialConfig;
import dev.velolib.radial.render.SlotRenderHelper;
import dev.velolib.radial.ui.widget.DropdownButtonWidget;
import dev.velolib.radial.ui.widget.DropdownMenuWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SlotEditorScreen extends Screen {

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
    private EditBox nameField;
    private DropdownButtonWidget<SlotMode> modeDropdown;

    public SlotEditorScreen(RadialSlot slot) {
        super(Component.translatable("screen.radial.editor.title"));
        this.slot = slot;

        this.oldName = slot.name;
        this.oldValue = slot.value;
        this.oldId = slot.itemId;
        this.oldIconColor = slot.iconColor;
        this.oldMode = slot.mode;
        this.oldChildCount = slot.childSlotCount;
        this.oldChildren = slot.children != null ? new ArrayList<>(slot.children) : null;
        this.oldMacros = slot.macros != null ? new ArrayList<>(slot.macros) : null;
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(300, (int) (width * 0.9));

        LinearLayout mainLayout = LinearLayout.vertical().spacing(8);

        // --- ROW 1: Name ---
        LinearLayout nameGroup = LinearLayout.vertical().spacing(2);
        StringWidget nameLabel = new StringWidget(Component.translatable("screen.radial.editor.name"), font);
        nameGroup.addChild(nameLabel);

        nameField =
                new EditBox(font, 0, 0, contentWidth, ROW_HEIGHT, Component.translatable("screen.radial.editor.name"));
        nameField.setMaxLength(Integer.MAX_VALUE);
        nameField.setValue(slot.name);
        nameField.setResponder(v -> slot.name = v);
        nameGroup.addChild(nameField);

        mainLayout.addChild(nameGroup);

        // --- ROW 2: Mode ---
        LinearLayout modeGroup = LinearLayout.vertical().spacing(2);
        StringWidget modeLabel = new StringWidget(Component.translatable("screen.radial.editor.mode"), font);
        modeGroup.addChild(modeLabel);

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
                            this.rebuildWidgets();
                        },
                        this::addRenderableWidget) {
                    @Override
                    public void closeMenu() {
                        if (this.isMenuOpen()) {
                            SlotEditorScreen.this.removeWidget(this.getActiveMenu());
                        }
                        super.closeMenu();
                    }
                };
        modeGroup.addChild(modeDropdown);
        mainLayout.addChild(modeGroup);

        // --- ROW 3: Dynamic Container ---
        LinearLayout dynamicLayoutContainer = LinearLayout.vertical().spacing(8);
        slot.mode.buildEditorWidgets(this, slot, contentWidth, dynamicLayoutContainer);
        mainLayout.addChild(dynamicLayoutContainer);

        // --- ROW 4: Action Buttons ---
        LinearLayout actionGroup = LinearLayout.horizontal().spacing(HORIZ_GAP);
        int actionBtnWidth = (contentWidth - HORIZ_GAP) / 2;

        Button saveButton = Button.builder(Component.translatable("screen.radial.editor.save"), btn -> {
                    this.isSaved = true;
                    RadialConfig.save();
                    onClose();
                })
                .bounds(0, 0, actionBtnWidth, ROW_HEIGHT)
                .build();
        actionGroup.addChild(saveButton);

        Button cancelButton = Button.builder(Component.translatable("screen.radial.editor.cancel"), btn -> onClose())
                .bounds(0, 0, actionBtnWidth, ROW_HEIGHT)
                .build();
        actionGroup.addChild(cancelButton);

        mainLayout.addChild(actionGroup);

        // --- ROW 5: Slot Management ---
        LinearLayout managementGroup = LinearLayout.horizontal().spacing(HORIZ_GAP);
        int mgmtBtnWidth = (contentWidth - HORIZ_GAP * 2) / 3;

        Button copyBtn = Button.builder(Component.translatable("screen.radial.editor.copy"), btn -> copyToClipboard())
                .bounds(0, 0, mgmtBtnWidth, ROW_HEIGHT)
                .build();
        managementGroup.addChild(copyBtn);

        Button pasteBtn = Button.builder(
                        Component.translatable("screen.radial.editor.paste"), btn -> pasteFromClipboard())
                .bounds(0, 0, mgmtBtnWidth, ROW_HEIGHT)
                .build();
        managementGroup.addChild(pasteBtn);

        Button resetBtn = Button.builder(Component.translatable("screen.radial.editor.reset"), btn -> resetSlot())
                .bounds(0, 0, mgmtBtnWidth, ROW_HEIGHT)
                .build();
        managementGroup.addChild(resetBtn);

        mainLayout.addChild(managementGroup);

        // --- FINAL ASSEMBLY ---
        FrameLayout rootLayout = new FrameLayout();
        rootLayout.addChild(mainLayout);
        rootLayout.arrangeElements();

        // Offset the Y position down by 20 to ensure room at the top of the screen for the icon
        FrameLayout.centerInRectangle(rootLayout, 0, 20, width, height);
        rootLayout.visitWidgets(this::addRenderableWidget);
    }

    private void copyToClipboard() {
        try {
            String json = RadialConfig.GSON.toJson(this.slot);
            this.minecraft.keyboardHandler.setClipboard(json);
            showToast(
                    Component.translatable("screen.radial.editor.toast.copied"),
                    Component.translatable("screen.radial.editor.toast.copied.desc"),
                    false);
        } catch (Exception e) {
            showToast(
                    Component.translatable("screen.radial.editor.toast.copy_failed"),
                    Component.literal(
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : Component.translatable("screen.radial.editor.error.unknown")
                                            .getString()),
                    true);
        }
    }

    private void pasteFromClipboard() {
        try {
            String json = this.minecraft.keyboardHandler.getClipboard();
            if (json == null || json.trim().isEmpty()) {
                throw new IllegalArgumentException(Component.translatable("screen.radial.editor.error.empty_clipboard")
                        .getString());
            }

            RadialSlot pasted = RadialConfig.GSON.fromJson(json, RadialSlot.class);
            if (pasted == null) {
                throw new IllegalArgumentException(Component.translatable("screen.radial.editor.error.invalid_json")
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
            this.rebuildWidgets();

            showToast(
                    Component.translatable("screen.radial.editor.toast.pasted"),
                    Component.translatable("screen.radial.editor.toast.pasted.desc"),
                    false);
        } catch (Exception e) {
            showToast(
                    Component.translatable("screen.radial.editor.toast.paste_failed"),
                    Component.literal(
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : Component.translatable("screen.radial.editor.error.unknown")
                                            .getString()),
                    true);
        }
    }

    private void showToast(Component title, Component desc, boolean error) {
        // 1.21.1 backport: SystemToast.add(ToastManager,...) API differs from 26.x;
        // use the action-bar overlay for copy/paste feedback instead.
        try {
            if (this.minecraft != null && this.minecraft.gui != null) {
                this.minecraft.gui.setOverlayMessage(error ? desc : title, false);
            }
        } catch (Exception e) {
            // Feedback is best-effort; clipboard action already succeeded/failed
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

        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        int centerX = width / 2;

        // DYNAMIC ICON POSITIONING:
        // By calculating the icon's Y coordinate based on the `nameField` widget's actual Y coordinate,
        // the icon will perfectly hover 20 pixels above the form regardless of screen size!
        int iconY = (nameField != null) ? nameField.getY() - SLOT_SIZE - 20 : height / 2 - 110;

        // Draw background slot
        graphics.blitSprite(SlotRenderHelper.SLOT_TEXTURE, centerX - 13, iconY, SLOT_SIZE, SLOT_SIZE);

        SlotRenderHelper.renderSlotIcon(graphics, slot, centerX - 13, iconY, 255);

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
    public void onClose() {
        if (!this.isSaved) {
            slot.name = oldName;
            slot.value = oldValue;
            slot.itemId = oldId;
            slot.iconColor = oldIconColor;
            slot.mode = oldMode;
            slot.childSlotCount = oldChildCount;

            if (oldChildren != null) {
                slot.children = new ArrayList<>(oldChildren);
            } else {
                slot.children = null;
            }

            if (oldMacros != null) {
                slot.macros = new ArrayList<>(oldMacros);
            } else {
                slot.macros = null;
            }

            slot.clearCache();
        }

        minecraft.setScreen(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.modeDropdown != null && this.modeDropdown.isMenuOpen()) {
            DropdownMenuWidget<SlotMode> floatingMenu = this.modeDropdown.getActiveMenu();

            if (floatingMenu.isMouseOver(mouseX, mouseY)) {
                floatingMenu.mouseClicked(mouseX, mouseY, button);
                return true;
            } else if (this.modeDropdown.isMouseOver(mouseX, mouseY)) {
                // Let the click fall through so the button can close itself
            } else {
                this.modeDropdown.closeMenu();
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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
    public boolean isPauseScreen() {
        return false;
    }
}
