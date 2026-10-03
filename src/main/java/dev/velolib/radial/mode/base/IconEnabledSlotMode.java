package dev.velolib.radial.mode.base;

import dev.velolib.radial.RadialClient;
import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotMode;
import dev.velolib.radial.render.SlotRenderHelper;
import dev.velolib.radial.ui.screen.SlotEditorScreen;
import dev.velolib.radial.ui.screen.iconpicker.IconPickerScreen;
import dev.velolib.radial.util.EncoderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

public abstract class IconEnabledSlotMode implements SlotMode {

    private static final int HORIZ_GAP = 5;
    private static final int BUTTON_WIDTH = 55;
    private static final int ROW_HEIGHT = 20;
    private static final int INVALID_TEXT_COLOR = 0xFFFF5555;

    /**
     * Helper to build the "Action Value" label and text field, plus a Select button
     * when this mode {@link #hasValuePicker() has a value picker}.
     */
    protected void buildValueRow(
            SlotEditorScreen screen, RadialSlot slot, int width, DirectionalLayoutWidget container) {
        boolean hasPicker = this.hasValuePicker();
        int valueFieldWidth = hasPicker ? width - BUTTON_WIDTH - HORIZ_GAP : width;

        // Group the label and row together vertically
        DirectionalLayoutWidget valueGroup = DirectionalLayoutWidget.vertical().spacing(2);

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        TextWidget label = new TextWidget(Text.translatable("screen.radial.editor.value"), textRenderer);
        valueGroup.add(label);

        // Horizontal row for the field + picker button
        DirectionalLayoutWidget inputRow = DirectionalLayoutWidget.horizontal().spacing(HORIZ_GAP);

        TextFieldWidget valueField = new TextFieldWidget(
                textRenderer, 0, 0, valueFieldWidth, ROW_HEIGHT, Text.translatable("screen.radial.editor.value"));
        valueField.setMaxLength(Integer.MAX_VALUE);
        valueField.setText(slot.value != null ? slot.value : "");
        valueField.setPlaceholder(this.getValueHint());
        valueField.setChangedListener(v -> slot.value = v);
        inputRow.add(valueField);

        if (hasPicker) {
            ButtonWidget valueBrowseButton = ButtonWidget.builder(
                            Text.translatable("screen.radial.editor.select"),
                            unused -> openValuePicker(screen, id -> {
                                valueField.setText(id);
                                slot.value = id;
                            }))
                    .dimensions(0, 0, BUTTON_WIDTH, ROW_HEIGHT)
                    .build();
            inputRow.add(valueBrowseButton);
        }

        valueGroup.add(inputRow);
        container.add(valueGroup);
    }

