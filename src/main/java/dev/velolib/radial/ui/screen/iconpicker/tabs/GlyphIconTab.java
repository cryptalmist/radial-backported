package dev.velolib.radial.ui.screen.iconpicker.tabs;

import dev.velolib.radial.render.SlotRenderHelper;
import dev.velolib.radial.ui.screen.iconpicker.GridIconTab;
import dev.velolib.radial.ui.screen.iconpicker.IconSearch;
import dev.velolib.radial.util.GlyphCache;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class GlyphIconTab extends GridIconTab<GlyphCache.Glyph> {

    public GlyphIconTab(String currentId, Consumer<String> onSelect, Runnable onClose) {
        super(currentId, onSelect, onClose);
    }

    @Override
    public Component getTitle() {
        return Component.translatable("screen.radial.editor.icon_picker.glyphs");
    }

    @Override
    public boolean accepts(String iconId) {
        return iconId.startsWith(SlotRenderHelper.GLYPH_PREFIX);
    }

    @Override
    protected List<GlyphCache.Glyph> search(String query) {
        return IconSearch.rank(
                GlyphCache.getGlyphs(),
                query,
                glyph -> glyph.displayName().toLowerCase(Locale.ROOT),
                GlyphCache.Glyph::searchText);
    }

    @Override
    protected void renderIcon(
            GuiGraphics graphics, int x, int y, int mouseX, int mouseY, GlyphCache.Glyph glyph) {
        SlotRenderHelper.renderIcon(graphics, getIconId(glyph), x, y, getSlotSize(), 0xFFFFFFFF, () -> null);
    }

    @Override
    protected void renderHoverTooltip(GuiGraphics graphics, GlyphCache.Glyph glyph, int mouseX, int mouseY) {
        List<Component> lines = glyph.name() != null
                ? List.of(
                        Component.literal(glyph.name()),
                        Component.literal(glyph.hex()).withStyle(ChatFormatting.DARK_GRAY))
                : List.of(Component.literal(glyph.hex()));

        graphics.renderTooltip(Minecraft.getInstance().font, lines, Optional.empty(), mouseX, mouseY);
    }

    @Override
    protected String getIconId(GlyphCache.Glyph glyph) {
        return SlotRenderHelper.GLYPH_PREFIX + glyph.character();
    }

    @Override
    protected Component getItemNarration(GlyphCache.Glyph glyph) {
        return Component.literal(glyph.displayName());
    }
}
