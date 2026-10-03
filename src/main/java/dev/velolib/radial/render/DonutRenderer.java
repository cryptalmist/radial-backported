package dev.velolib.radial.render;

import dev.velolib.radial.RadialClient;
import dev.velolib.radial.config.RadialConfig;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * A highly optimized, asynchronous, texture-based renderer for radial menu sectors (donut slices).
 * <p>
 * Instead of calculating complex polygons or shaders every frame, this class generates a single,
 * high-resolution, anti-aliased texture of a radial sector using CPU-side distance fields and
 * 2x2 supersampling. This texture is then cached and rendered multiple times with different
 * matrix rotations to form the complete radial menu.
 * <p>
 * <b>Key Features:</b>
 * <ul>
 *     <li><b>Asynchronous Generation:</b> Texture math is offloaded to a background thread to prevent client stuttering.</li>
 *     <li><b>Texture Caching:</b> Regenerates only when configuration or geometry changes.</li>
 *     <li><b>Supersampled Anti-Aliasing (SSAA):</b> Ensures smooth, curved edges at any resolution using a 2x2 sub-pixel grid.</li>
 * </ul>
 */
public class DonutRenderer implements AutoCloseable {

    private static final float AA_WIDTH = 1.0f;
    private static final int AA_SAMPLES = 4;
    private static final float ZERO_GAP_EPSILON = 0.0001f;

    private final String idSuffix;

    private NativeImageBackedTexture baseTexture;
    private NativeImageBackedTexture hotTexture;

    private Identifier baseTexId;
    private Identifier hotTexId;

    /**
     * Tracks the current active background generation task.
     * Prevents older, slower async tasks from overwriting newer textures if the user
     * rapidly changes configurations (e.g., dragging a radius slider).
     */
    private long generationId = 0;

    // Cached geometry/configuration to detect when a regeneration is required.
    private int lastCount = -1;
    private float lastInner = -1.0f;
    private float lastOuter = -1.0f;
    private float lastGap = -1.0f;
    private float lastResScale = -1.0f;
    private int lastTexSize = -1;

    private boolean lastDrawSectorBorders = true;
    private float lastSectorBorderWidth = -1.0f;
    private boolean lastDrawOuterBorders = false;
    private int lastBackgroundColor = -1;
    private int lastActivationColor = -1;
    private int lastBorderColor = -1;
    private int lastHighlightBorderColor = -1;

    private int texSize = 0;

    public DonutRenderer(String idSuffix) {
        this.idSuffix = idSuffix;
    }

    /**
     * Validates the current cache against the provided geometry and config.
     * <p>
     * If any parameter (radius, gaps, colors, etc.) has changed, this triggers
     * an asynchronous regeneration of the sector textures. If nothing has changed,
     * this method exits immediately (zero cost).
     *
     * @param count    The total number of sectors in the radial menu.
     * @param inner    The inner radius of the donut.
     * @param outer    The outer radius of the donut.
     * @param resScale The resolution scale multiplier for sharp textures on high-DPI displays.
     */
    public void prepare(int count, float inner, float outer, float resScale) {
        RadialConfig config = RadialConfig.INSTANCE;

        float maxR = outer + 4.0f;
        int requiredTexSize = (int) (maxR * 2.0f * resScale) + 4;

        if (requiredTexSize <= 0) {
            return;
        }

        boolean needsRegeneration = baseTexture == null
                || hotTexture == null
                || lastCount != count
                || lastInner != inner
                || lastOuter != outer
                || lastGap != config.sectorGap
                || lastResScale != resScale
                || lastTexSize != requiredTexSize
                || lastDrawSectorBorders != config.drawSectorBorders
                || lastSectorBorderWidth != config.sectorBorderWidth
                || lastDrawOuterBorders != config.drawOuterBorders
                || lastBackgroundColor != config.backgroundColor.getRGB()
                || lastActivationColor != config.activationColor.getRGB()
                || lastBorderColor != config.borderColor.getRGB()
                || lastHighlightBorderColor != config.highlightBorderColor.getRGB();

        if (!needsRegeneration) {
            return;
        }

        regenerateTextures(requiredTexSize, count, inner, outer, resScale, config);

        lastCount = count;
        lastInner = inner;
        lastOuter = outer;
        lastGap = config.sectorGap;
        lastResScale = resScale;
        lastTexSize = requiredTexSize;
        lastDrawSectorBorders = config.drawSectorBorders;
        lastSectorBorderWidth = config.sectorBorderWidth;
        lastDrawOuterBorders = config.drawOuterBorders;
        lastBackgroundColor = config.backgroundColor.getRGB();
        lastActivationColor = config.activationColor.getRGB();
        lastBorderColor = config.borderColor.getRGB();
        lastHighlightBorderColor = config.highlightBorderColor.getRGB();
    }

