package dev.velolib.radial.ui.widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class DropdownButtonWidget<T> extends ClickableWidget {
    private static final Identifier SPRITE = Identifier.of("minecraft", "widget/text_field");
    private static final Identifier SPRITE_HIGHLIGHTED = Identifier.of("minecraft", "widget/text_field_highlighted");
    private final List<T> options;
    private final Function<T, Text> labelMapper;
    private final Consumer<T> onSelect;
    private final Consumer<DropdownMenuWidget<T>> menuRegistrar;
    private T selectedOption;
    private DropdownMenuWidget<T> activeMenu = null;

    public DropdownButtonWidget(
            int x,
            int y,
            int width,
            int height,
            List<T> options,
            T initialSelection,
            Function<T, Text> labelMapper,
            Consumer<T> onSelect,
            Consumer<DropdownMenuWidget<T>> menuRegistrar) {
        super(x, y, width, height, labelMapper.apply(initialSelection));
        this.options = options;
        this.selectedOption = initialSelection;
        this.labelMapper = labelMapper;
        this.onSelect = onSelect;
        this.menuRegistrar = menuRegistrar;
    }

    public DropdownMenuWidget<T> getActiveMenu() {
        return this.activeMenu;
    }

    public boolean isMenuOpen() {
        return this.activeMenu != null;
    }

    public void closeMenu() {
        this.activeMenu = null;
    }

    public void updateSelection(T option) {
        this.selectedOption = option;
        this.setMessage(this.labelMapper.apply(option));
        this.onSelect.accept(option);
        this.closeMenu();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.isMouseOver(mouseX, mouseY)) {
            this.onClick(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // PLAY SOUND: Triggers the standard click noise when toggling the menu
        this.playDownSound(MinecraftClient.getInstance().getSoundManager());

        if (isMenuOpen()) {
            closeMenu();
        } else {
            this.activeMenu = new DropdownMenuWidget<>(
                    getX(),
                    getY() + getHeight(),
                    this.width,
                    this.height,
                    this.options,
                    this.selectedOption,
                    this.labelMapper,
                    this);
            this.menuRegistrar.accept(this.activeMenu);
        }
    }

    @Override
    protected void renderWidget(DrawContext graphics, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer textRenderer = mc.textRenderer;

        boolean open = isMenuOpen();
        Identifier currentSprite = (this.isFocused() || open) ? SPRITE_HIGHLIGHTED : SPRITE;

        graphics.drawGuiTexture(RenderLayer::getGuiTextured, currentSprite, getX(), getY(), width, height);

        int textColor = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
        Text currentText = this.labelMapper.apply(this.selectedOption);

        graphics.drawText(textRenderer, currentText, getX() + 4, getY() + (getHeight() - 8) / 2, textColor, true);
        graphics.drawText(
                textRenderer,
                Text.literal(open ? "▲" : "▼"),
                getX() + width - 12,
                getY() + (getHeight() - 8) / 2,
                textColor,
                true);
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, getMessage());
    }
}
