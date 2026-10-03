package dev.velolib.radial;

import com.mojang.blaze3d.platform.InputConstants;
import dev.velolib.radial.api.ShortcutRegistry;
import dev.velolib.radial.api.SlotModeRegistry;
import dev.velolib.radial.config.RadialConfig;
import dev.velolib.radial.config.RadialConfigScreen;
import dev.velolib.radial.integration.MalilibIntegration;
import dev.velolib.radial.mixin.KeyMappingAccessor;
import dev.velolib.radial.ui.screen.RadialScreen;
import dev.velolib.radial.util.MacroExecutor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadialClient {

    public static final String MOD_ID = "radial";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String KEY_CATEGORY = "key.category.radial.main";

    public static final KeyMapping OPEN_RADIAL = new KeyMapping(
            "key." + MOD_ID + ".open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, KEY_CATEGORY);
    public static final KeyMapping BACK_KEY = new KeyMapping(
            "key." + MOD_ID + ".back", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping STOP_MACROS = new KeyMapping(
            "key." + MOD_ID + ".stop_macros",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            KEY_CATEGORY);
    public static final KeyMapping[] SLOT_KEYS = new KeyMapping[12];

    static {
        for (int i = 0; i < 12; i++) {
            SLOT_KEYS[i] = new KeyMapping(
                    "key." + MOD_ID + ".slot." + (i + 1),
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(),
                    KEY_CATEGORY);
        }
    }

    // How long a simulated key press is held, and how long it must be released before the next press of that key
    private static final int KEY_HOLD_TICKS = 2;
    private static final int KEY_RELEASE_TICKS = 1;

    private static final Map<KeyMapping, ScheduledKeyPress> keyPressQueue = new ConcurrentHashMap<>();
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
    public static boolean isRadialInternalKey(KeyMapping key) {
        if (key == OPEN_RADIAL || key == BACK_KEY || key == STOP_MACROS) return true;
        for (KeyMapping slotKey : SLOT_KEYS) {
            if (key == slotKey) return true;
        }
        return false;
    }

    public static void scheduleKeyPress(KeyMapping key) {
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

    private static void startKeyPress(KeyMapping key, ScheduledKeyPress press) {
        KeyMappingAccessor accessor = (KeyMappingAccessor) key;
        accessor.setClickCount(accessor.getClickCount() + 1);
        press.holdTicksLeft = KEY_HOLD_TICKS;
        press.releaseTicksLeft = KEY_RELEASE_TICKS;
    }

    private static void tickKeyPresses() {
        var it = keyPressQueue.entrySet().iterator();

        while (it.hasNext()) {
            var entry = it.next();
            KeyMapping key = entry.getKey();
            ScheduledKeyPress press = entry.getValue();

            if (press.holdTicksLeft > 0) {
                key.setDown(true);
                press.holdTicksLeft--;
                continue;
            }

            key.setDown(false);

            if (press.queuedPresses <= 0) {
                it.remove();
            } else if (press.releaseTicksLeft > 0) {
                // Stay released for at least one tick so the game sees two separate presses
                press.releaseTicksLeft--;
            } else {
                press.queuedPresses--;
                startKeyPress(key, press);
                key.setDown(true);
                press.holdTicksLeft--;
            }
        }
    }

    /**
     * Whether the physical key or mouse button is currently held, regardless of which screen is open.
     * Unbound keys always count as released.
     */
    public static boolean isPhysicallyDown(InputConstants.Key key) {
        int code = key.getValue();
        if (code == InputConstants.UNKNOWN.getValue()) return false;

        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().getWindow();

        return switch (key.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS;
            case KEYSYM -> GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }

    public static void devLogger(String message) {
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            LOGGER.info("DEV - [ {} ]", message);
        }
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing Radial Client...");

        // REGISTER CONFIG
        SlotModeRegistry.init();
        ShortcutRegistry.init();
        RadialConfig.load();
        MacroExecutor.init();

        if (ModList.get().isLoaded("mafglib")) {
            MalilibIntegration.init();
        }

        modEventBus.addListener(RadialClient::registerKeys);

        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (minecraft, parent) -> {
            if (ModList.get().isLoaded("yet_another_config_lib_v3")) {
                return RadialConfigScreen.create(parent);
            }
            // Instead of returning null, return the parent so it stays
            // on the current screen instead of doing nothing.
            return parent;
        });

        NeoForge.EVENT_BUS.addListener(RadialClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(RadialClient::onRenderGuiLayer);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_RADIAL);
        event.register(BACK_KEY);
        event.register(STOP_MACROS);
        for (KeyMapping key : SLOT_KEYS) {
            event.register(key);
        }
    }

    private static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (VanillaGuiLayers.CROSSHAIR.equals(event.getName())
                && Minecraft.getInstance().screen instanceof RadialScreen) {
            event.setCanceled(true);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();

        if (OPEN_RADIAL.isDown()) {
            if (!keyLocked && client.screen == null) {
                RadialScreen.prepareRenderer();
                client.setScreen(new RadialScreen());
            }
        } else {
            keyLocked = false;
        }

        //noinspection StatementWithEmptyBody
        while (OPEN_RADIAL.consumeClick()) {}

        while (STOP_MACROS.consumeClick()) {
            int stopped = MacroExecutor.cancelAll();
            clearQueuedKeyPresses();
            if (client.gui != null) {
                client.gui.setOverlayMessage(Component.translatable("radial.macro.stopped", stopped), false);
            }
        }

        if (!keyPressQueue.isEmpty()) {
            tickKeyPresses();
        }
    }
}
