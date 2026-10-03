package dev.velolib.radial.ui.screen;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotMode;
import dev.velolib.radial.mode.MacroSlotMode;
import dev.velolib.radial.ui.widget.DropdownButtonWidget;
import dev.velolib.radial.ui.widget.DropdownMenuWidget;
import dev.velolib.radial.ui.widget.MacroActionList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class MacroEditorScreen extends Screen implements MacroActionList.DropdownHost {

    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 36;
    private static final int BUTTON_WIDTH = 150;

    private final Screen parent;
    private final RadialSlot slot;

    private final List<RadialSlot.Macro> oldMacros;
    private boolean isSaved = false;

    private MacroActionList actionList;
    private DropdownButtonWidget<SlotMode> openDropdown;

    public MacroEditorScreen(Screen parent, RadialSlot slot) {
        super(Text.translatable("screen.radial.macro.title"));
        this.parent = parent;
        this.slot = slot;

        if (this.slot.macros == null) {
            this.slot.macros = new ArrayList<>();
        }

        this.oldMacros = new ArrayList<>(this.slot.macros);
    }

    @Override
    protected void init() {
        this.openDropdown = null;

        double previousScroll = this.actionList != null ? this.actionList.getScrollY() : 0.0;

        this.actionList = new MacroActionList(
                this.client,
                this.width,
                this.height - HEADER_HEIGHT - FOOTER_HEIGHT,
                HEADER_HEIGHT,
                this,
                this.slot,
                this);
        this.actionList.setScrollY(previousScroll);
        this.addDrawableChild(this.actionList);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.radial.editor.save"), unused -> {
                    this.isSaved = true;
                    this.close();
                })
                .dimensions(this.width / 2 - BUTTON_WIDTH - 4, this.height - 28, BUTTON_WIDTH, 20)
                .tooltip(Tooltip.of(Text.translatable("screen.radial.macro.save.tooltip")))
                .build());

        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.radial.editor.cancel"), unused -> this.close())
                        .dimensions(this.width / 2 + 4, this.height - 28, BUTTON_WIDTH, 20)
                        .build());
    }

    // --- Dropdown handling ---

    private DropdownMenuWidget<SlotMode> getOpenMenu() {
        return this.openDropdown != null ? this.openDropdown.getActiveMenu() : null;
    }

    @Override
    public void onDropdownOpened(DropdownButtonWidget<SlotMode> button) {
        if (this.openDropdown != null && this.openDropdown != button) {
            this.openDropdown.closeMenu();
        }
        this.openDropdown = button;

        DropdownMenuWidget<SlotMode> menu = button.getActiveMenu();
        if (menu != null && menu.getY() + menu.getHeight() > this.height) {
            menu.setY(button.getY() - menu.getHeight());
        }
    }

    @Override
    public void onDropdownClosed(DropdownButtonWidget<SlotMode> button) {
        if (this.openDropdown == button) {
            this.openDropdown = null;
        }
    }

    @Override
    public void closeDropdown() {
        if (this.openDropdown != null) {
            this.openDropdown.closeMenu();
        }
    }

    // --- Rendering ---

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);

        // Hide hover effects underneath the open dropdown menu
        DropdownMenuWidget<SlotMode> menu = this.getOpenMenu();
        boolean hoveringMenu = menu != null && menu.isMouseOver(mouseX, mouseY);
        int passMouseX = hoveringMenu ? -999 : mouseX;
        int passMouseY = hoveringMenu ? -999 : mouseY;

        super.render(graphics, passMouseX, passMouseY, delta);

        graphics.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        int columnY = HEADER_HEIGHT - 12;
        graphics.drawText(
                this.textRenderer,
                Text.translatable("screen.radial.macro.column.type"),
                this.actionList.getTypeColumnX(),
                columnY,
                0xFFA0A0A0,
                false);
        graphics.drawText(
                this.textRenderer,
                Text.translatable("screen.radial.macro.column.value"),
                this.actionList.getValueColumnX(),
                columnY,
                0xFFA0A0A0,
                false);

        // Action counter, highlighted once the limit is reached
        int actionCount = this.actionList.getActionCount();
        Text counter = Text.translatable("screen.radial.macro.count", actionCount, MacroSlotMode.MAX_ACTIONS);
        graphics.drawText(
                this.textRenderer,
                counter,
                this.actionList.getRowRight() - 2 - this.textRenderer.getWidth(counter),
                columnY,
                actionCount >= MacroSlotMode.MAX_ACTIONS ? 0xFFFF5555 : 0xFFA0A0A0,
                false);

        if (menu != null) {
            menu.render(graphics, mouseX, mouseY, delta);
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        DropdownMenuWidget<SlotMode> menu = this.getOpenMenu();
        if (menu != null) {
            if (menu.isMouseOver(click.x(), click.y())) {
                menu.mouseClicked(click, doubled);
                return true;
            }

            // Clicking the owning button lets it toggle itself closed
            if (!this.openDropdown.isMouseOver(click.x(), click.y())) {
                this.closeDropdown();
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        DropdownMenuWidget<SlotMode> menu = this.getOpenMenu();
        if (menu != null) {
            if (menu.isMouseOver(mouseX, mouseY)) {
                return menu.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }

            // The menu is anchored to its row, so close it before the list scrolls away
            this.closeDropdown();
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        if (event.isEscape() && this.openDropdown != null) {
            this.closeDropdown();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void close() {
        if (!this.isSaved) {
            this.slot.macros = new ArrayList<>(this.oldMacros);
        }

        this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
