package dev.velolib.radial.ui.screen.iconpicker.tabs;

import dev.velolib.radial.render.SlotRenderHelper;
import dev.velolib.radial.ui.screen.iconpicker.GridIconTab;
import dev.velolib.radial.ui.screen.iconpicker.IconSearch;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class EffectIconTab extends GridIconTab<EffectIconTab.EffectEntry> {

    // Built per picker so the translated names follow the current language
    private final List<EffectEntry> effects = Registries.STATUS_EFFECT.stream()
            .map(effect -> {
                Identifier id = Objects.requireNonNull(Registries.STATUS_EFFECT.getId(effect));
                Text name = Text.translatable(effect.getTranslationKey());
                String lowerName = name.getString().toLowerCase();

                return new EffectEntry(id, name, lowerName, lowerName + " " + id);
            })
            .toList();

    public EffectIconTab(String currentId, Consumer<String> onSelect, Runnable onClose) {
        super(currentId, onSelect, onClose);
    }

    @Override
    public Text getTitle() {
        return Text.translatable("screen.radial.editor.icon_picker.effects");
    }

    @Override
    public boolean accepts(String iconId) {
        return iconId.startsWith(SlotRenderHelper.EFFECT_PREFIX);
    }

    @Override
    protected List<EffectEntry> search(String query) {
        return IconSearch.rank(effects, query, EffectEntry::lowerName, EffectEntry::searchText);
    }

    @Override
    protected void renderIcon(
            DrawContext graphics, int x, int y, int mouseX, int mouseY, EffectEntry effect, boolean hovered) {
        SlotRenderHelper.renderIcon(graphics, getIconId(effect), x, y, getSlotSize(), 0xFFFFFFFF, () -> null);

        if (hovered) {
            graphics.drawTooltip(MinecraftClient.getInstance().textRenderer, effect.name(), mouseX, mouseY);
        }
    }

    @Override
    protected String getIconId(EffectEntry effect) {
        return SlotRenderHelper.EFFECT_PREFIX + effect.id();
    }

    @Override
    protected Text getItemNarration(EffectEntry effect) {
        return effect.name();
    }

    public record EffectEntry(Identifier id, Text name, String lowerName, String searchText) {}
}
