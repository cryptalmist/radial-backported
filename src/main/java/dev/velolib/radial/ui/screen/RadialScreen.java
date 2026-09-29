package dev.velolib.radial.ui.screen;

import dev.velolib.radial.RadialClient;
import dev.velolib.radial.api.RadialSlot;
import dev.velolib.radial.api.SlotActionContext;
import dev.velolib.radial.config.RadialConfig;
import dev.velolib.radial.render.DonutRenderer;
import dev.velolib.radial.render.SlotRenderHelper;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

public class RadialScreen extends Screen {

    // How far a hovered slot moves outward, and how much it grows
    public static final float SLOT_PUSH = 7.5F;
    public static final float SLOT_HOVER_SCALE = 0.1F;

    private static final int SLOT_SIZE = 26;
    private static final int ITEM_SIZE = 16;
    private static final float STAGGER_STEP_FRACTION = 0.4F;

    // The maximum possible renderable slots (12 config slots + 1 submenu back button)
    private static final int MAX_RENDER_SLOTS = 13;

    private static final DonutRenderer SECTOR_RENDERER = new DonutRenderer("main");

    private static final ItemStack BACK_ICON = new ItemStack(Items.ARROW);
    private static final ItemStack MISSING_ICON = new ItemStack(Items.BARRIER);

    private record MenuState(List<RadialSlot> slots, int slotCount) {}

    private final Deque<MenuState> history = new ArrayDeque<>();

    private final List<RadialSlot> rootSlots;
    private final float[] pushAnim;

    private List<RadialSlot> activeSlots;
    private int currentSlotCount;
    private int hoveredSlot = -1;

    private double revealElapsedSeconds = 0.0;
    private double menuElapsedSeconds = 0.0;
    private long lastNano;

    public RadialScreen() {
        super(Component.empty());
        this.rootSlots = RadialConfig.INSTANCE.slots;
        this.activeSlots = rootSlots;
        this.currentSlotCount = RadialConfig.INSTANCE.slotCount;
        this.pushAnim = new float[MAX_RENDER_SLOTS];
    }

    public static void prepareRenderer() {
        RadialConfig config = RadialConfig.INSTANCE;
        SECTOR_RENDERER.prepare(config.slotCount, config.getVisibleInnerRadius(), config.getVisibleOuterRadius(), 2.0F);
    }

    @Override
    protected void init() {
        prepareSectorRenderer();

        RadialConfig.ActivationMode mode = RadialConfig.INSTANCE.activationMode;
        if (mode == RadialConfig.ActivationMode.SCROLL_CLICK || mode == RadialConfig.ActivationMode.SCROLL_RELEASE) {
            hoveredSlot = 0; // Default to first slot in scroll mode
            GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        }
    }

    @Override
    public void removed() {
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        super.removed();
    }

