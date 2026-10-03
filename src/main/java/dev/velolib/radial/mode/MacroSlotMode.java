package dev.velolib.radial.mode;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotActionContext;
import dev.velolib.radial.mode.base.IconEnabledSlotMode;
import dev.velolib.radial.ui.screen.MacroEditorScreen;
import dev.velolib.radial.ui.screen.SlotEditorScreen;
import dev.velolib.radial.util.MacroExecutor;
import java.util.ArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

public class MacroSlotMode extends IconEnabledSlotMode {

    public static final int MAX_ACTIONS = 100;

    @Override
    public Text getTranslatedName() {
        return Text.translatable("radial.mode.macro");
    }

    @Override
    public boolean isMacroAction() {
        return false; // Macros can't nest
    }

    @Override
    public void onInitialize(RadialSlot slot) {
        if (slot.macros == null) slot.macros = new ArrayList<>();
    }

    @Override
    public void performAction(RadialSlot slot, SlotActionContext context) {
        context.closeScreen(); // Close the menu so the macro executes safely

        MacroExecutor.start(slot.macros);
    }

    @Override
    public void buildEditorWidgets(
            SlotEditorScreen screen, RadialSlot slot, int width, DirectionalLayoutWidget container) {
        onInitialize(slot);

        DirectionalLayoutWidget macroGroup = DirectionalLayoutWidget.vertical().spacing(2);

        TextWidget label = new TextWidget(
                Text.translatable("screen.radial.editor.macro"), MinecraftClient.getInstance().textRenderer);
        macroGroup.add(label);

        ButtonWidget editButton = ButtonWidget.builder(
                        Text.translatable("screen.radial.editor.macro.edit", slot.macros.size(), MAX_ACTIONS),
                        unused -> MinecraftClient.getInstance().setScreen(new MacroEditorScreen(screen, slot)))
                .dimensions(0, 0, width, 20)
                .build();
        macroGroup.add(editButton);

        container.add(macroGroup);

        buildIconRow(screen, slot, width, container);
    }
}
