package dev.velolib.radial.ui.screen.iconpicker;

import com.mojang.blaze3d.platform.InputConstants;
import dev.velolib.radial.util.IconHistory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

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
            GuiGraphics graphics, int x, int y, int mouseX, int mouseY, T item, boolean hovered);

    protected abstract String getIconId(T item);

    protected abstract Component getItemNarration(T item);

    protected Component getEmptyMessage() {
        return Component.translatable("screen.radial.editor.icon_picker.no_results");
    }

    /**
     * Called after an icon is favorited or unfavorited from this tab.
     */
    protected void onFavoritesChanged() {}

    protected int getSlotSize() {
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

        listWidget.updateSizeAndPosition(listWidth, Math.max(1, bottom - top), top);
        listWidget.setX(left);

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
        double scroll = listWidget != null ? listWidget.getScrollAmount() : 0.0;
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

        listWidget.setEntries(rows);
        listWidget.setScrollAmount(0.0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        /* Managed by listWidget */
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false; /* Managed by listWidget */
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (currentResults.isEmpty() || listWidget == null) {
            return false;
        }

        boolean confirmation =
                keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER;
        if (confirmation) {
            // Without a query or cursor, Enter would just grab whatever happens to be listed first
            if (cursor < 0 && query.isEmpty()) {
                return false;
            }

            selectIcon(currentResults.get(Math.max(cursor, 0)));
            return true;
        }

        int last = currentResults.size() - 1;
        int next;

        boolean down = keyCode == InputConstants.KEY_DOWN;
        boolean up = keyCode == InputConstants.KEY_UP;
        boolean right = keyCode == InputConstants.KEY_RIGHT;
        boolean left = keyCode == InputConstants.KEY_LEFT;

        if (down) {
            next = cursor < 0 ? 0 : Math.min(cursor + columns, last);
        } else if (up && cursor >= 0) {
            // Moving up past the first row hands focus back to the search bar
            next = cursor - columns;
        } else if (right && cursor >= 0) {
            next = Math.min(cursor + 1, last);
        } else if (left && cursor >= 0) {
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

    private class IconGridList extends ContainerObjectSelectionList<IconGridEntry> {

        public IconGridList(Minecraft mc, int w, int h, int y, int rowHeight) {
            super(mc, w, h, y, rowHeight);
        }

        public void setEntries(Collection<IconGridEntry> entries) {
            replaceEntries(entries);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}

        @Override
        public int getRowWidth() {
            int availableWidth = Math.min(330, getWidth() - 20);
            int gridColumns = Math.max(1, availableWidth / SLOT_SIZE);

            return gridColumns * SLOT_SIZE;
        }

        private void centerOnRow(int row) {
            if (row >= 0 && row < children().size()) {
                centerScrollOn(children().get(row));
            }
        }

        private void scrollToRow(int row) {
            if (row >= 0 && row < children().size()) {
                ensureVisible(children().get(row));
            }
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.renderWidget(graphics, mouseX, mouseY, delta);

            Font font = Minecraft.getInstance().font;

            if (children().isEmpty()) {
                Component empty = getEmptyMessage();
                graphics.drawString(font, empty, getX() + (getWidth() - font.width(empty)) / 2, getY() + 12, 0xFFAAAAAA);
            }

            Component hint = Component.translatable("screen.radial.editor.icon_picker.favorite_hint");
            graphics.drawString(font, hint, getX() + (getWidth() - font.width(hint)) / 2, getBottom() + 3, 0xFF808080);
        }
    }

    private class IconGridEntry extends ContainerObjectSelectionList.Entry<IconGridEntry> {

        private final int startIndex;
        private final List<T> items;
        private int lastLeft;
        private int lastTop;

        private IconGridEntry(int startIndex, List<T> items) {
            this.startIndex = startIndex;
            this.items = items;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int index,
                int top,
                int left,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovered,
                float delta) {
            this.lastLeft = left;
            this.lastTop = top;

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
                    graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, CURRENT_OUTLINE_COLOR);
                } else if (atCursor) {
                    graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, CURSOR_OUTLINE_COLOR);
                }

                if (IconHistory.isFavorite(iconId)) {
                    // A small corner mark, drawn after the icon so items can't cover it
                    graphics.fill(x + SLOT_SIZE - 4, y + 1, x + SLOT_SIZE - 1, y + 4, FAVORITE_COLOR);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) {
                return false;
            }

            int verticalOffset = Math.max(0, (ROW_HEIGHT - SLOT_SIZE) / 2);

            for (int i = 0; i < items.size(); i++) {
                int x = lastLeft + i * SLOT_SIZE;
                int y = lastTop + verticalOffset;

                if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
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

        public Component getNarration() {
            return items.isEmpty()
                    ? Component.translatable("screen.radial.editor.icon_picker.empty_row")
                    : getItemNarration(items.getFirst());
        }

        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }

        public List<? extends GuiEventListener> children() {
            return List.of();
        }
    }
}
