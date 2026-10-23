package dev.velolib.radial.mixin;

import dev.velolib.radial.ui.RadialOverlayScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.4 only: draws a screen's floating overlay (the dropdown menu) after everything else has been
 * submitted, because the GUI is flushed per RenderLayer and a later widget can still be painted over.
 */
@Mixin(Screen.class)
public class ScreenOverlayMixin {

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void radial$renderOverlay(DrawContext graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;

        if (self instanceof RadialOverlayScreen overlay) {
            overlay.renderRadialOverlay(graphics, mouseX, mouseY, delta);
        }
    }
}