    @Override
    public void tick() {
        prepareSectorRenderer(); // Cheap cache check
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- State Helpers ---

    private boolean isSubmenu() {
        return !history.isEmpty();
    }

    private int getRenderCount() {
        return isSubmenu() ? currentSlotCount + 1 : currentSlotCount;
    }

    /**
     * Maps the visual hovered slot index to the actual RadialSlot index in the active list.
     * Returns null if the slot is the "Back" button or out of bounds.
     */
    private RadialSlot getTargetSlot(int index) {
        if (index == -1 || (isSubmenu() && index == 0)) {
            return null;
        }
        int targetIndex = isSubmenu() ? index - 1 : index;
        if (targetIndex >= 0 && targetIndex < activeSlots.size()) {
            return activeSlots.get(targetIndex);
        }
        return null;
    }

    // --- Renderer Preparation & Geometry ---

    private void prepareSectorRenderer() {
        RadialConfig config = RadialConfig.INSTANCE;
        SECTOR_RENDERER.prepare(getRenderCount(), config.getVisibleInnerRadius(), config.getVisibleOuterRadius(), 2.0F);
    }

    // --- Render Loop ---

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        RadialConfig config = RadialConfig.INSTANCE;
        RadialConfig.ActivationMode mode = config.activationMode;
        boolean isScrollMode =
                mode == RadialConfig.ActivationMode.SCROLL_CLICK || mode == RadialConfig.ActivationMode.SCROLL_RELEASE;

        // 1. Check Key Release
        if (!RadialClient.isPhysicallyDown(KeyMappingHelper.getBoundKeyOf(RadialClient.OPEN_RADIAL))) {
            if (mode == RadialConfig.ActivationMode.RELEASE || mode == RadialConfig.ActivationMode.SCROLL_RELEASE) {
                if (hoveredSlot != -1) {
                    if (isSubmenu() && hoveredSlot == 0) {
                        onClose();
                        return;
                    }
                    RadialSlot slot = getTargetSlot(hoveredSlot);
                    if (slot != null && slot.mode.activateOnRelease()) {
                        performAction(slot);
                        return;
                    }
                }
            }
            onClose();
            return;
        }

        // 2. Timing
        long now = System.nanoTime();
        if (lastNano == 0) lastNano = now;
        float dt = (float) Math.min((now - lastNano) / 1.0e9, 0.1);
        lastNano = now;

        revealElapsedSeconds += dt;
        menuElapsedSeconds += dt;

        int cx = width / 2;
        int cy = height / 2;
        int renderCount = getRenderCount();

        // 3. Hit Test (Mouse mode only)
        if (!isScrollMode) {
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist >= config.getDetectionInnerRadius() && dist <= config.getDetectionOuterRadius()) {
                double angle = Math.atan2(dy, dx);
                if (angle < 0.0) angle += Math.PI * 2.0;

                double sectorSize = Math.PI * 2.0 / renderCount;
                double shiftedAngle = (angle + Math.PI / 2.0 + sectorSize / 2.0) % (Math.PI * 2.0);
                hoveredSlot = (int) (shiftedAngle / sectorSize) % renderCount;
            } else {
                hoveredSlot = -1;
            }
        }

        // 4. Hover Animations
        float hoverDuration = config.hoverAnimationDurationMs / 1000.0F;
        if (!config.enableHoverAnimation || hoverDuration <= 0.0F) {
            for (int i = 0; i < renderCount; i++) {
                pushAnim[i] = (i == hoveredSlot) ? 1.0F : 0.0F;
            }
        } else {
            float step = Mth.clamp(dt / hoverDuration, 0.0F, 1.0F);
            for (int i = 0; i < renderCount; i++) {
                float target = (i == hoveredSlot) ? 1.0F : 0.0F;
                if (pushAnim[i] < target) pushAnim[i] = Math.min(target, pushAnim[i] + step);
                else if (pushAnim[i] > target) pushAnim[i] = Math.max(target, pushAnim[i] - step);
            }
        }

        // 5. Render Sectors (Background Ring)
        if (config.showActivationZone) {
            for (int i = 0; i < renderCount; i++) {
                // Apply the easing curve to the background sectors
                float smoothedHover = easeOutCubic(pushAnim[i]);
                float hoverPush = config.enableHoverAnimation ? SLOT_PUSH * smoothedHover : 0.0F;

                float slotAngle = (float) ((Math.PI * 2.0 / renderCount) * i - Math.PI / 2.0);
                float revealEase = easeOutQuint(getRevealProgress(i, renderCount, config));

                SECTOR_RENDERER.renderSector(
                        graphics, cx, cy, slotAngle, hoverPush, (i == hoveredSlot), revealEase, 2.0F);
            }
        }

        // 6. Render Slots & Icons
        for (int i = 0; i < renderCount; i++) {
            float revealProgress = getRevealProgress(i, renderCount, config);
            if (revealProgress <= 0.0F) continue;

            float revealEase = easeOutQuint(revealProgress);
            int revealAlpha = Mth.clamp((int) (revealEase * 255.0F + 0.5F), 0, 255);
            if (revealAlpha <= 0) continue;

            float smoothedHover = easeOutCubic(pushAnim[i]);
            float slotAngle = (float) ((Math.PI * 2.0 / renderCount) * i - Math.PI / 2.0);
            float hoverPush = config.enableHoverAnimation ? SLOT_PUSH * smoothedHover : 0.0F;
            float finalRadius = (config.slotRadius * revealEase) + (hoverPush * revealEase);

            float slotX = (float) (cx + Math.cos(slotAngle) * finalRadius);
            float slotY = (float) (cy + Math.sin(slotAngle) * finalRadius);

            float scale = revealEase * (config.enableHoverAnimation ? 1.0F + SLOT_HOVER_SCALE * smoothedHover : 1.0F);

            graphics.pose().pushMatrix();
            graphics.pose().translate(slotX, slotY);
            graphics.pose().scale(scale, scale);

            int color = (revealAlpha << 24) | 0xFFFFFF;
            int drawOffset = -SLOT_SIZE / 2;

            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    SlotRenderHelper.SLOT_TEXTURE,
                    drawOffset,
                    drawOffset,
                    SLOT_SIZE,
                    SLOT_SIZE,
                    color);
            if (i == hoveredSlot) {
                graphics.blitSprite(
                        RenderPipelines.GUI_TEXTURED,
                        SlotRenderHelper.SELECTION_TEXTURE,
                        drawOffset,
                        drawOffset,
                        SLOT_SIZE,
                        SLOT_SIZE,
                        color);
            }

