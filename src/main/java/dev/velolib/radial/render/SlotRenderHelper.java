package dev.velolib.radial.render;

import com.mojang.brigadier.StringReader;
import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.util.PhosphorIconCache;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class SlotRenderHelper {

    public static final Identifier SLOT_TEXTURE =
            Identifier.of("minecraft", "gamemode_switcher/slot");
    public static final Identifier SELECTION_TEXTURE =
            Identifier.of("minecraft", "gamemode_switcher/selection");

    public static final String PHOSPHOR_PREFIX = "radial:icon.";
    public static final String GLYPH_PREFIX = "radial:glyph.";
    public static final String EFFECT_PREFIX = "radial:effect.";
    public static final String DYNAMIC_SLOT_PREFIX = "radial:slot.";

    private static final int SLOT_ICON_SIZE = 26;

    private static final Pattern DYNAMIC_SLOT_PATTERN = Pattern.compile(
            "radial:slot\\.(hotbar\\.[0-8]|inventory\\.(1?[0-9]|2[0-6])|armor\\.(head|chest|legs|feet)|offhand)");

    private SlotRenderHelper() {}

    public static ItemStack resolveDynamicItem(String itemId) {
        MinecraftClient minecraft = MinecraftClient.getInstance();

        if (minecraft.player == null) {
            return null;
        }

        if (itemId != null && itemId.startsWith(DYNAMIC_SLOT_PREFIX)) {
            String[] parts = itemId.split("\\.");

            if (parts.length >= 2) {
                String type = parts[1];
                PlayerInventory inv = minecraft.player.getInventory();

                try {
                    switch (type) {
                        case "hotbar":
                            if (parts.length >= 3) {
                                int hbIndex = Integer.parseInt(parts[2]);
                                if (hbIndex >= 0 && hbIndex < 9) {
                                    return inv.getMainStacks().get(hbIndex);
                                }
                            }
                            break;

                        case "inventory":
                            if (parts.length >= 3) {
                                int invIndex = Integer.parseInt(parts[2]);
                                if (invIndex >= 0 && invIndex < 27) {
                                    return inv.getMainStacks().get(invIndex + 9);
                                }
                            }
                            break;

                        case "armor":
                            if (parts.length >= 3) {
                                switch (parts[2]) {
                                    case "head":
                                        return minecraft.player.getEquippedStack(EquipmentSlot.HEAD);
                                    case "chest":
                                        return minecraft.player.getEquippedStack(EquipmentSlot.CHEST);
                                    case "legs":
                                        return minecraft.player.getEquippedStack(EquipmentSlot.LEGS);
                                    case "feet":
                                        return minecraft.player.getEquippedStack(EquipmentSlot.FEET);
                                }
                            }
                            break;

                        case "offhand":
                            return minecraft.player.getOffHandStack();
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            return ItemStack.EMPTY;
        }

        return null;
    }

    /**
     * Parses an item id with optional components (the /give format), or returns a barrier named
     * "invalid id" if it can't be parsed.
     */
    public static ItemStack parseItemStack(String itemId) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client.world == null) {
                return new ItemStack(Items.AIR);
            }

            RegistryWrapper.WrapperLookup registryLookup = client.world.getRegistryManager();

            ItemStringReader.ItemResult result =
                    new ItemStringReader(registryLookup).consume(new StringReader(itemId));

            ItemStack stack = new ItemStack(result.item(), 1);
            stack.applyUnvalidatedChanges(result.components());
            return stack;
        } catch (Exception e) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("radial.item.invalid_id"));
            return barrier;
        }
    }

    /**
     * Whether the icon is drawn as text and can therefore take a tint color.
     */
    public static boolean isTintable(String iconId) {
        return iconId != null && (iconId.startsWith(PHOSPHOR_PREFIX) || iconId.startsWith(GLYPH_PREFIX));
    }

    public static boolean isValidIconId(String iconId) {
        if (iconId == null || iconId.isBlank()) {
            return false;
        }

        if (iconId.startsWith(PHOSPHOR_PREFIX)) {
            return PhosphorIconCache.getIcon(iconId.substring(PHOSPHOR_PREFIX.length())) != null;
        }

        if (iconId.startsWith(GLYPH_PREFIX)) {
            return iconId.length() > GLYPH_PREFIX.length();
        }

        if (iconId.startsWith(EFFECT_PREFIX)) {
            Identifier id = Identifier.tryParse(iconId.substring(EFFECT_PREFIX.length()));
            return id != null && Registries.STATUS_EFFECT.getOptionalValue(id).isPresent();
        }

        if (iconId.startsWith(DYNAMIC_SLOT_PREFIX)) {
            return DYNAMIC_SLOT_PATTERN.matcher(iconId).matches();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            // Components can't be checked without registries, so don't flag anything
            return true;
        }

        try {
            RegistryWrapper.WrapperLookup registryLookup = client.world.getRegistryManager();
            new ItemStringReader(registryLookup).consume(new StringReader(iconId));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static ItemStack getDisplayStack(RadialSlot slot) {
        ItemStack dynamic = resolveDynamicItem(slot.itemId);

        if (dynamic != null) {
            return dynamic;
        }

        return slot.getRenderStack();
    }

    /**
     * Backwards-compatible overload for callers that don't track reveal alpha yet.
     */
    public static void renderSlotIcon(DrawContext graphics, RadialSlot slot, float x, float y) {
        renderSlotIcon(graphics, slot, x, y, 0xFF);
    }

    public static void renderSlotIcon(DrawContext graphics, RadialSlot slot, float x, float y, int alpha) {
        if (slot == null || slot.itemId == null || !slot.mode.shouldRenderIcon()) {
            return;
        }

        int tint = isTintable(slot.itemId) ? slot.getIconColor() : 0xFFFFFF;

        renderIcon(graphics, slot.itemId, (int) x, (int) y, SLOT_ICON_SIZE, (alpha << 24) | tint, slot::getRenderStack);
    }

    /**
     * Draws any icon id centered in a {@code size}x{@code size} box.
     *
     * @param argb      color for text-based and sprite icons; items ignore it since they can't be tinted
     * @param itemStack supplies the stack for plain item ids, letting callers cache the parsed stack
     */
    public static void renderIcon(
            DrawContext graphics,
            String iconId,
            int x,
            int y,
            int size,
            int argb,
            Supplier<ItemStack> itemStack) {
        MinecraftClient client = MinecraftClient.getInstance();

        // --- Phosphor Icon ---
        if (iconId.startsWith(PHOSPHOR_PREFIX)) {
            PhosphorIconCache.PhosphorIcon icon = PhosphorIconCache.getIcon(iconId.substring(PHOSPHOR_PREFIX.length()));
            if (icon == null) {
                return;
            }

            Text component =
                    Text.literal(icon.character()).setStyle(Style.EMPTY.withFont(PhosphorIconCache.FONT));

            // The Phosphor font's glyphs are centered on the baseline, so the box center is the text origin
            int textX = x + Math.round((size - client.textRenderer.getWidth(component)) / 2.0F);
            graphics.drawText(client.textRenderer, component, textX, y + size / 2, argb, false);
            return;
        }

        // --- Glyph ---
        if (iconId.startsWith(GLYPH_PREFIX)) {
            String glyph = iconId.substring(GLYPH_PREFIX.length());

            int textX = x + Math.round((size - client.textRenderer.getWidth(glyph)) / 2.0F);
            int textY = y + Math.round((size - 8) / 2.0F);
            graphics.drawText(client.textRenderer, glyph, textX, textY, argb, false);
            return;
        }

        // --- Effect ---
        if (iconId.startsWith(EFFECT_PREFIX)) {
            Identifier id = Identifier.tryParse(iconId.substring(EFFECT_PREFIX.length()));
            if (id == null) {
                return;
            }

            Optional<StatusEffect> effect = Registries.STATUS_EFFECT.getOptionalValue(id);
            effect.ifPresent(value -> {
                // Drawn at the sprite's native 18px so it stays crisp
                int offset = (size - 18) / 2;
                // Yarn 1.21.11 DrawContext has no tinted gui-sprite overload; draw untinted like EffectIconTab
                graphics.drawGuiTexture(
                        RenderPipelines.GUI_TEXTURED, effectSprite(id), x + offset, y + offset, 18, 18);
            });
            return;
        }

        // --- Dynamic Slot / Item ---
        ItemStack stack = resolveDynamicItem(iconId);
        if (stack == null) {
            stack = itemStack.get();
        }

        if (stack != null && !stack.isEmpty()) {
            int offset = (size - 16) / 2;
            graphics.drawItem(stack, x + offset, y + offset);
        }
    }

    public static Identifier effectSprite(Identifier effectId) {
        return Identifier.of(effectId.getNamespace(), "mob_effect/" + effectId.getPath());
    }
}
