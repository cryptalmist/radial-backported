package dev.velolib.radial.mode;

import com.mojang.blaze3d.platform.InputConstants;
import dev.velolib.radial.RadialClient;
import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotActionContext;
import dev.velolib.radial.mixin.KeyMappingAccessor;
import dev.velolib.radial.mode.base.IconEnabledSlotMode;
import dev.velolib.radial.ui.screen.KeybindPickerScreen;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class KeybindSlotMode extends IconEnabledSlotMode {

    // Keys the game handles directly instead of through KeyMapping clicks, keyed by keybind ID
    private static final Map<String, Consumer<Minecraft>> SPECIAL_ACTIONS = Map.of(
            "key.screenshot", client -> Screenshot.grab(client, false),
            "key.debug.overlay", client -> client.getDebugOverlay().showDebugScreen());

    @Override
    public Component getTranslatedName() {
        return Component.translatable("radial.mode.keybind");
    }

    @Override
    public Component getValueHint() {
        return Component.translatable("screen.radial.editor.hint.keybind");
    }

    @Override
    public boolean hasValuePicker() {
        return true;
    }

    @Override
    public void openValuePicker(Screen parent, Consumer<String> onSelect) {
        Minecraft.getInstance().gui.setScreen(new KeybindPickerScreen(parent, onSelect));
    }

    @Override
    public void buildEditorWidgets(Screen screen, RadialSlot slot, int width, LinearLayout container) {
        buildValueRow(screen, slot, width, container);
        buildIconRow(screen, slot, width, container);
    }

    @Override
    public void performAction(RadialSlot slot, SlotActionContext context) {
        context.closeScreen();
        if (slot.value == null || slot.value.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();

        Consumer<Minecraft> specialAction = SPECIAL_ACTIONS.get(slot.value);
        if (specialAction != null) {
            specialAction.accept(client);
            return;
        }

        for (KeyMapping key : client.options.keyMappings) {
            if (key.getName().equals(slot.value)) {
                // SAFETY CHECK: Abort if it's an internal radial key
                if (RadialClient.isRadialInternalKey(key)) return;

                if (slot.value.startsWith("key.debug")) {
                    InputConstants.Key inputKey = ((KeyMappingAccessor) key).getKey();
                    int keyCode = inputKey.getValue();
                    var dummyEvent = new KeyEvent(keyCode, 0, 0);
                    client.keyboardHandler.handleDebugKeys(dummyEvent);
                }

                RadialClient.scheduleKeyPress(key);
                break;
            }
        }
    }
}
