package dev.velolib.radial.mode;

import dev.velolib.radial.RadialClient;
import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotActionContext;
import dev.velolib.radial.mode.base.IconEnabledSlotMode;
import dev.velolib.radial.ui.screen.KeybindPickerScreen;
import dev.velolib.radial.ui.screen.SlotEditorScreen;
import java.util.HashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class KeybindSlotMode extends IconEnabledSlotMode {
    public static final HashMap<KeyBinding, Consumer<MinecraftClient>> SPECIAL_ACTIONS = new HashMap<>();

    static {
        // TODO: add crash debug key

        SPECIAL_ACTIONS.put(new KeyBinding("key.screenshot", GLFW.GLFW_KEY_F2, KeyBinding.Category.MISC), client -> {
            ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(), message -> client.inGameHud
                    .getChatHud()
                    .addMessage(message));
        });

        SPECIAL_ACTIONS.put(
                new KeyBinding("key.debug.overlay", GLFW.GLFW_KEY_F3, KeyBinding.Category.DEBUG), client -> {
                    client.keyboard.processF3(new KeyInput(GLFW.GLFW_KEY_F3, 0, 0));
                });
    }

    @Override
    public Text getTranslatedName() {
        return Text.translatable("radial.mode.keybind");
    }

    @Override
    public Text getValueHint() {
        return Text.translatable("screen.radial.editor.hint.keybind");
    }

    @Override
    public boolean hasValuePicker() {
        return true;
    }

    @Override
    public void openValuePicker(Screen parent, Consumer<String> onSelect) {
        MinecraftClient.getInstance().setScreen(new KeybindPickerScreen(parent, onSelect));
    }

    @Override
    public void buildEditorWidgets(
            SlotEditorScreen screen, RadialSlot slot, int width, DirectionalLayoutWidget container) {
        buildValueRow(screen, slot, width, container);
        buildIconRow(screen, slot, width, container);
    }

    @Override
    public void performAction(RadialSlot slot, SlotActionContext context) {
        context.closeScreen();
        MinecraftClient client = MinecraftClient.getInstance();

        for (HashMap.Entry<KeyBinding, Consumer<MinecraftClient>> entry : SPECIAL_ACTIONS.entrySet()) {
            if (entry.getKey().getId().equals(slot.value)) {
                entry.getValue().accept(client);
                return;
            }
        }

        for (KeyBinding key : client.options.allKeys) {
            if (key.getId().equals(slot.value)) {
                // SAFETY CHECK: Abort if it's an internal radial key
                if (dev.velolib.radial.RadialClient.isRadialInternalKey(key)) return;

                if (slot.value.startsWith("key.debug")) {
                    InputUtil.Key inputKey = KeyBindingHelper.getBoundKeyOf(key);
                    int keyCode = inputKey.getCode();
                    var dummyEvent = new KeyInput(keyCode, 0, 0);
                    client.keyboard.processF3(dummyEvent);
                }

                RadialClient.scheduleKeyPress(key);
                break;
            }
        }
    }
}
