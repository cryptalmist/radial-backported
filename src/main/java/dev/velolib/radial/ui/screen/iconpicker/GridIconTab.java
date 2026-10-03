package dev.velolib.radial.ui.screen.iconpicker;

import com.mojang.blaze3d.platform.InputConstants;
import dev.velolib.radial.util.IconHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public abstract class GridIconTab<T> implements IconTab {
    private static final int SLOT_SIZE = 20;
    private static final int ROW_HEIGHT = 24;

    private static final int CURRENT_OUTLINE_COLOR = 0xFFFFAA00;
    private static final int CURSOR_OUTLINE_COLOR = 0xFFFFFFFF;
    private static final int FAVORITE_COLOR = 0xFFFFD24A;

    protected final String currentId;
    protected final Consumer<String> onSelect;
    protected final Runnable onClose;

    private IconGridList listWidget;
    private List<T> currentResults = new ArrayList<>();
    private int columns = 1;
    private String query = "";

    // Keyboard cursor into currentResults, or -1 while focus is still on the search bar
    private int cursor = -1;

    public GridIconTab(String currentId, Consumer<String> onSelect, Runnable onClose) {
        this.currentId = currentId;
        this.onSelect = onSelect;
        this.onClose = onClose;
    }

    /**
     * Returns matching icons for a lowercase, trimmed query, best matches first.
     */
    protected abstract List<T> search(String query);

    protected abstract void renderIcon(
            GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY, T item, boolean hovered);

    protected abstract String getIconId(T item);

    protected abstract Component getItemNarration(T item);

    protected Component getEmptyMessage() {
        return Component.translatable("screen.radial.editor.icon_picker.no_results");
    }

    /**
     * Called after an icon is favorited or unfavorited from this tab.
     */
    protected void onFavoritesChanged() {}

    protected final int getSlotSize() {
        return SLOT_SIZE;
    }

    private void selectIcon(T item) {
        onSelect.accept(getIconId(item));
        onClose.run();
    }

    @Override
    public void setup(int width, int height, Consumer<Renderable> addRenderable, Consumer<GuiEventListener> addWidget) {
        int listWidth = Math.min(350, (int) (width * 0.9));
        int top = 65;
        int bottom = height - 40;
        int left = width / 2 - listWidth / 2;

        listWidget = new IconGridList(Minecraft.getInstance(), listWidth, Math.max(1, bottom - top), top, ROW_HEIGHT);

        listWidget.updateSizeAndPosition(listWidth, Math.max(1, bottom - top), left, top);

        addRenderable.accept(listWidget);
        addWidget.accept(listWidget);

        updateSearch("");
        scrollToCurrent();
    }

    private void scrollToCurrent() {
        if (currentId == null) return;

        for (int i = 0; i < currentResults.size(); i++) {
            if (currentId.equals(getIconId(currentResults.get(i)))) {
                listWidget.centerOnRow(i / columns);
                return;
            }
        }
    }

    @Override
    public void updateSearch(String query) {
        this.query = query.trim().toLowerCase();
        currentResults = search(this.query);
        cursor = -1;
        rebuildRows();
    }

    protected final String getQuery() {
        return query;
    }

    /**
     * Re-runs the current search while keeping the scroll position, e.g. after the underlying list changed.
     */
    protected final void refreshResults() {
        double scroll = listWidget != null ? listWidget.scrollAmount() : 0.0;
        updateSearch(query);
        if (listWidget != null) listWidget.setScrollAmount(scroll);
    }

    private void rebuildRows() {
        if (listWidget == null) return;

        int usableWidth = listWidget.getRowWidth();
        columns = Math.max(1, usableWidth / SLOT_SIZE);

        List<IconGridEntry> rows = new ArrayList<>();

        for (int start = 0; start < currentResults.size(); start += columns) {
            int end = Math.min(start + columns, currentResults.size());

            rows.add(new IconGridEntry(start, new ArrayList<>(currentResults.subList(start, end))));
        }

        listWidget.replaceEntries(rows);
        listWidget.setScrollAmount(0.0);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        /* Managed by listWidget */
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false; /* Managed by listWidget */
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (currentResults.isEmpty() || listWidget == null) {
            return false;
        }

        if (event.isConfirmation()) {
            // Without a query or cursor, Enter would just grab whatever happens to be listed first
            if (cursor < 0 && query.isEmpty()) {
                return false;
            }

            selectIcon(currentResults.get(Math.max(cursor, 0)));
            return true;
        }

        int last = currentResults.size() - 1;
        int next;

        if (event.isDown()) {
            next = cursor < 0 ? 0 : Math.min(cursor + columns, last);
        } else if (event.isUp() && cursor >= 0) {
            // Moving up past the first row hands focus back to the search bar
            next = cursor - columns;
        } else if (event.isRight() && cursor >= 0) {
            next = Math.min(cursor + 1, last);
        } else if (event.isLeft() && cursor >= 0) {
            next = Math.max(cursor - 1, 0);
        } else {
            return false;
        }

        cursor = Math.max(next, -1);
        if (cursor >= 0) {
            listWidget.scrollToRow(cursor / columns);
        }
        return true;
    }

    @Override
    public boolean showSearchBar() {
        return true;
    }

    private class IconGridList extends ObjectSelectionList<IconGridEntry> {

        public IconGridList(Minecraft mc, int w, int h, int y, int rowHeight) {
            super(mc, w, h, y, rowHeight);
        }

        @Override
        public int getRowWidth() {
            int availableWidth = Math.min(330, getWidth() - 20);
            int columns = Math.max(1, availableWidth / SLOT_SIZE);

            return columns * SLOT_SIZE;
        }

        @Override
        protected boolean entriesCanBeSelected() {
            // Clicks act on single icons, so a whole-row selection box would be misleading
            return false;
        }

        private void centerOnRow(int row) {
            if (row >= 0 && row < children().size()) {
                centerScrollOn(children().get(row));
            }
        }

        private void scrollToRow(int row) {
            if (row >= 0 && row < children().size()) {
                scrollToEntry(children().get(row));
            }
        }

        @Override
        public void extractWidgetRenderState(
                @NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);

            Font font = Minecraft.getInstance().font;

            if (children().isEmpty()) {
                Component empty = getEmptyMessage();
                graphics.text(font, empty, getX() + (getWidth() - font.width(empty)) / 2, getY() + 12, 0xFFAAAAAA);
            }

            Component hint = Component.translatable("screen.radial.editor.icon_picker.favorite_hint");
            graphics.text(font, hint, getX() + (getWidth() - font.width(hint)) / 2, getBottom() + 3, 0xFF808080);
        }
    }

    private class IconGridEntry extends ObjectSelectionList.Entry<IconGridEntry> {

        private final int startIndex;
        private final List<T> items;

        private IconGridEntry(int startIndex, List<T> items) {
            this.startIndex = startIndex;
            this.items = items;
        }

        @Override
        public void extractContent(
                @NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int left = getContentX();
            int top = getContentY();

            int verticalOffset = Math.max(0, (ROW_HEIGHT - SLOT_SIZE) / 2);

            for (int i = 0; i < items.size(); i++) {
                int x = left + i * SLOT_SIZE;
                int y = top + verticalOffset;
                T item = items.get(i);
                String iconId = getIconId(item);

                boolean slotHovered = mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
                boolean atCursor = startIndex + i == cursor;

                if (slotHovered || atCursor) {
                    graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x40FFFFFF);
                }

                renderIcon(graphics, x, y, mouseX, mouseY, item, slotHovered);

                if (iconId.equals(currentId)) {
                    graphics.outline(x, y, SLOT_SIZE, SLOT_SIZE, CURRENT_OUTLINE_COLOR);
                } else if (atCursor) {
                    graphics.outline(x, y, SLOT_SIZE, SLOT_SIZE, CURSOR_OUTLINE_COLOR);
                }

                if (IconHistory.isFavorite(iconId)) {
                    // A small corner mark, drawn after the icon so items can't cover it
                    graphics.fill(x + SLOT_SIZE - 4, y + 1, x + SLOT_SIZE - 1, y + 4, FAVORITE_COLOR);
                }
            }
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT
                    && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
                return false;
            }

            int verticalOffset = Math.max(0, (ROW_HEIGHT - SLOT_SIZE) / 2);

            for (int i = 0; i < items.size(); i++) {
                int x = getContentX() + i * SLOT_SIZE;
                int y = getContentY() + verticalOffset;

                if (event.x() >= x && event.x() < x + SLOT_SIZE && event.y() >= y && event.y() < y + SLOT_SIZE) {
                    if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                        IconHistory.toggleFavorite(getIconId(items.get(i)));
                        onFavoritesChanged();
                    } else {
                        selectIcon(items.get(i));
                    }
                    return true;
                }
            }

            return false;
        }

        @Override
        public @NonNull Component getNarration() {
            return items.isEmpty()
                    ? Component.translatable("screen.radial.editor.icon_picker.empty_row")
                    : getItemNarration(items.getFirst());
        }
    }
}
