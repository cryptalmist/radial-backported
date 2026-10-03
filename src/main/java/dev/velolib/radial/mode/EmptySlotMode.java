package dev.velolib.radial.mode;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotActionContext;
import dev.velolib.radial.mode.base.IconEnabledSlotMode;
import dev.velolib.radial.ui.screen.SlotEditorScreen;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.text.Text;

public class EmptySlotMode extends IconEnabledSlotMode {
    @Override
    public Text getTranslatedName() {
        return Text.translatable("radial.mode.empty");
    }

    @Override
    public boolean isMacroAction() {
        return false; // A "do nothing" step is pointless inside a macro
    }

    @Override
    public boolean activateOnRelease() {
        return false; // Don't trigger when releasing on empty slots
    }

    @Override
    public void performAction(RadialSlot slot, SlotActionContext context) {
        // Do absolutely nothing. Do not even call context.closeScreen()!
    }

    @Override
    public void buildEditorWidgets(
            SlotEditorScreen screen, RadialSlot slot, int width, DirectionalLayoutWidget container) {
        buildIconRow(screen, slot, width, container);
    }
}
