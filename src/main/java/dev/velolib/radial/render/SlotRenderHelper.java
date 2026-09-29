package dev.velolib.radial.render;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.util.PhosphorIconCache;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class SlotRenderHelper {

    public static final Identifier SLOT_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "gamemode_switcher/slot");
    public static final Identifier SELECTION_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "gamemode_switcher/selection");

    private SlotRenderHelper() {}

    public static ItemStack resolveDynamicItem(String itemId) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return null;
        }

        if (itemId != null && itemId.startsWith("radial:slot.")) {
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
                                    return inv.getNonEquipmentItems().get(hbIndex);
                                }
                            }
                            break;

                        case "inventory":
                            if (parts.length >= 3) {
                                int invIndex = Integer.parseInt(parts[2]);
                                if (invIndex >= 0 && invIndex < 27) {
                                    return inv.getNonEquipmentItems().get(invIndex + 9);
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

    public static ItemStack getDisplayStack(RadialSlot slot) {
        ItemStack dynamic = resolveDynamicItem(slot.itemId);

        if (dynamic != null) {
            return dynamic;
        }

        return slot.getRenderStack();
    }

    public static void renderSlotIcon(GuiGraphicsExtractor graphics, RadialSlot slot, float x, float y) {
        if (slot == null || slot.itemId == null || !slot.mode.shouldRenderIcon()) {
            return;
        }

        String itemId = slot.itemId;

        // --- Phosphor Icon ---
        if (itemId.startsWith("radial:icon.")) {
            renderPhosphorIcon(graphics, itemId.substring("radial:icon.".length()), x, y);
            return;
        }

        // --- Glyph ---
        if (itemId.startsWith("radial:glyph.")) {
            String glyph = itemId.substring("radial:glyph.".length());
            Minecraft client = Minecraft.getInstance();

            int textWidth = client.font.width(glyph);
            int centerX = (int) x + Math.round((26 - textWidth) / 2.0f);
            int centerY = (int) y + Math.round((26 - 8) / 2.0f);

            graphics.text(client.font, glyph, centerX, centerY, 0xFFFFFFFF);
            return;
        }

        // --- Effect ---
        if (itemId.startsWith("radial:effect.")) {
            Identifier id = Identifier.tryParse(itemId.substring("radial:effect.".length()));
            if (id == null) {
                return;
            }

            Optional<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getOptional(id);
            effect.ifPresent(value -> {
                String path = Objects.requireNonNull(BuiltInRegistries.MOB_EFFECT.getKey(value))
                        .getPath();
                Identifier spriteId = Identifier.fromNamespaceAndPath("minecraft", "mob_effect/" + path);

                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, spriteId, (int) x + 4, (int) y + 4, 18, 18);
            });
            return;
        }

        // --- Dynamic Slot ---
        ItemStack stack = getDisplayStack(slot);

        if (stack != null && !stack.isEmpty()) {
            graphics.fakeItem(stack, (int) x + 5, (int) y + 5);
        }
    }

    private static void renderPhosphorIcon(GuiGraphicsExtractor graphics, String iconName, float x, float y) {
        PhosphorIconCache.PhosphorIcon icon = PhosphorIconCache.getIcon(iconName);

        if (icon == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Component component =
                Component.literal(icon.character()).setStyle(Style.EMPTY.withFont(PhosphorIconCache.FONT));

        int textWidth = client.font.width(component);
        int textX = (int) x + Math.round((26 - textWidth) / 2.0F);
        int textY = (int) y + Math.round((26 - client.font.lineHeight) / 2.0F) + 4;

        graphics.text(client.font, component, textX, textY, 0xFFFFFFFF, false);
    }
}
