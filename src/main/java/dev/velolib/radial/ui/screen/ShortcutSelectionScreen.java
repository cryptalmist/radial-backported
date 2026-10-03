package dev.velolib.radial.ui.screen;

import dev.velolib.radial.api.ShortcutEntry;
import dev.velolib.radial.api.ShortcutRegistry;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ShortcutSelectionScreen extends Screen {

    private static final int ENTRY_HEIGHT = 28;

    private final Screen parent;
    private final Consumer<Identifier> onSelect;

    private ShortcutList shortcutList;

    public ShortcutSelectionScreen(Screen parent, Consumer<Identifier> onSelect) {

        super(Text.translatable("screen.radial.shortcut_picker.title"));

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

        shortcutList = new ShortcutList(MinecraftClient.getInstance(), listWidth, listHeight, listTop, ENTRY_HEIGHT);

        shortcutList.setX(listLeft);

        addDrawableChild(shortcutList);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), unused -> close())
                .dimensions(width / 2 - 100, height - 28, 200, 20)
                .build());

        setInitialFocus(searchField);

        updateSearch("");
    }

    private void updateSearch(String query) {
        if (shortcutList == null) {
            return;
        }

        String q = query.toLowerCase();

        List<ShortcutEntryItem> entries = ShortcutRegistry.getRegisteredShortcuts().entrySet().stream()
                .filter(entry -> {
                    String name = entry.getValue().name().getString().toLowerCase();

                    String id = entry.getKey().toString().toLowerCase();

                    return name.contains(q) || id.contains(q);
                })
                .map(entry -> new ShortcutEntryItem(entry, onSelect, this::close))
                .collect(Collectors.toList());

        shortcutList.replaceEntries(entries);
        shortcutList.setScrollY(0.0);
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

    private static class ShortcutList extends AlwaysSelectedEntryListWidget<ShortcutEntryItem> {

        private ShortcutList(MinecraftClient minecraft, int width, int height, int y, int itemHeight) {

            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return Math.min(330, getWidth() - 20);
        }
    }

    private static class ShortcutEntryItem extends AlwaysSelectedEntryListWidget.Entry<ShortcutEntryItem> {

        private final Identifier id;
        private final ShortcutEntry entry;
        private final Consumer<Identifier> onSelect;
        private final Runnable onClose;

        private ShortcutEntryItem(
                Map.Entry<Identifier, ShortcutEntry> mapEntry, Consumer<Identifier> onSelect, Runnable onClose) {

            this.id = mapEntry.getKey();
            this.entry = mapEntry.getValue();
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

            int textY = top + ((bottom - top) - client.textRenderer.fontHeight) / 2;

            String displayName = entry.name().getString();

            graphics.drawText(client.textRenderer, displayName, left + 8, textY, 0xFFFFFFFF, true);

            String idString = id.toString();

            int idWidth = client.textRenderer.getWidth(idString);

            graphics.drawText(client.textRenderer, idString, right - idWidth - 8, textY, 0xFFAAAAAA, true);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubleClick) {

            if (click.button() != 0) {
                return false;
            }

            onSelect.accept(id);
            onClose.run();

            return true;
        }

        @Override
        public Text getNarration() {
            return entry.name();
        }
    }
}
