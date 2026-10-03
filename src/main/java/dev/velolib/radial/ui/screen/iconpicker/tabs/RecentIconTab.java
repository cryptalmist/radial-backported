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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
    public Text getTitle() {
        return Text.translatable("screen.radial.editor.icon_picker.recent");
    }

    @Override
    protected Text getEmptyMessage() {
        return getQuery().isEmpty()
                ? Text.translatable("screen.radial.editor.icon_picker.recent.empty")
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
            DrawContext graphics, int x, int y, int mouseX, int mouseY, String id, boolean hovered) {
        SlotRenderHelper.renderIcon(graphics, id, x, y, getSlotSize(), 0xFFFFFFFF, () -> getStack(id));

        if (hovered) {
            MinecraftClient client = MinecraftClient.getInstance();
            ItemStack dynamic = SlotRenderHelper.resolveDynamicItem(id);

            if (dynamic == null && !id.startsWith("radial:")) {
                graphics.drawTooltip(
                        client.textRenderer,
                        Screen.getTooltipFromItem(client, getStack(id)),
                        getStack(id).getTooltipData(),
                        mouseX,
                        mouseY);
            } else {
                graphics.drawTooltip(client.textRenderer, Text.literal(getLabel(id)), mouseX, mouseY);
            }
        }
    }

    @Override
    protected String getIconId(String id) {
        return id;
    }

    @Override
    protected Text getItemNarration(String id) {
        return Text.literal(getLabel(id));
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
                return Registries.STATUS_EFFECT
                        .getOptionalValue(effectId)
                        .map(effect -> Text.translatable(effect.getTranslationKey()).getString())
                        .orElse(id);
            }
            return id;
        }

        if (id.startsWith("radial:")) {
            return id;
        }

        return getStack(id).getName().getString();
    }
}
