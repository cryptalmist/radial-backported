package dev.velolib.radial;

import dev.velolib.radial.api.ShortcutRegistry;
import dev.velolib.radial.api.SlotModeRegistry;
import dev.velolib.radial.config.RadialConfig;
import dev.velolib.radial.integration.MalilibIntegration;
import dev.velolib.radial.mixin.KeyMappingAccessor;
import dev.velolib.radial.ui.screen.RadialScreen;
import dev.velolib.radial.util.MacroExecutor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadialClient implements ClientModInitializer {

    public static final String MOD_ID = "radial";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

    public static final KeyBinding OPEN_RADIAL = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key." + MOD_ID + ".open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
    public static final KeyBinding BACK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + MOD_ID + ".back", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY));
    public static final KeyBinding STOP_MACROS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + MOD_ID + ".stop_macros",
            InputUtil.Type.KEYSYM,
            InputUtil.UNKNOWN_KEY.getCode(),
            CATEGORY));
    public static final KeyBinding[] SLOT_KEYS = new KeyBinding[12];

    static {
        for (int i = 0; i < 12; i++) {
            SLOT_KEYS[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key." + MOD_ID + ".slot." + (i + 1),
                    InputUtil.Type.KEYSYM,
                    InputUtil.UNKNOWN_KEY.getCode(),
                    CATEGORY));
        }
    }

    // How long a simulated key press is held, and how long it must be released before the next press of that key
    private static final int KEY_HOLD_TICKS = 2;
    private static final int KEY_RELEASE_TICKS = 1;

    private static final Map<KeyBinding, ScheduledKeyPress> keyPressQueue = new ConcurrentHashMap<>();
    private static boolean keyLocked = false;

    /**
     * Press state of a single key. Repeated presses are queued so each one gets a distinct
     * press and release, instead of merging into one long hold.
     */
    private static final class ScheduledKeyPress {
        private int holdTicksLeft;
        private int releaseTicksLeft;
        private int queuedPresses;
    }

    public static void lockKey() {
        keyLocked = true;
    }

    /**
     * Helper to blacklist our internal keys from the picker and slot modes.
     */
    public static boolean isRadialInternalKey(KeyBinding key) {
        if (key == OPEN_RADIAL || key == BACK_KEY || key == STOP_MACROS) return true;
        for (KeyBinding slotKey : SLOT_KEYS) {
            if (key == slotKey) return true;
        }
        return false;
    }

    public static void scheduleKeyPress(KeyBinding key) {
        if (key == null) return;

        ScheduledKeyPress press = keyPressQueue.get(key);
        if (press == null) {
            press = new ScheduledKeyPress();
            keyPressQueue.put(key, press);
            startKeyPress(key, press);
        } else {
            // The key is still busy with an earlier press; run this one after it is released
            press.queuedPresses++;
        }
    }

    public static void clearQueuedKeyPresses() {
        keyPressQueue.values().forEach(press -> press.queuedPresses = 0);
    }

    private static void startKeyPress(KeyBinding key, ScheduledKeyPress press) {
        KeyMappingAccessor accessor = (KeyMappingAccessor) key;
        accessor.setTimesPressed(accessor.getTimesPressed() + 1);
        press.holdTicksLeft = KEY_HOLD_TICKS;
        press.releaseTicksLeft = KEY_RELEASE_TICKS;
    }

    private static void tickKeyPresses() {
        var it = keyPressQueue.entrySet().iterator();

        while (it.hasNext()) {
            var entry = it.next();
            KeyBinding key = entry.getKey();
            ScheduledKeyPress press = entry.getValue();

            if (press.holdTicksLeft > 0) {
                key.setPressed(true);
                press.holdTicksLeft--;
                continue;
            }

            key.setPressed(false);

            if (press.queuedPresses <= 0) {
                it.remove();
            } else if (press.releaseTicksLeft > 0) {
                // Stay released for at least one tick so the game sees two separate presses
                press.releaseTicksLeft--;
            } else {
                press.queuedPresses--;
                startKeyPress(key, press);
                key.setPressed(true);
                press.holdTicksLeft--;
            }
        }
    }

    /**
     * Whether the physical key or mouse button is currently held, regardless of which screen is open.
     * Unbound keys always count as released.
     */
    public static boolean isPhysicallyDown(InputUtil.Key key) {
        int code = key.getCode();
        if (code == InputUtil.UNKNOWN_KEY.getCode()) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();

        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS;
        } else {
            // KEYSYM / SCANCODE
            if (code < 0) return false;
            return GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
        }
    }

    public static void devLogger(String message) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            LOGGER.info("DEV - [ {} ]", message);
        }
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Radial Client...");

        // REGISTER CONFIG
        SlotModeRegistry.init();
        ShortcutRegistry.init();
        RadialConfig.load();
        MacroExecutor.init();

        if (FabricLoader.getInstance().isModLoaded("malilib")) {
            MalilibIntegration.init();
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (OPEN_RADIAL.isPressed()) {
                if (!keyLocked && client.currentScreen == null) {
                    RadialScreen.prepareRenderer();
                    client.setScreen(new RadialScreen());
                }
            } else {
                keyLocked = false;
            }

            //noinspection StatementWithEmptyBody
            while (OPEN_RADIAL.wasPressed()) {}

            while (STOP_MACROS.wasPressed()) {
                int stopped = MacroExecutor.cancelAll();
                clearQueuedKeyPresses();
                client.inGameHud.setOverlayMessage(Text.translatable("radial.macro.stopped", stopped), false);
            }

            if (!keyPressQueue.isEmpty()) {
                tickKeyPresses();
            }
        });
    }
}
