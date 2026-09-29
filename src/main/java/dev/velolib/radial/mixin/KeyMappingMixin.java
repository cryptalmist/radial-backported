package dev.velolib.radial.mixin;

import dev.velolib.radial.RadialClient;
import dev.velolib.radial.ui.screen.RadialScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void allowRadialMovement(CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().gui.screen() instanceof RadialScreen) {
            KeyMapping self = (KeyMapping) (Object) this;

            if (self.getCategory().equals(KeyMapping.Category.MOVEMENT)) {
                cir.setReturnValue(RadialClient.isPhysicallyDown(KeyMappingHelper.getBoundKeyOf(self)));
            }
        }
    }
}
