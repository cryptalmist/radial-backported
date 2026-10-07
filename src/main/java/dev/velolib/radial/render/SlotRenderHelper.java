package dev.velolib.radial.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.StringReader;
import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.util.PhosphorIconCache;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SlotRenderHelper {

    public static final ResourceLocation SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "gamemode_switcher/slot");
    public static final ResourceLocation SELECTION_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "gamemode_switcher/selection");

    public static final String PHOSPHOR_PREFIX = "radial:icon.";
    public static final String GLYPH_PREFIX = "radial:glyph.";
    public static final String EFFECT_PREFIX = "radial:effect.";
    public static final String DYNAMIC_SLOT_PREFIX = "radial:slot.";

    private static final int SLOT_ICON_SIZE = 26;

    private static final Pattern DYNAMIC_SLOT_PATTERN = Pattern.compile(
            "radial:slot\\.(hotbar\\.[0-8]|inventory\\.(1?[0-9]|2[0-6])|armor\\.(head|chest|legs|feet)|offhand)");

    private SlotRenderHelper() {}

    public static ItemStack resolveDynamicItem(String itemId) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return null;
        }

        if (itemId != null && itemId.startsWith(DYNAMIC_SLOT_PREFIX)) {
            String[] parts = itemId.split("\\.");

            if (parts.length >= 2) {
                String type = parts[1];
                Inventory inv = minecraft.player.getInventory();

                try {
                    switch (type) {
                        case "hotbar":
                            if (parts.length >= 3) {
                                int hbIndex = Integer.parseInt(parts[2]);
                                if (hbIndex >= 0 && hbIndex < 9) {
                                    return inv.getItem(hbIndex);
                                }
                            }
                            break;

                        case "inventory":
                            if (parts.length >= 3) {
                                int invIndex = Integer.parseInt(parts[2]);
                                if (invIndex >= 0 && invIndex < 27) {
                                    return inv.getItem(invIndex + 9);
                                }
                            }
                            break;

                        case "armor":
                            if (parts.length >= 3) {
                                switch (parts[2]) {
                                    case "head":
                                        return minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
                                    case "chest":
                                        return minecraft.player.getItemBySlot(EquipmentSlot.CHEST);
                                    case "legs":
                                        return minecraft.player.getItemBySlot(EquipmentSlot.LEGS);
                                    case "feet":
                                        return minecraft.player.getItemBySlot(EquipmentSlot.FEET);
                                }
                            }
                            break;

                        case "offhand":
                            return minecraft.player.getOffhandItem();
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
            Minecraft client = Minecraft.getInstance();

            if (client.level == null) {
                return new ItemStack(Items.AIR);
            }

            ItemParser.ItemResult result =
                    new ItemParser(client.level.registryAccess()).parse(new StringReader(itemId));

            ItemStack stack = new ItemStack(result.item(), 1);
            stack.applyComponentsAndValidate(result.components());
            return stack;
        } catch (Exception e) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.set(DataComponents.CUSTOM_NAME, Component.translatable("radial.item.invalid_id"));
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
            ResourceLocation id = ResourceLocation.tryParse(iconId.substring(EFFECT_PREFIX.length()));
            return id != null && BuiltInRegistries.MOB_EFFECT.containsKey(id);
        }

        if (iconId.startsWith(DYNAMIC_SLOT_PREFIX)) {
            return DYNAMIC_SLOT_PATTERN.matcher(iconId).matches();
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            // Components can't be checked without registries, so don't flag anything
            return true;
        }

        try {
            new ItemParser(client.level.registryAccess()).parse(new StringReader(iconId));
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

    public static void renderSlotIcon(GuiGraphics graphics, RadialSlot slot, float x, float y) {
        renderSlotIcon(graphics, slot, x, y, 255);
    }

    public static void renderSlotIcon(GuiGraphics graphics, RadialSlot slot, float x, float y, int alpha) {
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
            GuiGraphics graphics,
            String iconId,
            int x,
            int y,
            int size,
            int argb,
            Supplier<ItemStack> itemStack) {
        Minecraft client = Minecraft.getInstance();

        // --- Phosphor Icon ---
        if (iconId.startsWith(PHOSPHOR_PREFIX)) {
            PhosphorIconCache.PhosphorIcon icon = PhosphorIconCache.getIcon(iconId.substring(PHOSPHOR_PREFIX.length()));
            if (icon == null) {
                return;
            }

            Component component =
                    Component.literal(icon.character()).setStyle(Style.EMPTY.withFont(PhosphorIconCache.FONT));

            // The Phosphor font's glyphs are centered on the baseline, so the box center is the text origin
            int textX = x + Math.round((size - client.font.width(component)) / 2.0F);
            float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.drawString(client.font, component, textX, y + size / 2, argb, false);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }

        // --- Glyph ---
        if (iconId.startsWith(GLYPH_PREFIX)) {
            String glyph = iconId.substring(GLYPH_PREFIX.length());

            int textX = x + Math.round((size - client.font.width(glyph)) / 2.0F);
            int textY = y + Math.round((size - 8) / 2.0F);
            graphics.drawString(client.font, glyph, textX, textY, argb);
            return;
        }

        // --- Effect ---
        // In 1.21.1 mob effect icons live in their own atlas (MobEffectTextureManager),
        // not the GUI atlas, so blitSprite can't see them. Draw the atlas sprite directly.
        if (iconId.startsWith(EFFECT_PREFIX)) {
            ResourceLocation id = ResourceLocation.tryParse(iconId.substring(EFFECT_PREFIX.length()));
            if (id == null) {
                return;
            }

            Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.getHolder(id);
            if (holder.isEmpty()) {
                return;
            }

            TextureAtlasSprite sprite =
                    Minecraft.getInstance().getMobEffectTextures().get(holder.get());

            // Drawn at the sprite's native 18px so it stays crisp
            int offset = (size - 18) / 2;
            graphics.blit(x + offset, y + offset, 0, 18, 18, sprite);
            return;
        }

        // --- Dynamic Slot / Item ---
        ItemStack stack = resolveDynamicItem(iconId);
        if (stack == null) {
            stack = itemStack.get();
        }

        if (stack != null && !stack.isEmpty()) {
            int offset = (size - 16) / 2;
            graphics.renderFakeItem(stack, x + offset, y + offset);
        }
    }
}
