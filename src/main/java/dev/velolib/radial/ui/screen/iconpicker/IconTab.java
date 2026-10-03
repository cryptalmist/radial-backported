package dev.velolib.radial.ui.screen.iconpicker;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public interface IconTab {
    Component getTitle();

    // Allows the tab to register its own list/widgets to the main screen
    void setup(int width, int height, Consumer<Renderable> addRenderable, Consumer<GuiEventListener> addWidget);

    // Render custom backgrounds or overlays (like the inventory tab does)
    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);

    boolean mouseClicked(double mouseX, double mouseY, int button);

    void updateSearch(String query);

    boolean showSearchBar();

    // Whether this tab offers the given icon id, so the picker can open on the tab holding the current icon
    default boolean accepts(String iconId) {
        return false;
    }

    // Lets the tab handle keyboard navigation before the screen's own focus handling
    default boolean keyPressed(KeyEvent event) {
        return false;
    }
}
