package dev.velolib.radial.ui.screen;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

public class KeybindPickerScreen extends Screen {

    private static final int ENTRY_HEIGHT = 28;

    private final Screen parent;
    private final Consumer<String> onSelect;

    private KeybindList keybindList;

    public KeybindPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Text.translatable("screen.radial.keybind_picker.title"));

        this.parent = parent;
        this.onSelect = onSelect;
    }

    private int getListStartY() {
        return 45;
    }

    private int getListBottom() {
        return height - 40;
    }

    private int getListWidth() {
        return Math.min(350, (int) (width * 0.9));
    }

    private int getListHeight() {
        return Math.max(1, getListBottom() - getListStartY());
    }

    private int getListLeft() {
        return width / 2 - getListWidth() / 2;
    }

    @Override
    protected void init() {
        int listWidth = getListWidth();
        int listLeft = getListLeft();
        int listTop = getListStartY();
        int listHeight = getListHeight();

        TextFieldWidget searchField = new TextFieldWidget(
                textRenderer, listLeft, 15, listWidth, 20, Text.translatable("screen.radial.editor.search"));

        searchField.setPlaceholder(Text.translatable("screen.radial.editor.search"));
        searchField.setChangedListener(this::updateSearch);

        addDrawableChild(searchField);

        keybindList = new KeybindList(MinecraftClient.getInstance(), listWidth, listHeight, listTop, ENTRY_HEIGHT);

        keybindList.setX(listLeft);

        addDrawableChild(keybindList);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), unused -> close())
                .dimensions(width / 2 - 100, height - 28, 200, 20)
                .build());

        setInitialFocus(searchField);

        updateSearch("");
    }

    private void updateSearch(String query) {
        if (keybindList == null) {
            return;
        }

        String q = query.toLowerCase();

        List<KeybindEntry> entries = Arrays.stream(client.options.allKeys)
                .filter(key -> {
                    // BLACKLIST CHECK: Skip our internal radial keys
                    if (dev.velolib.radial.RadialClient.isRadialInternalKey(key)) {
                        return false;
                    }

                    String actionName =
                            Text.translatable(key.getId()).getString().toLowerCase();

                    String category = key.getCategory().getLabel().getString().toLowerCase();

                    return actionName.contains(q) || category.contains(q);
                })
                .map(key -> new KeybindEntry(key, onSelect, this::close))
                .collect(Collectors.toList());

        keybindList.replaceEntries(entries);
        keybindList.setScrollY(0.0);
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {

        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private static class KeybindList extends AlwaysSelectedEntryListWidget<KeybindEntry> {

        private KeybindList(MinecraftClient minecraft, int width, int height, int y, int itemHeight) {

            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return Math.min(330, getWidth() - 20);
        }
    }

    private static class KeybindEntry extends AlwaysSelectedEntryListWidget.Entry<KeybindEntry> {

        private final KeyBinding key;
        private final Consumer<String> onSelect;
        private final Runnable onClose;

        private KeybindEntry(KeyBinding key, Consumer<String> onSelect, Runnable onClose) {

            this.key = key;
            this.onSelect = onSelect;
            this.onClose = onClose;
        }

        @Override
        public void render(DrawContext graphics, int mouseX, int mouseY, boolean hovered, float delta) {

            MinecraftClient client = MinecraftClient.getInstance();

            int left = getX();
            int top = getY();
            int right = left + getWidth();
            int bottom = top + getHeight();

            graphics.fill(left, top + 1, right, bottom - 1, hovered ? 0x80FFFFFF : 0x40000000);

            String actionName = Text.translatable(key.getId()).getString();

            String boundKey = key.getBoundKeyLocalizedText().getString();

            String display = actionName + " [" + boundKey + "]";

            int textY = top + ((bottom - top) - client.textRenderer.fontHeight) / 2;

            graphics.drawText(client.textRenderer, display, left + 8, textY, 0xFFFFFFFF, true);

            Text category = key.getCategory().getLabel();

            int categoryWidth = client.textRenderer.getWidth(category);

            graphics.drawText(client.textRenderer, category, right - categoryWidth - 8, textY, 0xFFAAAAAA, true);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubleClick) {

            if (click.button() != 0) {
                return false;
            }

            onSelect.accept(key.getId());
            onClose.run();

            return true;
        }

        @Override
        public Text getNarration() {
            return Text.translatable(key.getId());
        }
    }
}
