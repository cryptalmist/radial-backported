package dev.velolib.radial.api;

import com.mojang.brigadier.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RadialSlot {

    public String name;
    public SlotMode mode;
    public String value;
    public String itemId;

    // Submenu configuration
    public List<RadialSlot> children = new ArrayList<>();
    public int childSlotCount = 8;

    // Macros
    public record Macro(SlotMode mode, String value) {}

    public List<Macro> macros = new ArrayList<>();

    private transient ItemStack cachedStack;

    public RadialSlot(String name, SlotMode mode, String value, String itemId) {
        this.name = name;
        this.mode = mode;
        this.value = value;
        this.itemId = itemId;
    }

    public void clearCache() {
        cachedStack = null;
    }

    /**
     * Fills in missing fields and drops invalid entries, recursing into submenu children.
     */
    public void sanitize() {
        if (name == null) name = "";
        if (mode == null) mode = SlotModeRegistry.getDefaultMode();
        if (value == null) value = "";
        if (itemId == null) itemId = "minecraft:air";

        // The radial menu renders at most 12 slots (plus the back button) per ring
        childSlotCount = Math.clamp(childSlotCount, 2, 12);

        if (macros == null) macros = new ArrayList<>();
        macros.removeIf(macro -> macro == null || macro.mode() == null);

        if (children == null) children = new ArrayList<>();
        children.removeIf(Objects::isNull);
        for (RadialSlot child : children) {
            child.sanitize();
        }

        mode.onInitialize(this);
    }

    public ItemStack getRenderStack() {
        if (cachedStack != null) {
            return cachedStack;
        }

        try {
            Minecraft client = Minecraft.getInstance();

            if (client.level == null) {
                cachedStack = new ItemStack(Items.AIR);
                return cachedStack;
            }

            HolderLookup.Provider registryLookup = client.level.registryAccess();

            ItemInput result = new ItemParser(registryLookup).parse(new StringReader(itemId));

            cachedStack = new ItemStack(result.item(), 1);
            cachedStack.applyComponentsAndValidate(result.components());
        } catch (Exception e) {
            cachedStack = new ItemStack(Items.BARRIER);
            cachedStack.set(DataComponents.CUSTOM_NAME, Component.translatable("radial.item.invalid_id"));
        }

        return cachedStack;
    }
}
