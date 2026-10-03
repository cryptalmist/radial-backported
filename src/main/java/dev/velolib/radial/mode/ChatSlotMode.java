package dev.velolib.radial.mode;

import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotActionContext;
import dev.velolib.radial.mode.base.IconEnabledSlotMode;
import dev.velolib.radial.ui.screen.SlotEditorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;

public class ChatSlotMode extends IconEnabledSlotMode {

    @Override
    public Text getTranslatedName() {
        return Text.translatable("radial.mode.chat");
    }

    @Override
    public Text getValueHint() {
        return Text.translatable("screen.radial.editor.hint.chat");
    }

    @Override
    public void buildEditorWidgets(
            SlotEditorScreen screen, RadialSlot slot, int width, DirectionalLayoutWidget container) {
        buildValueRow(screen, slot, width, container);
        buildIconRow(screen, slot, width, container);
    }

    @Override
    public void performAction(RadialSlot slot, SlotActionContext context) {
        context.closeScreen(); // Close the radial menu first

        if (slot.value == null || slot.value.isEmpty()) return;

        // Execute the chat message or command
        ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
        if (connection != null) {
            if (slot.value.startsWith("/")) {
                connection.sendChatCommand(slot.value.substring(1)); // Remove the slash for commands
            } else {
                connection.sendChatMessage(slot.value); // Send as normal chat
            }
        }
    }
}