            if (isSubmenu() && i == 0) {
                graphics.item(BACK_ICON, -ITEM_SIZE / 2, -ITEM_SIZE / 2);
            } else {
                RadialSlot slot = getTargetSlot(i);
                if (slot != null) {
                    SlotRenderHelper.renderSlotIcon(graphics, slot, drawOffset, drawOffset, revealAlpha);
                } else {
                    graphics.item(MISSING_ICON, -ITEM_SIZE / 2, -ITEM_SIZE / 2);
                }
            }

            graphics.pose().popMatrix();
        }

        // 7. Render Center Label
        if (hoveredSlot != -1) {
            String name;
            if (isSubmenu() && hoveredSlot == 0) {
                name = Component.translatable("radial.ui.back").getString();
            } else {
                RadialSlot hovered = getTargetSlot(hoveredSlot);
                name = hovered != null ? hovered.name : "";
            }

            if (!name.isEmpty()) {
                int alpha = Mth.clamp((int) (easeOutQuint(getGlobalRevealProgress(config)) * 255.0F + 0.5F), 0, 255);
                graphics.text(
                        font, Component.nullToEmpty(name), cx - font.width(name) / 2, cy - 4, (alpha << 24) | 0xFFFFFF);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    // --- Math & Animations ---

    private int getStaggerIndex(int index, int count, RadialConfig.RevealAnimation animation) {
        if (count <= 1) return 0;
        return switch (animation) {
            case STAGGERED_CLOCKWISE -> index;
            case STAGGERED_COUNTERCLOCKWISE -> (count - index) % count;
            case STAGGERED_BOTH -> Math.min(index, count - index);
            default -> index;
        };
    }

    private float getRevealProgress(int index, int count, RadialConfig config) {
        if (config.revealDurationMs <= 0) return 1.0F;

        float totalDuration = config.revealDurationMs / 1000.0F;
        RadialConfig.RevealAnimation animation = config.revealAnimation;

        if (animation == RadialConfig.RevealAnimation.ZOOM || count <= 1) {
            return Mth.clamp((float) (menuElapsedSeconds / totalDuration), 0.0F, 1.0F);
        }

        int staggerCount = (animation == RadialConfig.RevealAnimation.STAGGERED_BOTH) ? (count / 2) + 1 : count;
        float step = totalDuration * STAGGER_STEP_FRACTION / (staggerCount - 1);
        float elementDuration = Math.max(0.001F, totalDuration - step * (staggerCount - 1));

        return Mth.clamp(
                ((float) menuElapsedSeconds - (step * getStaggerIndex(index, count, animation))) / elementDuration,
                0.0F,
                1.0F);
    }

    public float getGlobalRevealProgress(RadialConfig config) {
        if (config.revealDurationMs <= 0) return 1.0F;
        return Mth.clamp((float) (revealElapsedSeconds / (config.revealDurationMs / 1000.0F)), 0.0F, 1.0F);
    }

    public float easeOutQuint(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse * inverse * inverse;
    }

    public float easeOutCubic(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    // --- Input & Actions ---

    private void resetCursorPosition() {
        RadialConfig.ActivationMode mode = RadialConfig.INSTANCE.activationMode;
        if (mode == RadialConfig.ActivationMode.CLICK || mode == RadialConfig.ActivationMode.RELEASE) {
            long windowHandle = minecraft.getWindow().handle();
            double centerX = minecraft.getWindow().getScreenWidth() / 2.0;
            double centerY = minecraft.getWindow().getScreenHeight() / 2.0;
            GLFW.glfwSetCursorPos(windowHandle, centerX, centerY);
        }
    }

    private void performAction(RadialSlot slot) {
        slot.mode.performAction(slot, new SlotActionContext() {
            @Override
            public void closeScreen() {
                RadialClient.lockKey();
                RadialScreen.this.onClose();
            }

            @Override
            public void openSubmenu(List<RadialSlot> children, int slotCount) {
                // Hard limit the submenu depth
                if (history.size() >= 5) {
                    return;
                }

                history.push(new MenuState(activeSlots, currentSlotCount));

                activeSlots = children;
                currentSlotCount = slotCount;
                resetAnims();
                prepareSectorRenderer();
                if (RadialConfig.INSTANCE.resetCursorOnSubmenu) {
                    resetCursorPosition();
                }
            }

            @Override
            public boolean isRoot() {
                return activeSlots == rootSlots;
            }
        });
    }

    private void goBack() {
        if (!history.isEmpty()) {
            MenuState previous = history.pop();
            activeSlots = previous.slots;
            currentSlotCount = previous.slotCount;
        } else {
            // Fallback just in case, though it shouldn't be reached if the back button is hidden on root
            activeSlots = rootSlots;
            currentSlotCount = RadialConfig.INSTANCE.slotCount;
        }

        resetAnims();
        prepareSectorRenderer();
        if (RadialConfig.INSTANCE.resetCursorOnSubmenu) {
            resetCursorPosition();
        }
    }

    private void resetAnims() {
        Arrays.fill(pushAnim, 0.0F);

        RadialConfig.ActivationMode mode = RadialConfig.INSTANCE.activationMode;
        hoveredSlot =
                (mode == RadialConfig.ActivationMode.SCROLL_CLICK || mode == RadialConfig.ActivationMode.SCROLL_RELEASE)
                        ? 0
                        : -1;

        menuElapsedSeconds = 0.0;
        lastNano = System.nanoTime();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        RadialConfig.ActivationMode mode = RadialConfig.INSTANCE.activationMode;
        if (mode == RadialConfig.ActivationMode.SCROLL_CLICK || mode == RadialConfig.ActivationMode.SCROLL_RELEASE) {
            int renderCount = getRenderCount();
            if (renderCount > 0 && scrollY != 0) {
                int shift = scrollY > 0 ? -1 : 1;
                hoveredSlot = (hoveredSlot + shift + renderCount) % renderCount;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent click, boolean doubled) {
        if (hoveredSlot != -1) {
            if (click.button() == 0) {
                if (isSubmenu() && hoveredSlot == 0) {
                    goBack();
                    return true;
                }

                RadialSlot slot = getTargetSlot(hoveredSlot);
                if (slot != null) {
                    performAction(slot);
                    return true;
                }
            } else if (click.button() == 1) {
                if (isSubmenu() && hoveredSlot == 0) {
                    return true; // Right-clicking "Back" does nothing
                }

                RadialSlot slot = getTargetSlot(hoveredSlot);
                if (slot != null) {
                    minecraft.gui.setScreen(new SlotEditorScreen(slot));
                    return true;
                }
            }
        }

        if (click.button() == 0) {
            onClose();
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        // 1. Check if the "Back" key was pressed
        if (RadialClient.BACK_KEY.matches(event)) {
            if (isSubmenu()) {
                goBack();
                return true;
            }
        }

        // 2. Check if any of the Slot 1-12 keys were pressed
        for (int i = 0; i < RadialClient.SLOT_KEYS.length; i++) {
            if (RadialClient.SLOT_KEYS[i].matches(event)) {
                if (i < currentSlotCount && i < activeSlots.size()) {
                    performAction(activeSlots.get(i));
                    return true;
                }
            }
        }

        // Pass any other keys (like ESC) to the default screen handler
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Intentionally skip super.extractBackground to remove the dark gradient

        if (RadialConfig.INSTANCE.enableBackgroundBlur) {
            // The blur strength is animated by OptionsMixin
            this.extractBlurredBackground(graphics);
        }
    }
}
