package dev.velolib.radial.ui.screen;

import dev.velolib.radial.integration.MalilibIntegration;
import dev.velolib.radial.integration.MalilibIntegration.MalilibAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

public class MalilibSelectionScreen extends Screen {

    private static final int ENTRY_HEIGHT = 28;

    private final Screen parent;
    private final Consumer<MalilibAction> onSelect;
    private final Map<String, List<MalilibAction>> actionsByMod;

    private final List<ButtonWidget> tabButtons = new ArrayList<>();

    private String currentTab;
    private List<MalilibAction> currentActions = new ArrayList<>();

    private TextFieldWidget searchField;
    private MalilibList malilibList;

    public MalilibSelectionScreen(Screen parent, Consumer<MalilibAction> onSelect) {

        super(Text.translatable("screen.radial.malilib_picker.title"));

        this.parent = parent;
        this.onSelect = onSelect;

        List<MalilibAction> actions = MalilibIntegration.getAllActions();

        this.actionsByMod = actions.stream()
                .collect(Collectors.groupingBy(MalilibAction::modName, TreeMap::new, Collectors.toList()));

        if (!actionsByMod.isEmpty()) {
            currentTab = actionsByMod.keySet().iterator().next();
        }
    }

    private int getListStartY() {
        return 65;
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
        tabButtons.clear();

        if (actionsByMod.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), unused -> close())
                    .dimensions(width / 2 - 100, height - 28, 200, 20)
                    .build());

            return;
        }

        int tabWidth = Math.min(80, width / actionsByMod.size());

        int xOffset = (width - tabWidth * actionsByMod.size()) / 2;

        for (String modName : actionsByMod.keySet()) {
            ButtonWidget button = ButtonWidget.builder(Text.literal(modName), unused -> {
                        setTab(modName);
                        updateTabButtonStates();
                    })
                    .dimensions(xOffset, 10, tabWidth, 20)
                    .build();

            button.active = !modName.equals(currentTab);

            tabButtons.add(button);
            addDrawableChild(button);

            xOffset += tabWidth;
        }

        int listWidth = getListWidth();

        searchField = new TextFieldWidget(
                textRenderer,
                width / 2 - listWidth / 2,
                35,
                listWidth,
                20,
                Text.translatable("screen.radial.editor.search"));

        searchField.setPlaceholder(Text.translatable("screen.radial.editor.search"));

        searchField.setChangedListener(this::updateSearch);

        addDrawableChild(searchField);

        malilibList = new MalilibList(
                MinecraftClient.getInstance(), listWidth, getListHeight(), getListStartY(), ENTRY_HEIGHT);

        malilibList.setX(getListLeft());

        addDrawableChild(malilibList);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), unused -> close())
                .dimensions(width / 2 - 100, height - 28, 200, 20)
                .build());

        setInitialFocus(searchField);

        setTab(currentTab);
    }

    private void setTab(String tabName) {
        currentTab = tabName;

        currentActions = actionsByMod.getOrDefault(tabName, new ArrayList<>());

        if (searchField != null) {
            searchField.setText("");
        }

        updateSearch("");
    }

    private void updateSearch(String query) {
        if (malilibList == null) {
            return;
        }

        String q = query.toLowerCase();

        List<MalilibEntry> entries = currentActions.stream()
                .filter(action -> action.name().toLowerCase().contains(q)
                        || action.displayName().toLowerCase().contains(q))
                .map(action -> new MalilibEntry(action, onSelect, this::close))
                .collect(Collectors.toList());

        malilibList.replaceEntries(entries);
        malilibList.setScrollY(0.0);
    }

    private void updateTabButtonStates() {
        for (ButtonWidget button : tabButtons) {
            button.active = !button.getMessage().getString().equals(currentTab);
        }
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {

        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        if (actionsByMod.isEmpty()) {
            graphics.drawCenteredTextWithShadow(
                    textRenderer, Text.translatable("screen.radial.malilib_picker.empty"), width / 2, height / 2, 0xFF555555);

            super.render(graphics, mouseX, mouseY, delta);

            return;
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private static class MalilibList extends AlwaysSelectedEntryListWidget<MalilibEntry> {

        private MalilibList(MinecraftClient minecraft, int width, int height, int y, int itemHeight) {

            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return Math.min(330, getWidth() - 20);
        }
    }

    private static class MalilibEntry extends AlwaysSelectedEntryListWidget.Entry<MalilibEntry> {

        private final MalilibAction action;
        private final Consumer<MalilibAction> onSelect;
        private final Runnable onClose;

        private MalilibEntry(MalilibAction action, Consumer<MalilibAction> onSelect, Runnable onClose) {

            this.action = action;
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

            graphics.drawText(client.textRenderer, action.displayName(), left + 8, textY, 0xFFFFFFFF, true);

            String category = action.category();

            int categoryWidth = client.textRenderer.getWidth(category);

            graphics.drawText(client.textRenderer, category, right - categoryWidth - 8, textY, 0xFFAAAAAA, true);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubleClick) {

            if (click.button() != 0) {
                return false;
            }

            onSelect.accept(action);
            onClose.run();

            return true;
        }

        @Override
        public Text getNarration() {
            return Text.literal(action.displayName());
        }
    }
}
