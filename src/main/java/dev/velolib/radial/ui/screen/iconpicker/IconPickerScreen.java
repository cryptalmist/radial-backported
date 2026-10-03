package dev.velolib.radial.ui.screen.iconpicker;

import dev.velolib.radial.ui.screen.iconpicker.tabs.*;
import dev.velolib.radial.util.IconHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class IconPickerScreen extends Screen {
    private final Screen parent;

    private final List<IconTab> tabs = new ArrayList<>();

    private IconTab currentTab;

    private TextFieldWidget searchField;

    /**
     * @param currentId the slot's current icon id, used to open on its tab and highlight it; may be null
     */
    public IconPickerScreen(Screen parent, String currentId, Consumer<String> onSelect) {
        super(Text.translatable("screen.radial.icon_picker.title"));
        this.parent = parent;

        Consumer<String> recordingSelect = id -> {
            IconHistory.recordUse(id);
            onSelect.accept(id);
        };

        // Register Tabs
        RecentIconTab recentTab = new RecentIconTab(currentId, recordingSelect, this::close);
        ItemIconTab itemTab = new ItemIconTab(currentId, recordingSelect, this::close);
        tabs.add(recentTab);
        tabs.add(itemTab);
        tabs.add(new InventoryIconTab(currentId, recordingSelect, this::close));
        tabs.add(new EffectIconTab(currentId, recordingSelect, this::close));
        tabs.add(new PhosphorIconTab(currentId, recordingSelect, this::close));
        tabs.add(new GlyphIconTab(currentId, recordingSelect, this::close));

        this.currentTab = getInitialTab(currentId, recentTab, itemTab);
    }

    private IconTab getInitialTab(String currentId, IconTab recentTab, IconTab itemTab) {
        boolean hasIcon = currentId != null && !currentId.isBlank() && !currentId.equals("minecraft:air");

        if (hasIcon) {
            for (IconTab tab : tabs) {
                if (tab.accepts(currentId)) {
                    return tab;
                }
            }
        }

        return RecentIconTab.hasEntries() ? recentTab : itemTab;
    }

    @Override
    protected void init() {
        clearChildren();

        int tabWidth = Math.min(80, width / Math.max(1, tabs.size()));
        int xOffset = (width - tabWidth * tabs.size()) / 2;

        for (IconTab tab : tabs) {
            ButtonWidget button = ButtonWidget.builder(tab.getTitle(), unused -> setTab(tab))
                    .dimensions(xOffset, 10, tabWidth, 20)
                    .build();

            button.active = (tab != currentTab);
            addDrawableChild(button);

            xOffset += tabWidth;
        }

        int listWidth = Math.min(350, (int) (width * 0.9));
        searchField = new TextFieldWidget(
                textRenderer,
                width / 2 - listWidth / 2,
                35,
                listWidth,
                20,
                Text.translatable("screen.radial.editor.search"));
        searchField.setPlaceholder(Text.translatable("screen.radial.editor.search"));
        searchField.setChangedListener(query -> {
            if (currentTab != null) currentTab.updateSearch(query);
        });

        addDrawableChild(searchField);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), unused -> close())
                .dimensions(width / 2 - 100, height - 28, 200, 20)
                .build());

        currentTab.setup(width, height, renderable -> addDrawableChild((ClickableWidget) renderable), listener -> {
            if (!this.children().contains(listener)) {
                addSelectableChild((ClickableWidget) listener);
            }
        });

        // The field is recreated on every init, so the query already starts empty; calling setText here would
        // re-run the search and throw away the tab's scroll to the current icon
        searchField.visible = currentTab.showSearchBar();
        setInitialFocus(searchField);
    }

    private void setTab(IconTab tab) {
        this.currentTab = tab;
        this.clearAndInit(); // Triggers init() again to cleanly swap widgets
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        // Let the tab render its background/custom UI
        currentTab.render(graphics, mouseX, mouseY, delta);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        // Ctrl+Tab / Ctrl+Shift+Tab cycle through the tabs
        if (event.isTab() && event.hasCtrl()) {
            int step = event.hasShift() ? -1 : 1;
            setTab(tabs.get(Math.floorMod(tabs.indexOf(currentTab) + step, tabs.size())));
            return true;
        }

        // The tab gets first pick so arrows and Enter drive the icon grid even while typing a search
        if (currentTab.keyPressed(event)) {
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (currentTab.mouseClicked(click, doubled)) {
            return true;
        }

        boolean handled = super.mouseClicked(click, doubled);

        // Right-clicking to favorite focuses the icon list; hand focus back so typing keeps searching
        if (click.button() == 1 && searchField != null && searchField.visible) {
            setFocused(searchField);
        }

        return handled;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
