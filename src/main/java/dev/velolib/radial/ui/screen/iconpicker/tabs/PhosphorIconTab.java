package dev.velolib.radial.ui.screen.iconpicker.tabs;

import dev.velolib.radial.render.SlotRenderHelper;
import dev.velolib.radial.ui.screen.iconpicker.GridIconTab;
import dev.velolib.radial.ui.screen.iconpicker.IconSearch;
import dev.velolib.radial.util.PhosphorIconCache;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class PhosphorIconTab extends GridIconTab<PhosphorIconCache.PhosphorIcon> {

    public PhosphorIconTab(String currentId, Consumer<String> onSelect, Runnable onClose) {
        super(currentId, onSelect, onClose);
    }

    @Override
    public Text getTitle() {
        return Text.translatable("screen.radial.editor.icon_picker.phosphor");
    }

    @Override
    public boolean accepts(String iconId) {
        return iconId.startsWith(SlotRenderHelper.PHOSPHOR_PREFIX);
    }

    @Override
    protected List<PhosphorIconCache.PhosphorIcon> search(String query) {
        return IconSearch.rank(
                PhosphorIconCache.getIcons(),
                query,
                icon -> icon.name().toLowerCase(),
                PhosphorIconCache.PhosphorIcon::searchText);
    }

    @Override
    protected void renderIcon(
            DrawContext graphics,
            int x,
            int y,
            int mouseX,
            int mouseY,
            PhosphorIconCache.PhosphorIcon icon,
            boolean hovered) {
        SlotRenderHelper.renderIcon(graphics, getIconId(icon), x, y, getSlotSize(), 0xFFFFFFFF, () -> null);

        if (hovered) {
            graphics.drawTooltip(
                    MinecraftClient.getInstance().textRenderer, Text.literal(icon.name()), mouseX, mouseY);
        }
    }

    @Override
    protected String getIconId(PhosphorIconCache.PhosphorIcon icon) {
        return SlotRenderHelper.PHOSPHOR_PREFIX + icon.name();
    }

    @Override
    protected Text getItemNarration(PhosphorIconCache.PhosphorIcon icon) {
        return Text.literal(icon.name());
    }
}
