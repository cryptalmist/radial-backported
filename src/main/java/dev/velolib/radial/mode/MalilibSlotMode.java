package dev.velolib.radial.mode;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotActionContext;
import dev.velolib.radial.integration.MalilibIntegration;
import dev.velolib.radial.mode.base.IconEnabledSlotMode;
import dev.velolib.radial.ui.screen.MalilibSelectionScreen;
import dev.velolib.radial.ui.screen.SlotEditorScreen;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.text.Text;

public class MalilibSlotMode extends IconEnabledSlotMode {
    @Override
    public Text getTranslatedName() {
        return Text.translatable("radial.mode.malilib");
    }

    @Override
    public Text getValueHint() {
        return Text.translatable("screen.radial.editor.hint.malilib");
    }

    @Override
    public boolean hasValuePicker() {
        return true;
    }

    @Override
    public void openValuePicker(Screen parent, Consumer<String> onSelect) {
        MinecraftClient.getInstance()
                .setScreen(new MalilibSelectionScreen(parent, action -> onSelect.accept(action.id())));
    }

    @Override
    public boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("malilib");
    }

    @Override
    public void performAction(RadialSlot slot, SlotActionContext context) {
        context.closeScreen();

        if (FabricLoader.getInstance().isModLoaded("malilib")) {
            MalilibIntegration.executeHotkey(slot.value);
        }
    }

    @Override
    public void buildEditorWidgets(
            SlotEditorScreen screen, RadialSlot slot, int width, DirectionalLayoutWidget container) {
        buildValueRow(screen, slot, width, container);
        buildIconRow(screen, slot, width, container);
    }
}