    /**
     * Renders the pre-generated sector texture to the screen.
     * <p>
     * Performs matrix transformations (translation and rotation) to place the single
     * sector texture into its correct slot in the radial ring. Does <b>not</b> perform
     * any heavy calculations or texture generation.
     *
     * @param graphics    The GUI graphics extractor for drawing.
     * @param cx          The X coordinate of the screen center.
     * @param cy          The Y coordinate of the screen center.
     * @param slotAngle   The angle (in radians) to rotate this specific sector.
     * @param push        The distance to push the sector outward (used for hover animations).
     * @param highlighted True to use the highlighted (hover) texture, false for the base texture.
     * @param ease        The animation progress (0.0 to 1.0) used for scaling and opacity interpolation.
     * @param resScale    The resolution scale to properly size the texture on screen.
     */
    public void renderSector(
            DrawContext graphics,
            float cx,
            float cy,
            float slotAngle,
            float push,
            boolean highlighted,
            float ease,
            float resScale) {
        if (baseTexture == null || hotTexture == null || baseTexId == null || hotTexId == null) {
            return;
        }

        float clampedEase = MathHelper.clamp(ease, 0.0f, 1.0f);
        if (clampedEase <= 0.0f) {
            return;
        }

        graphics.getMatrices().pushMatrix();
        graphics.getMatrices().translate(cx, cy);
        graphics.getMatrices().rotate(slotAngle);
        graphics.getMatrices().translate(push * clampedEase, 0);
        graphics.getMatrices().scale(clampedEase / resScale, clampedEase / resScale);

        int offset = -texSize / 2;
        Identifier texture = highlighted ? hotTexId : baseTexId;
        int alpha = MathHelper.clamp((int) (clampedEase * 255.0f + 0.5f), 0, 255);

        graphics.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                offset,
                offset,
                0,
                0,
                texSize,
                texSize,
                texSize,
                texSize,
                (alpha << 24) | 0xFFFFFF);

