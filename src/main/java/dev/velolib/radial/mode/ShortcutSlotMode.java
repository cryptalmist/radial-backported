package dev.velolib.radial.mode;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.ShortcutEntry;
import dev.velolib.radial.api.ShortcutRegistry;
import dev.velolib.radial.api.SlotActionContext;
import dev.velolib.radial.mode.base.IconEnabledSlotMode;
import dev.velolib.radial.ui.screen.ShortcutSelectionScreen;
import dev.velolib.radial.ui.screen.SlotEditorScreen;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ShortcutSlotMode extends IconEnabledSlotMode {

    @Override
    public Text getTranslatedName() {
        return Text.translatable("radial.mode.shortcut");
    }

    @Override
    public Text getValueHint() {
        return Text.translatable("screen.radial.editor.hint.shortcut");
    }

    @Override
    public boolean hasValuePicker() {
        return true;
    }

    @Override
    public void openValuePicker(Screen parent, Consumer<String> onSelect) {
        MinecraftClient.getInstance()
                .setScreen(new ShortcutSelectionScreen(parent, (Identifier id) -> onSelect.accept(id.toString())));
    }

    @Override
    public void performAction(RadialSlot slot, SlotActionContext context) {
        if (slot.value == null || slot.value.isBlank()) {
            return;
        }

        Identifier menuId = Identifier.tryParse(slot.value);
        if (menuId == null) {
            return;
        }

        ShortcutEntry entry = ShortcutRegistry.getRegisteredShortcuts().get(menuId);

        if (entry != null && entry.openAction() != null) {
            entry.openAction().accept(null);
        }
    }

    @Override
    public void buildEditorWidgets(
            SlotEditorScreen screen, RadialSlot slot, int width, DirectionalLayoutWidget container) {
        buildValueRow(screen, slot, width, container);
        buildIconRow(screen, slot, width, container);
    }
}
