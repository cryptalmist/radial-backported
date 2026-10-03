package dev.velolib.radial.api;

import dev.velolib.radial.mode.*;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

public class SlotModeRegistry {
    private static final Map<Identifier, SlotMode> REGISTRY = new LinkedHashMap<>();
    private static final Identifier EMPTY_ID = Identifier.of("radial", "empty");
    private static boolean initialized = false;

    public static void register(Identifier id, SlotMode mode) {
        if (REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate registration for mode ID: " + id);
        }
        REGISTRY.put(id, mode);
    }

    public static Map<Identifier, SlotMode> getRegisteredModes() {
        return java.util.Collections.unmodifiableMap(REGISTRY);
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        register(EMPTY_ID, new EmptySlotMode());
        register(Identifier.of("radial", "delay"), new DelaySlotMode());
        register(Identifier.of("radial", "chat"), new ChatSlotMode());
        register(Identifier.of("radial", "keybind"), new KeybindSlotMode());
        register(Identifier.of("radial", "shortcut"), new ShortcutSlotMode());
        register(Identifier.of("radial", "malilib"), new MalilibSlotMode());
        register(Identifier.of("radial", "submenu"), new SubmenuSlotMode());
        register(Identifier.of("radial", "macro"), new MacroSlotMode());

        FabricLoader.getInstance()
                .getEntrypointContainers("radial", RadialApiEntrypoint.class)
                .forEach(container -> container.getEntrypoint().registerSlotModes());
    }

    public static SlotMode getDefaultMode() {
        SlotMode mode = REGISTRY.get(EMPTY_ID);
        if (mode == null) {
            throw new IllegalStateException("Critical error: radial:empty mode was not registered!");
        }
        return mode;
    }
}
