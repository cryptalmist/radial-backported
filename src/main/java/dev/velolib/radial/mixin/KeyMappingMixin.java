package dev.velolib.radial.mixin;

import dev.velolib.radial.RadialClient;
import dev.velolib.radial.ui.screen.RadialScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public class KeyMappingMixin {

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void allowRadialMovement(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.currentScreen instanceof RadialScreen) {

            KeyBinding self = (KeyBinding) (Object) this;

            if (self.getCategory().equals(KeyBinding.Category.MOVEMENT)) {
                cir.setReturnValue(RadialClient.isPhysicallyDown(KeyBindingHelper.getBoundKeyOf(self)));
            }
        }
    }
}