    /**
     * Helper to build the 3 icon widgets (Text Field, Browse Button, Hand Button)
     * using modern layout managers.
     */
    protected void buildIconRow(
            SlotEditorScreen screen, RadialSlot slot, int width, DirectionalLayoutWidget container) {
        int iconFieldWidth = width - (BUTTON_WIDTH * 2) - (HORIZ_GAP * 2);

        // Group the label and the row of inputs together
        DirectionalLayoutWidget iconGroup = DirectionalLayoutWidget.vertical().spacing(2);

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        // 1. Label
        TextWidget label = new TextWidget(Text.translatable("screen.radial.editor.icon"), textRenderer);
        iconGroup.add(label);

        // 2. Horizontal row for Field + Buttons
        DirectionalLayoutWidget inputRow = DirectionalLayoutWidget.horizontal().spacing(HORIZ_GAP);

        // Built first so the icon field's responder can enable it for tintable icons
        TextFieldWidget colorField = buildIconColorField(slot, width);

        // Icon TextField
        TextFieldWidget iconField = new TextFieldWidget(
                textRenderer, 0, 0, iconFieldWidth, ROW_HEIGHT, Text.translatable("screen.radial.editor.icon"));
        iconField.setMaxLength(Integer.MAX_VALUE);
        iconField.setText(slot.itemId != null ? slot.itemId : "minecraft:stone");
        iconField.setChangedListener(v -> {
            slot.itemId = v;
            slot.clearCache();
            updateIconFieldState(iconField, colorField, v);
        });
        updateIconFieldState(iconField, colorField, iconField.getText());
        inputRow.add(iconField);

        // Browse Button (setText runs the responder, which updates the slot)
        ButtonWidget browseIconButton = ButtonWidget.builder(
                        Text.translatable("screen.radial.editor.browse"),
                        unused -> MinecraftClient.getInstance().setScreen(new IconPickerScreen(screen, slot.itemId, id -> {
                            iconField.setText(id);
                            slot.itemId = id;
                            slot.clearCache();
                            updateIconFieldState(iconField, colorField, id);
                        })))
                .dimensions(0, 0, BUTTON_WIDTH, ROW_HEIGHT)
                .build();
        inputRow.add(browseIconButton);

        // Hand Button
        ButtonWidget handButton = ButtonWidget.builder(Text.translatable("screen.radial.editor.hand"), unused -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null && client.world != null) {
                        String id = getHandItemId(client.player.getMainHandStack(), client);
                        iconField.setText(id);
                        slot.itemId = id;
                        slot.clearCache();
                        updateIconFieldState(iconField, colorField, id);
                    }
                })
                .dimensions(0, 0, BUTTON_WIDTH, ROW_HEIGHT)
                .build();
        inputRow.add(handButton);

        // Add the horizontal row into the vertical group, then add the group to the main container
        iconGroup.add(inputRow);
        container.add(iconGroup);

        // 3. Tint color for text-based icons
        DirectionalLayoutWidget colorGroup = DirectionalLayoutWidget.vertical().spacing(2);
        colorGroup.add(
                new TextWidget(Text.translatable("screen.radial.editor.icon_color"), textRenderer));
        colorGroup.add(colorField);
        container.add(colorGroup);
    }

    private static TextFieldWidget buildIconColorField(RadialSlot slot, int width) {
        TextFieldWidget colorField = new TextFieldWidget(
                MinecraftClient.getInstance().textRenderer,
                0,
                0,
                width,
                ROW_HEIGHT,
                Text.translatable("screen.radial.editor.icon_color"));
        colorField.setMaxLength(7);
        colorField.setText(slot.iconColor != null ? slot.iconColor : "");
        colorField.setPlaceholder(Text.translatable("screen.radial.editor.icon_color.hint"));
        colorField.setChangedListener(v -> {
            Integer color = RadialSlot.parseIconColor(v);
            boolean valid = v.isBlank() || color != null;
            colorField.setEditableColor(
                    valid ? TextFieldWidget.DEFAULT_EDITABLE_COLOR : INVALID_TEXT_COLOR);

            // Invalid input leaves the last valid color in place until it's fixed
            if (valid) {
                slot.iconColor = color != null ? RadialSlot.formatIconColor(color) : null;
                slot.clearCache();
            }
        });

        return colorField;
    }

    private static void updateIconFieldState(
            TextFieldWidget iconField, TextFieldWidget colorField, String iconId) {
        boolean valid = SlotRenderHelper.isValidIconId(iconId);
        iconField.setEditableColor(valid ? TextFieldWidget.DEFAULT_EDITABLE_COLOR : INVALID_TEXT_COLOR);
        iconField.setTooltip(
                valid ? null : Tooltip.of(Text.translatable("screen.radial.editor.icon.invalid")));

        boolean tintable = SlotRenderHelper.isTintable(iconId);
        colorField.active = tintable;
        colorField.setEditable(tintable);
        colorField.setTooltip(
                tintable ? null : Tooltip.of(Text.translatable("screen.radial.editor.icon_color.disabled")));
    }

    private static String getHandItemId(ItemStack stack, MinecraftClient client) {
        if (stack.isEmpty()) {
            return "minecraft:air";
        }

        try {
            return EncoderUtils.toGiveCommandString(stack, client.world.getRegistryManager());
        } catch (Exception e) {
            // Some component data can't be written back out; the plain item is still a usable icon
            RadialClient.LOGGER.warn("Failed to encode held item components, using the plain item id", e);
            return Registries.ITEM.getId(stack.getItem()).toString();
        }
    }
}
