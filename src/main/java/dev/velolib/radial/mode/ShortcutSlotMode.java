package dev.velolib.radial.mode;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.ShortcutEntry;
import dev.velolib.radial.api.ShortcutRegistry;
import dev.velolib.radial.api.SlotActionContext;
import dev.velolib.radial.mode.base.IconEnabledSlotMode;
import dev.velolib.radial.ui.screen.ShortcutSelectionScreen;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ShortcutSlotMode extends IconEnabledSlotMode {

    @Override
    public Component getTranslatedName() {
        return Component.translatable("radial.mode.shortcut");
    }

    @Override
    public Component getValueHint() {
        return Component.translatable("screen.radial.editor.hint.shortcut");
    }

    @Override
    public boolean hasValuePicker() {
        return true;
    }

    @Override
    public void openValuePicker(Screen parent, Consumer<String> onSelect) {
        Minecraft.getInstance()
                .gui
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
    public void buildEditorWidgets(Screen screen, RadialSlot slot, int width, LinearLayout container) {
        buildValueRow(screen, slot, width, container);
        buildIconRow(screen, slot, width, container);
    }
}
