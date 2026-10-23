package dev.velolib.radial.ui.widget;

import java.util.List;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class DropdownMenuWidget<T> extends ClickableWidget {
    private static final int MAX_VISIBLE_ITEMS = 6;
    private static final Identifier SPRITE_HIGHLIGHTED = Identifier.of("minecraft", "widget/text_field_highlighted");
    private final List<T> options;
    private final T currentSelection;
    private final Function<T, Text> labelMapper;
    private final DropdownButtonWidget<T> parentButton;
    private final int itemHeight;
    private double scrollAmount = 0;

    public DropdownMenuWidget(
            int x,
            int y,
            int width,
            int itemHeight,
            List<T> options,
            T currentSelection,
            Function<T, Text> labelMapper,
            DropdownButtonWidget<T> parentButton) {
        super(x, y, width, Math.min(options.size(), MAX_VISIBLE_ITEMS) * itemHeight, Text.empty());
        this.options = options;
        this.currentSelection = currentSelection;
        this.labelMapper = labelMapper;
        this.parentButton = parentButton;
        this.itemHeight = itemHeight;
    }

    private int getMaxScroll() {
        return Math.max(0, (this.options.size() * this.itemHeight) - this.height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isMouseOver(mouseX, mouseY)) {
            int maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                this.scrollAmount -= scrollY * this.itemHeight;
                this.scrollAmount = MathHelper.clamp(this.scrollAmount, 0, maxScroll);

                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isMouseOver(mouseX, mouseY)) {
            this.onClick(mouseX, mouseY);
            return true;
        }

        this.parentButton.closeMenu();
        return false;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {

        int index = (int) ((mouseY - getY() + this.scrollAmount) / itemHeight);

        if (index >= 0 && index < options.size()) {
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            T selected = options.get(index);
            this.parentButton.updateSelection(selected);
        }
    }

    @Override
    protected void renderWidget(DrawContext graphics, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer textRenderer = mc.textRenderer;

        // 1.21.4: widget/text_field_highlighted is a 9-sliced black box with a white border, so the panel
        // only needs the sprite; the fill below is kept as a harmless solid base.
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF101010);

        graphics.drawGuiTexture(RenderLayer::getGuiTextured, SPRITE_HIGHLIGHTED, getX(), getY(), width, height);

        graphics.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);

        for (int i = 0; i < options.size(); i++) {
            T option = options.get(i);
            int itemY = (int) (getY() + (i * itemHeight) - this.scrollAmount);

            if (itemY + itemHeight < getY() || itemY > getY() + height) continue;

            boolean isItemHovered = mouseX >= getX()
                    && mouseX < getX() + width
                    && mouseY >= itemY
                    && mouseY < itemY + itemHeight
                    && mouseY >= getY()
                    && mouseY <= getY() + height;

            if (isItemHovered) {
                graphics.drawGuiTexture(
                        RenderLayer::getGuiTextured, SPRITE_HIGHLIGHTED, getX(), itemY, width, itemHeight);
            }

            Text text = labelMapper.apply(option);
            int optionColor = option.equals(currentSelection) ? 0xFF55FF55 : 0xFFFFFFFF;
            graphics.drawText(textRenderer, text, getX() + 4, itemY + (itemHeight - 8) / 2, optionColor, true);
        }
        graphics.disableScissor();

        // --- SCROLLBAR RENDER ---
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarWidth = 2;
            int scrollbarHeight =
                    Math.max(8, (int) ((float) this.height * this.height / (options.size() * itemHeight)));
            int scrollbarX = getX() + width - scrollbarWidth - 2; // Moved in slightly to respect the border
            int scrollbarY = getY() + 1 + (int) ((this.scrollAmount / maxScroll) * (this.height - 2 - scrollbarHeight));

            graphics.fill(
                    scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0x80888888);
        }
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