        graphics.getMatrices().popMatrix();
    }

    /**
     * Spawns a background thread to calculate the pixel data for the sector textures.
     * <p>
     * Uses {@link #generationId} to track the most recent request. If the user rapidly resizes
     * the radial menu, older background tasks will detect a mismatched ID upon completion and
     * gracefully discard their results. GPU texture registration is safely delegated back
     * to the main Minecraft thread.
     */
    private void regenerateTextures(int targetTexSize, int count, float inner, float outer, float resScale, RadialConfig config) {
        final long currentGenId = ++this.generationId;

        final int bgColor = config.backgroundColor.getRGB();
        final int borderColor = config.borderColor.getRGB();
        final int hotColor = config.activationColor.getRGB();
        final int hotBorderColor = config.highlightBorderColor.getRGB();
        final boolean drawSectorBorders = config.drawSectorBorders;
        final boolean drawOuterBorders = config.drawOuterBorders;
        final float sectorBorderWidth = config.sectorBorderWidth;
        final float sectorGap = config.sectorGap;

        CompletableFuture.supplyAsync(() -> {
                    NativeImage baseImage = new NativeImage(targetTexSize, targetTexSize, false);
                    NativeImage hotImage = null;

                    try {
                        hotImage = new NativeImage(targetTexSize, targetTexSize, false);

                        generatePixels(
                                baseImage,
                                hotImage,
                                targetTexSize,
                                count,
                                inner,
                                outer,
                                resScale,
                                bgColor,
                                borderColor,
                                hotColor,
                                hotBorderColor,
                                drawSectorBorders,
                                drawOuterBorders,
                                sectorBorderWidth,
                                sectorGap);
                    } catch (RuntimeException e) {
                        // Free the native memory before reporting the failure
                        baseImage.close();
                        if (hotImage != null) hotImage.close();
                        throw e;
                    }

                    return new NativeImage[] {baseImage, hotImage};
                })
                .thenAcceptAsync(
                        images -> {
                            // Drop results if a newer generation request was fired while this was processing
                            if (this.generationId != currentGenId) {
                                images[0].close();
                                images[1].close();
                                return;
                            }

                            if (this.baseTexture != null) this.baseTexture.close();
                            if (this.hotTexture != null) this.hotTexture.close();

                            this.texSize = targetTexSize;

                            this.baseTexture = new NativeImageBackedTexture(() -> "", images[0]);
                            this.hotTexture = new NativeImageBackedTexture(() -> "", images[1]);
                            this.baseTexture.upload();
                            this.hotTexture.upload();

                            this.baseTexId = Identifier.of("radial", "sector_base_" + this.idSuffix);
                            this.hotTexId = Identifier.of("radial", "sector_hot_" + this.idSuffix);

                            MinecraftClient.getInstance()
                                    .getTextureManager()
                                    .registerTexture(this.baseTexId, this.baseTexture);
                            MinecraftClient.getInstance()
                                    .getTextureManager()
                                    .registerTexture(this.hotTexId, this.hotTexture);
                        },
                        MinecraftClient.getInstance())
                .exceptionally(e -> {
                    RadialClient.LOGGER.error("Failed to generate radial sector textures", e);
                    return null;
                });
    }

    /**
     * The core mathematical engine for drawing the anti-aliased sector.
     * <p>
     * Iterates over the bounding box of the texture. To optimize performance, it uses an
     * <b>early exit</b> strategy to completely skip pixels that fall outside the donut's
     * inner and outer radii, explicitly clearing them to transparent to avoid garbage memory.
     * <p>
     * For pixels near the edges, it performs 2x2 supersampling, calculating the radial and
     * angular distance from the center, and applies a {@link #smoothstep(float)} function
     * to blend the colors, resulting in a perfectly smooth curve.
     */
    private void generatePixels(
            NativeImage baseImage,
            NativeImage hotImage,
            int texSize,
            int count,
            float inner,
            float outer,
            float resScale,
            int baseColor,
            int borderColor,
            int hotBaseColor,
            int hotBorderColor,
            boolean drawSectorBorders,
            boolean drawOuterBorders,
            float sectorBorderWidth,
            float sectorGap) {
        float center = texSize / 2.0f;
        float innerR = inner * resScale;
        float outerR = outer * resScale;
        float borderW = sectorBorderWidth * resScale;
        float aa = Math.max(0.75f, AA_WIDTH * resScale);
        float halfSpan = (float) (Math.PI / count);

        float gap = Math.max(0.0f, sectorGap * resScale);
        boolean zeroGap = gap <= ZERO_GAP_EPSILON;
        float halfGap = gap * 0.5f;
        float overlapWidth = 0.8f;

        for (int y = 0; y < texSize; y++) {
            for (int x = 0; x < texSize; x++) {

                float pxCenter = x + 0.5f;
                float pyCenter = y + 0.5f;
                float dxCenter = pxCenter - center;
                float dyCenter = pyCenter - center;
                float distCenter = MathHelper.sqrt(dxCenter * dxCenter + dyCenter * dyCenter);

                // Early exit: Avoid heavy math on empty space (both inner hole and outer corners)
                if (distCenter < innerR - aa - 1.0f || distCenter > outerR + aa + 1.0f) {
                    baseImage.setColor(x, y, 0x00000000);
                    hotImage.setColor(x, y, 0x00000000);
                    continue;
                }

                float angleCenter = (float) (float) Math.atan2(dyCenter, dxCenter);
                float angularDistanceCenter = (halfSpan - Math.abs(angleCenter)) * distCenter;

                float centerAngularAlpha = 0.0f;
                boolean centerInOverlapZone = false;

                if (zeroGap) {
                    if (angularDistanceCenter >= overlapWidth) {
                        centerAngularAlpha = 1.0f;
                    } else if (angularDistanceCenter > -overlapWidth) {
                        centerAngularAlpha = 1.0f;
                        centerInOverlapZone = true;
                    }
                }

                float baseAlphaTotal = 0.0f, baseRTotal = 0.0f, baseGTotal = 0.0f, baseBTotal = 0.0f;
                float hotAlphaTotal = 0.0f, hotRTotal = 0.0f, hotGTotal = 0.0f, hotBTotal = 0.0f;

                // 2x2 Supersampling loop
                for (int sy = 0; sy < 2; sy++) {
                    for (int sx = 0; sx < 2; sx++) {
                        float px = x + (sx == 0 ? 0.25f : 0.75f);
                        float py = y + (sy == 0 ? 0.25f : 0.75f);
                        float dx = px - center;
                        float dy = py - center;
                        float dist = MathHelper.sqrt(dx * dx + dy * dy);

                        if (dist < innerR - aa || dist > outerR + aa) continue;

                        float angle = (float) (float) Math.atan2(dy, dx);
                        float angularDistance = (halfSpan - Math.abs(angle)) * dist;

                        float angularAlpha;
                        boolean inOverlapZone = false;

                        if (zeroGap) {
                            angularAlpha = centerAngularAlpha;
                            inOverlapZone = centerInOverlapZone;
                        } else {
                            float edgeDistance = angularDistance - halfGap;
                            angularAlpha = smoothstep(MathHelper.clamp(edgeDistance / aa, 0.0f, 1.0f));
                        }

                        if (angularAlpha <= 0.0f) continue;

                        float radialDistance = Math.min(dist - innerR, outerR - dist);
                        float radialAlpha = smoothstep(MathHelper.clamp(radialDistance / aa, 0.0f, 1.0f));

                        if (radialAlpha <= 0.0f) continue;

                        float coverage = angularAlpha * radialAlpha;
                        boolean border = drawOuterBorders && (dist <= innerR + borderW || dist >= outerR - borderW);

                        if (!border && drawSectorBorders) {
                            float sideDistance = zeroGap ? angularDistance : (angularDistance - halfGap);
                            if (sideDistance >= 0.0f && sideDistance <= borderW) {
                                border = true;
                            }
                        }

                        int baseFinalColor = border ? borderColor : baseColor;
                        int hotFinalColor = border ? hotBorderColor : hotBaseColor;

                        float baseAlpha = (((baseFinalColor >> 24) & 0xFF) / 255.0f) * coverage;
                        float hotAlpha = (((hotFinalColor >> 24) & 0xFF) / 255.0f) * coverage;

                        if (zeroGap && inOverlapZone && !border) {
                            baseAlpha = 1.0f - (float) Math.sqrt(Math.max(0.0f, 1.0f - baseAlpha));
                            hotAlpha = 1.0f - (float) Math.sqrt(Math.max(0.0f, 1.0f - hotAlpha));
                        }

                        baseAlphaTotal += baseAlpha;
                        baseRTotal += ((baseFinalColor >> 16) & 0xFF) * baseAlpha;
                        baseGTotal += ((baseFinalColor >> 8) & 0xFF) * baseAlpha;
                        baseBTotal += (baseFinalColor & 0xFF) * baseAlpha;

                        hotAlphaTotal += hotAlpha;
                        hotRTotal += ((hotFinalColor >> 16) & 0xFF) * hotAlpha;
                        hotGTotal += ((hotFinalColor >> 8) & 0xFF) * hotAlpha;
                        hotBTotal += (hotFinalColor & 0xFF) * hotAlpha;
                    }
                }

                // Average sub-pixels and write to textures
                baseAlphaTotal /= AA_SAMPLES;
                if (baseAlphaTotal <= 0.0f) {
                    baseImage.setColor(x, y, 0x00000000);
                } else {
                    float denominator = baseAlphaTotal * AA_SAMPLES;
                    int alpha = MathHelper.clamp((int) (baseAlphaTotal * 255.0f + 0.5f), 0, 255);
                    int r = MathHelper.clamp((int) (baseRTotal / denominator + 0.5f), 0, 255);
                    int g = MathHelper.clamp((int) (baseGTotal / denominator + 0.5f), 0, 255);
                    int b = MathHelper.clamp((int) (baseBTotal / denominator + 0.5f), 0, 255);

                    int outColor = (alpha << 24) | (r << 16) | (g << 8) | b;
                    baseImage.setColor(x, y, toABGR(outColor));
                }

                hotAlphaTotal /= AA_SAMPLES;
                if (hotAlphaTotal <= 0.0f) {
                    hotImage.setColor(x, y, 0x00000000);
                } else {
                    float denominator = hotAlphaTotal * AA_SAMPLES;
                    int alpha = MathHelper.clamp((int) (hotAlphaTotal * 255.0f + 0.5f), 0, 255);
                    int r = MathHelper.clamp((int) (hotRTotal / denominator + 0.5f), 0, 255);
                    int g = MathHelper.clamp((int) (hotGTotal / denominator + 0.5f), 0, 255);
                    int b = MathHelper.clamp((int) (hotBTotal / denominator + 0.5f), 0, 255);

                    int outColor = (alpha << 24) | (r << 16) | (g << 8) | b;
                    hotImage.setColor(x, y, toABGR(outColor));
                }
            }
        }
    }

    /**
     * Applies a cubic smoothstep interpolation.
     * Used to create anti-aliased transitions at the mathematical boundaries of the sector.
     *
     * @param t The input value between 0.0 and 1.0.
     * @return The smoothed output.
     */
    private float smoothstep(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Fast bitwise swapper to convert standard ARGB integers into OpenGL ABGR format.
     */
    private int toABGR(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >> 16) | ((argb & 0x000000FF) << 16);
    }

    /**
     * Frees the GPU textures and native memory buffers.
     * <p>
     * Must be called when the radial menu is closed or destroyed to prevent VRAM memory leaks.
     * Increments the generation ID to ensure any pending background tasks abort.
     */
    @Override
    public void close() {
        if (baseTexId != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(baseTexId);
        if (hotTexId != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(hotTexId);

        if (baseTexture != null) baseTexture.close();
        if (hotTexture != null) hotTexture.close();

        baseTexId = null;
        hotTexId = null;
        baseTexture = null;
        hotTexture = null;

        lastCount = -1;
        lastInner = -1.0f;
        lastOuter = -1.0f;
        lastGap = -1.0f;
        lastResScale = -1.0f;
        lastTexSize = -1;
        lastDrawSectorBorders = true;
        lastSectorBorderWidth = -1.0f;
        lastDrawOuterBorders = false;
        lastBackgroundColor = -1;
        lastActivationColor = -1;
        lastBorderColor = -1;
        lastHighlightBorderColor = -1;
        texSize = 0;

        generationId++;
    }
}
