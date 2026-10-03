package dev.velolib.radial.api;

import dev.velolib.radial.mode.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import net.minecraft.resources.ResourceLocation;

public class SlotModeRegistry {
    private static final Map<ResourceLocation, SlotMode> REGISTRY = new LinkedHashMap<>();
    private static final ResourceLocation EMPTY_ID = ResourceLocation.fromNamespaceAndPath("radial", "empty");
    private static boolean initialized = false;

    public static void register(ResourceLocation id, SlotMode mode) {
        if (REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate registration for mode ID: " + id);
        }
        REGISTRY.put(id, mode);
    }

    public static Map<ResourceLocation, SlotMode> getRegisteredModes() {
        return java.util.Collections.unmodifiableMap(REGISTRY);
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        register(EMPTY_ID, new EmptySlotMode());
        register(ResourceLocation.fromNamespaceAndPath("radial", "delay"), new DelaySlotMode());
        register(ResourceLocation.fromNamespaceAndPath("radial", "chat"), new ChatSlotMode());
        register(ResourceLocation.fromNamespaceAndPath("radial", "keybind"), new KeybindSlotMode());
        register(ResourceLocation.fromNamespaceAndPath("radial", "shortcut"), new ShortcutSlotMode());
        register(ResourceLocation.fromNamespaceAndPath("radial", "malilib"), new MalilibSlotMode());
        register(ResourceLocation.fromNamespaceAndPath("radial", "submenu"), new SubmenuSlotMode());
        register(ResourceLocation.fromNamespaceAndPath("radial", "macro"), new MacroSlotMode());

        // Third-party addons can contribute modes via Java's ServiceLoader
        // (META-INF/services/dev.velolib.radial.api.RadialApiEntrypoint).
        ServiceLoader.load(RadialApiEntrypoint.class).forEach(RadialApiEntrypoint::registerSlotModes);
    }

    public static SlotMode getDefaultMode() {
        SlotMode mode = REGISTRY.get(EMPTY_ID);
        if (mode == null) {
            throw new IllegalStateException("Critical error: radial:empty mode was not registered!");
        }
        return mode;
    }
}
