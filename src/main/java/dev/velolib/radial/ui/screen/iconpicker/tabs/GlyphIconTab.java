package dev.velolib.radial.ui.screen.iconpicker.tabs;

import dev.velolib.radial.render.SlotRenderHelper;
import dev.velolib.radial.ui.screen.iconpicker.GridIconTab;
import dev.velolib.radial.ui.screen.iconpicker.IconSearch;
import dev.velolib.radial.util.GlyphCache;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class GlyphIconTab extends GridIconTab<GlyphCache.Glyph> {

    public GlyphIconTab(String currentId, Consumer<String> onSelect, Runnable onClose) {
        super(currentId, onSelect, onClose);
    }

    @Override
    public Text getTitle() {
        return Text.translatable("screen.radial.editor.icon_picker.glyphs");
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
            DrawContext graphics,
            int x,
            int y,
            int mouseX,
            int mouseY,
            GlyphCache.Glyph glyph,
            boolean hovered) {
        SlotRenderHelper.renderIcon(graphics, getIconId(glyph), x, y, getSlotSize(), 0xFFFFFFFF, () -> null);

        if (hovered) {
            List<Text> lines = glyph.name() != null
                    ? List.of(
                            Text.literal(glyph.name()),
                            Text.literal(glyph.hex()).formatted(Formatting.DARK_GRAY))
                    : List.of(Text.literal(glyph.hex()));

            graphics.drawTooltip(MinecraftClient.getInstance().textRenderer, lines, mouseX, mouseY);
        }
    }

    @Override
    protected String getIconId(GlyphCache.Glyph glyph) {
        return SlotRenderHelper.GLYPH_PREFIX + glyph.character();
    }

    @Override
    protected Text getItemNarration(GlyphCache.Glyph glyph) {
        return Text.literal(glyph.displayName());
    }
}
