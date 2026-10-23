package dev.velolib.radial.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Implemented by screens that draw a floating element (the slot-mode dropdown) on top of everything else.
 * <p>
 * On 1.21.4 the GUI is batched per {@code RenderLayer} and flushed asynchronously, so a widget rendered
 * late inside {@code render()} can still end up underneath content drawn earlier. {@code Screen#renderWithTooltip}
 * is the outermost entry point, so a mixin calls this at its TAIL - after the screen, its widgets and its
 * tooltips are all submitted - which is the only place in 1.21.4 where a panel is guaranteed to stay on top.
 */
public interface RadialOverlayScreen {

    void renderRadialOverlay(DrawContext graphics, int mouseX, int mouseY, float delta);
}
