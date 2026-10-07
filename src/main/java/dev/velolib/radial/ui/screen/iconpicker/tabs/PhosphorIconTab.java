package dev.velolib.radial.ui.screen.iconpicker.tabs;

import dev.velolib.radial.render.SlotRenderHelper;
import dev.velolib.radial.ui.screen.iconpicker.GridIconTab;
import dev.velolib.radial.ui.screen.iconpicker.IconSearch;
import dev.velolib.radial.util.PhosphorIconCache;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class PhosphorIconTab extends GridIconTab<PhosphorIconCache.PhosphorIcon> {

    public PhosphorIconTab(String currentId, Consumer<String> onSelect, Runnable onClose) {
        super(currentId, onSelect, onClose);
    }

    @Override
    public Component getTitle() {
        return Component.translatable("screen.radial.editor.icon_picker.phosphor");
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
            GuiGraphics graphics, int x, int y, int mouseX, int mouseY, PhosphorIconCache.PhosphorIcon icon) {
        SlotRenderHelper.renderIcon(graphics, getIconId(icon), x, y, getSlotSize(), 0xFFFFFFFF, () -> null);
    }

    @Override
    protected void renderHoverTooltip(
            GuiGraphics graphics, PhosphorIconCache.PhosphorIcon icon, int mouseX, int mouseY) {
        graphics.renderTooltip(
                Minecraft.getInstance().font, Component.literal(icon.name()), mouseX, mouseY);
    }

    @Override
    protected String getIconId(PhosphorIconCache.PhosphorIcon icon) {
        return SlotRenderHelper.PHOSPHOR_PREFIX + icon.name();
    }

    @Override
    protected Component getItemNarration(PhosphorIconCache.PhosphorIcon icon) {
        return Component.literal(icon.name());
    }
}
