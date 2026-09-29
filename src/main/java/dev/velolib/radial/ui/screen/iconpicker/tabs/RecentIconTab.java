package dev.velolib.radial.ui.screen.iconpicker.tabs;

import dev.velolib.radial.render.SlotRenderHelper;
import dev.velolib.radial.ui.screen.iconpicker.GridIconTab;
import dev.velolib.radial.ui.screen.iconpicker.IconSearch;
import dev.velolib.radial.util.IconHistory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Favorited icons first, then recently picked ones.
 */
public class RecentIconTab extends GridIconTab<String> {

    private final Map<String, ItemStack> stacks = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();

    public RecentIconTab(String currentId, Consumer<String> onSelect, Runnable onClose) {
        super(currentId, onSelect, onClose);
    }

    public static boolean hasEntries() {
        return !IconHistory.getFavorites().isEmpty() || !IconHistory.getRecent().isEmpty();
    }

    @Override
    public Component getTitle() {
        return Component.translatable("screen.radial.editor.icon_picker.recent");
    }

    @Override
    protected Component getEmptyMessage() {
        return getQuery().isEmpty()
                ? Component.translatable("screen.radial.editor.icon_picker.recent.empty")
                : super.getEmptyMessage();
    }

    @Override
    protected List<String> search(String query) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(IconHistory.getFavorites());
        ids.addAll(IconHistory.getRecent());

        return IconSearch.rank(new ArrayList<>(ids), query, id -> getLabel(id).toLowerCase(), id -> id.toLowerCase());
    }

    @Override
    protected void onFavoritesChanged() {
        refreshResults();
    }

    @Override
    protected void renderIcon(
            GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY, String id, boolean hovered) {
        SlotRenderHelper.renderIcon(graphics, id, x, y, getSlotSize(), 0xFFFFFFFF, () -> getStack(id));

        if (hovered) {
            Minecraft client = Minecraft.getInstance();
            ItemStack dynamic = SlotRenderHelper.resolveDynamicItem(id);

            if (dynamic == null && !id.startsWith("radial:")) {
                graphics.setTooltipForNextFrame(client.font, getStack(id), mouseX, mouseY);
            } else {
                graphics.setTooltipForNextFrame(client.font, Component.literal(getLabel(id)), mouseX, mouseY);
            }
        }
    }

    @Override
    protected String getIconId(String id) {
        return id;
    }

    @Override
    protected Component getItemNarration(String id) {
        return Component.literal(getLabel(id));
    }

    private ItemStack getStack(String id) {
        return stacks.computeIfAbsent(id, SlotRenderHelper::parseItemStack);
    }

    private String getLabel(String id) {
        return labels.computeIfAbsent(id, this::describe);
    }

    private String describe(String id) {
        if (id.startsWith(SlotRenderHelper.PHOSPHOR_PREFIX)) {
            return id.substring(SlotRenderHelper.PHOSPHOR_PREFIX.length());
        }

        if (id.startsWith(SlotRenderHelper.GLYPH_PREFIX)) {
            String glyph = id.substring(SlotRenderHelper.GLYPH_PREFIX.length());
            String name = glyph.isEmpty() ? null : Character.getName(glyph.codePointAt(0));
            return name != null ? name : glyph;
        }

        if (id.startsWith(SlotRenderHelper.EFFECT_PREFIX)) {
            Identifier effectId = Identifier.tryParse(id.substring(SlotRenderHelper.EFFECT_PREFIX.length()));
            if (effectId != null) {
                return BuiltInRegistries.MOB_EFFECT
                        .getOptional(effectId)
                        .map(effect -> Component.translatable(effect.getDescriptionId())
                                .getString())
                        .orElse(id);
            }
            return id;
        }

        if (id.startsWith("radial:")) {
            return id;
        }

        return getStack(id).getHoverName().getString();
    }
}
