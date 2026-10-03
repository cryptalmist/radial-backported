package dev.velolib.radial.ui.widget;

import java.util.List;
import java.util.function.Function;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class DropdownMenuWidget<T> extends AbstractWidget {
    private static final int MAX_VISIBLE_ITEMS = 6;
    private static final ResourceLocation SPRITE_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath("minecraft", "widget/text_field_highlighted");
    private final List<T> options;
    private final T currentSelection;
    private final Function<T, Component> labelMapper;
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
            Function<T, Component> labelMapper,
            DropdownButtonWidget<T> parentButton) {
        super(x, y, width, Math.min(options.size(), MAX_VISIBLE_ITEMS) * itemHeight, Component.empty());
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
        /*
         * Only consume the event when the mouse is actually inside
         * the dropdown. This prevents the event from being passed
         * to unrelated widgets.
         */
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        int maxScroll = getMaxScroll();

        if (maxScroll <= 0) {
            // Still consume the event because the dropdown is hovered.
            return true;
        }

        this.scrollAmount -= scrollY * this.itemHeight;
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0, maxScroll);

        return true;
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
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            T selected = options.get(index);
            this.parentButton.updateSelection(selected);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Opaque fallback background: guarantees the menu occludes the form
        // beneath it even if the sprite below fails to cover on some versions.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);

        graphics.blitSprite(SPRITE_HIGHLIGHTED, getX(), getY(), width, height);

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
                graphics.blitSprite(SPRITE_HIGHLIGHTED, getX(), itemY, width, itemHeight);
            }

            Component text = labelMapper.apply(option);
            int optionColor = option.equals(currentSelection) ? 0xFF55FF55 : 0xFFFFFFFF;
            graphics.drawString(font, text, getX() + 4, itemY + (itemHeight - 8) / 2, optionColor);
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
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
}
