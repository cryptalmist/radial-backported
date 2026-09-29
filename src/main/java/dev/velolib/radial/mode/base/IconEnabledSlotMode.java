package dev.velolib.radial.mode.base;

import dev.velolib.radial.RadialClient;
import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotMode;
import dev.velolib.radial.render.SlotRenderHelper;
import dev.velolib.radial.ui.screen.iconpicker.IconPickerScreen;
import dev.velolib.radial.util.EncoderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public abstract class IconEnabledSlotMode implements SlotMode {

    private static final int HORIZ_GAP = 5;
    private static final int BUTTON_WIDTH = 55;
    private static final int ROW_HEIGHT = 20;
    private static final int INVALID_TEXT_COLOR = 0xFFFF5555;

    /**
     * Helper to build the "Action Value" label and text field, plus a Select button
     * when this mode {@link #hasValuePicker() has a value picker}.
     */
    protected void buildValueRow(Screen screen, RadialSlot slot, int width, LinearLayout container) {
        boolean hasPicker = this.hasValuePicker();
        int valueFieldWidth = hasPicker ? width - BUTTON_WIDTH - HORIZ_GAP : width;

        // Group the label and row together vertically
        LinearLayout valueGroup = LinearLayout.vertical().spacing(2);

        StringWidget label =
                new StringWidget(Component.translatable("screen.radial.editor.value"), Minecraft.getInstance().font);
        valueGroup.addChild(label);

        // Horizontal row for the field + picker button
        LinearLayout inputRow = LinearLayout.horizontal().spacing(HORIZ_GAP);

        EditBox valueField = new EditBox(
                Minecraft.getInstance().font,
                0,
                0,
                valueFieldWidth,
                ROW_HEIGHT,
                Component.translatable("screen.radial.editor.value"));
        valueField.setMaxLength(Integer.MAX_VALUE);
        valueField.setValue(slot.value != null ? slot.value : "");
        valueField.setHint(this.getValueHint());
        valueField.setResponder(v -> slot.value = v);
        inputRow.addChild(valueField);

        if (hasPicker) {
            Button valueBrowseButton = Button.builder(
                            Component.translatable("screen.radial.editor.select"),
                            _ -> openValuePicker(screen, id -> {
                                valueField.setValue(id);
                                slot.value = id;
                            }))
                    .bounds(0, 0, BUTTON_WIDTH, ROW_HEIGHT)
                    .build();
            inputRow.addChild(valueBrowseButton);
        }

        valueGroup.addChild(inputRow);
        container.addChild(valueGroup);
    }

    /**
     * Helper to build the 3 icon widgets (Text Field, Browse Button, Hand Button)
     * using modern layout managers.
     */
    protected void buildIconRow(Screen screen, RadialSlot slot, int width, LinearLayout container) {
        int iconFieldWidth = width - (BUTTON_WIDTH * 2) - (HORIZ_GAP * 2);

        // Group the label and the row of inputs together
        LinearLayout iconGroup = LinearLayout.vertical().spacing(2);

        // 1. Label
        StringWidget label =
                new StringWidget(Component.translatable("screen.radial.editor.icon"), Minecraft.getInstance().font);
        iconGroup.addChild(label);

        // 2. Horizontal row for Field + Buttons
        LinearLayout inputRow = LinearLayout.horizontal().spacing(HORIZ_GAP);

        // Built first so the icon field's responder can enable it for tintable icons
        EditBox colorField = buildIconColorField(slot, width);

        // Icon EditBox
        EditBox iconField = new EditBox(
                Minecraft.getInstance().font,
                0,
                0,
                iconFieldWidth,
                ROW_HEIGHT,
                Component.translatable("screen.radial.editor.icon"));
        iconField.setMaxLength(Integer.MAX_VALUE);
        iconField.setValue(slot.itemId != null ? slot.itemId : "minecraft:air");
        iconField.setResponder(v -> {
            slot.itemId = v;
            slot.clearCache();
            updateIconFieldState(iconField, colorField, v);
        });
        updateIconFieldState(iconField, colorField, iconField.getValue());
        inputRow.addChild(iconField);

        // Browse Button (setValue runs the responder, which updates the slot)
        Button browseIconButton = Button.builder(
                        Component.translatable("screen.radial.editor.browse"), _ -> Minecraft.getInstance()
                                .gui
                                .setScreen(new IconPickerScreen(screen, slot.itemId, iconField::setValue)))
                .bounds(0, 0, BUTTON_WIDTH, ROW_HEIGHT)
                .build();
        inputRow.addChild(browseIconButton);

        // Hand Button
        Button handButton = Button.builder(Component.translatable("screen.radial.editor.hand"), _ -> {
                    Minecraft client = Minecraft.getInstance();
                    if (client.player != null && client.level != null) {
                        iconField.setValue(getHandItemId(client.player.getMainHandItem(), client));
                    }
                })
                .bounds(0, 0, BUTTON_WIDTH, ROW_HEIGHT)
                .build();
        inputRow.addChild(handButton);

        // Add the horizontal row into the vertical group, then add the group to the main container
        iconGroup.addChild(inputRow);
        container.addChild(iconGroup);

        // 3. Tint color for text-based icons
        LinearLayout colorGroup = LinearLayout.vertical().spacing(2);
        colorGroup.addChild(new StringWidget(
                Component.translatable("screen.radial.editor.icon_color"), Minecraft.getInstance().font));
        colorGroup.addChild(colorField);
        container.addChild(colorGroup);
    }

    private static EditBox buildIconColorField(RadialSlot slot, int width) {
        EditBox colorField = new EditBox(
                Minecraft.getInstance().font,
                0,
                0,
                width,
                ROW_HEIGHT,
                Component.translatable("screen.radial.editor.icon_color"));
        colorField.setMaxLength(7);
        colorField.setValue(slot.iconColor != null ? slot.iconColor : "");
        colorField.setHint(Component.translatable("screen.radial.editor.icon_color.hint"));
        colorField.setResponder(v -> {
            Integer color = RadialSlot.parseIconColor(v);
            boolean valid = v.isBlank() || color != null;
            colorField.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : INVALID_TEXT_COLOR);

            // Invalid input leaves the last valid color in place until it's fixed
            if (valid) {
                slot.iconColor = color != null ? RadialSlot.formatIconColor(color) : null;
                slot.clearCache();
            }
        });

        return colorField;
    }

    private static void updateIconFieldState(EditBox iconField, EditBox colorField, String iconId) {
        boolean valid = SlotRenderHelper.isValidIconId(iconId);
        iconField.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : INVALID_TEXT_COLOR);
        iconField.setTooltip(
                valid ? null : Tooltip.create(Component.translatable("screen.radial.editor.icon.invalid")));

        boolean tintable = SlotRenderHelper.isTintable(iconId);
        colorField.active = tintable;
        colorField.setEditable(tintable);
        colorField.setTooltip(
                tintable ? null : Tooltip.create(Component.translatable("screen.radial.editor.icon_color.disabled")));
    }

    private static String getHandItemId(ItemStack stack, Minecraft client) {
        if (stack.isEmpty()) {
            return "minecraft:air";
        }

        try {
            return EncoderUtils.toGiveCommandString(stack, client.level.registryAccess());
        } catch (Exception e) {
            // Some component data can't be written back out; the plain item is still a usable icon
            RadialClient.LOGGER.warn("Failed to encode held item components, using the plain item id", e);
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
    }
}
