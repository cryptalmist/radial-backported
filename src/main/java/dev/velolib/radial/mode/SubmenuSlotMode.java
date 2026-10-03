package dev.velolib.radial.mode;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotActionContext;
import dev.velolib.radial.api.SlotModeRegistry;
import dev.velolib.radial.mode.base.IconEnabledSlotMode;
import dev.velolib.radial.ui.screen.SlotEditorScreen;
import java.util.ArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class SubmenuSlotMode extends IconEnabledSlotMode {
    @Override
    public Text getTranslatedName() {
        return Text.translatable("radial.mode.submenu");
    }

    @Override
    public boolean isMacroAction() {
        return false; // Submenus need the radial menu to be open
    }

    @Override
    public boolean activateOnRelease() {
        return false; // Submenus only open on click, not hover release
    }

    @Override
    public void performAction(RadialSlot slot, SlotActionContext context) {
        context.openSubmenu(slot.children, slot.childSlotCount);
    }

    @Override
    public void onInitialize(RadialSlot slot) {
        if (slot.children == null) slot.children = new ArrayList<>();

        while (slot.children.size() < slot.childSlotCount) {
            slot.children.add(new RadialSlot(
                    "Sub Slot " + (slot.children.size() + 1),
                    SlotModeRegistry.getRegisteredModes().get(Identifier.of("radial", "empty")),
                    "",
                    "minecraft:stone"));
        }
    }

    @Override
    public void buildEditorWidgets(
            SlotEditorScreen screen, RadialSlot slot, int width, DirectionalLayoutWidget container) {
        int ROW_HEIGHT = 20;

        DirectionalLayoutWidget subGroup = DirectionalLayoutWidget.vertical().spacing(2);

        TextWidget label = new TextWidget(
                Text.translatable("screen.radial.editor.submenu"), MinecraftClient.getInstance().textRenderer);
        subGroup.add(label);

        // Pass 0, 0 for X and Y, the layout will override it automatically
        SliderWidget subCountSlider =
                new SliderWidget(
                        0,
                        0,
                        width,
                        ROW_HEIGHT,
                        Text.translatable("screen.radial.editor.submenu.placeholder", slot.childSlotCount),
                        (slot.childSlotCount - 2) / 10.0) {
                    @Override
                    protected void updateMessage() {
                        int val = 2 + (int) Math.round(value * 10);
                        setMessage(Text.translatable("screen.radial.editor.submenu.placeholder", val));
                    }

                    @Override
                    protected void applyValue() {
                        slot.childSlotCount = 2 + (int) Math.round(value * 10);
                        onInitialize(slot); // Trigger the array resize logic
                    }
                };
        subGroup.add(subCountSlider);

        container.add(subGroup);

        // Icon Row
        buildIconRow(screen, slot, width, container);
    }
}
