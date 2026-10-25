package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Original high resolution glyphs and HUD layers, with their real alpha bounds.
 * 使用原资源高分辨率图标和 HUD 图层，并按实际透明边界处理。
 */
public final class OriginalRewardUi {
    private static Map<String, Sprite> sprites;

    private record Sprite(ResourceLocation texture, int width, int height, int x, int y, int w, int h) {
    }

    private OriginalRewardUi() {}

    public static void reload() {
        sprites = null;
        EarnedSplashRenderer.reload();
    }

    private static void load() {
        if (sprites != null)
            return;
        Map<String, Sprite> result = new HashMap<>();
        var location = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/notice-assets.json");
        try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (String section : new String[] { "icons", "layers" })
                for (var entry : root.getAsJsonObject(section).entrySet()) {
                    var data = entry.getValue().getAsJsonObject();
                    var bounds = data.getAsJsonArray("bounds");
                    int width = data.get("width").getAsInt(), height = data.get("height").getAsInt(),
                            x = bounds.get(0).getAsInt(), y = bounds.get(1).getAsInt();
                    int w = bounds.get(2).getAsInt() - x, h = bounds.get(3).getAsInt() - y;
                    if (width <= 0 || height <= 0 || w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > width
                            || y + h > height)
                        continue;
                    var texture = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
                            "textures/gui/mwii/" + data.get("name").getAsString() + ".png");
                    result.put(entry.getKey(), new Sprite(texture, width, height, x, y, w, h));
                }
        }
        catch (Exception failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not load original reward UI metadata", failure);
        }

        // 将全黑的 *_anim 导出图记录为不可用，不猜测它们的精灵帧网格。
        sprites = Map.copyOf(result);
    }

    public static void icon(GuiGraphics graphics, String reward, float cx, float cy, float size, int tint,
            float opacity) {
        load();
        Sprite sprite = sprites.get(reward);
        if (sprite == null) {
            sprite = new Sprite(ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
                    "textures/gui/streaks/" + reward + ".png"), 64, 64, 0, 0, 64, 64);
        }
        float fit = size / Math.max(sprite.w, sprite.h);
        draw(graphics, sprite, cx - sprite.w * fit / 2, cy - sprite.h * fit / 2, sprite.w * fit, sprite.h * fit, tint,
                opacity, false, true);
    }

    public static void layer(GuiGraphics graphics, String name, float x, float y, float w, float h, int tint,
            float opacity, boolean additive) {
        load();
        Sprite sprite = sprites.get(name);
        if (sprite != null)
            draw(graphics, sprite, x, y, w, h, tint, opacity, additive, !additive);
    }

    /** Keep source transparent margins for native layout coordinates. 保留原图透明边界以匹配原生布局。 */
    static void surface(GuiGraphics graphics, String name, float x, float y, float w, float h, int tint,
            float opacity) {
        load();
        Sprite sprite = sprites.get(name);
        if (sprite != null)
            draw(graphics, sprite, x, y, w, h, tint, opacity, false, false);
    }

    static void iconSurface(GuiGraphics graphics, String reward, float x, float y, float w, float h, int tint,
            float opacity) {
        surface(graphics, reward, x, y, w, h, tint, opacity);
    }

    /** Draw a separately indexed native surface. 绘制单独索引的原图层，保留尺寸、透明边界与混合方式。 */
    static void textureSurface(GuiGraphics graphics, ResourceLocation texture, int textureWidth, int textureHeight,
            float x, float y, float width, float height, int tint, float opacity, boolean additive) {
        draw(graphics, new Sprite(texture, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight), x, y, width,
                height, tint, opacity, additive, false);
    }

    /** Preserve both notched end caps while resizing the banner. 横向三段拉伸，保持横幅两端缺口形状。 */
    static void notchedLayer(GuiGraphics graphics, String name, float x, float y, float w, float h, int tint,
            float opacity, float capWidth, float capFraction) {
        load();
        Sprite sprite = sprites.get(name);
        if (sprite == null || w <= 0 || h <= 0)
            return;
        int cut = Math.round(sprite.width * capFraction);
        float cap = Math.min(capWidth, w / 2);
        draw(graphics, new Sprite(sprite.texture, sprite.width, sprite.height, 0, 0, cut, sprite.height), x, y, cap, h,
                tint, opacity, false, true);
        draw(graphics,
                new Sprite(sprite.texture, sprite.width, sprite.height, cut, 0, sprite.width - 2 * cut, sprite.height),
                x + cap, y, w - 2 * cap, h, tint, opacity, false, true);
        draw(graphics,
                new Sprite(sprite.texture, sprite.width, sprite.height, sprite.width - cut, 0, cut, sprite.height),
                x + w - cap, y, cap, h, tint, opacity, false, true);
    }

    private static void draw(GuiGraphics graphics, Sprite sprite, float x, float y, float w, float h, int tint,
            float opacity, boolean additive, boolean crop) {
        if (opacity <= .002F || w <= 0 || h <= 0)
            return;
        graphics.flush();
        boolean blending = GL11.glIsEnabled(GL11.GL_BLEND);
        float[] color = RenderSystem.getShaderColor().clone();
        int[] factors = { GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) };
        RenderSystem.enableBlend();
        if (additive)
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        else
            RenderSystem.defaultBlendFunc();
        graphics.setColor(((tint >> 16) & 255) / 255F, ((tint >> 8) & 255) / 255F, (tint & 255) / 255F,
                Mth.clamp(opacity, 0, 1) * ((tint >>> 24) / 255F));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(w / Math.max(1, crop ? sprite.w : sprite.width),
                h / Math.max(1, crop ? sprite.h : sprite.height), 1);
        try {
            UiDraw.blit(graphics, "OriginalRewardUi.draw.0", sprite.texture, 0, 0, crop ? sprite.w : sprite.width,
                    crop ? sprite.h : sprite.height, crop ? sprite.x : 0, crop ? sprite.y : 0,
                    crop ? sprite.w : sprite.width, crop ? sprite.h : sprite.height, sprite.width, sprite.height);
        } finally {
            graphics.flush();
            graphics.pose().popPose();
            graphics.setColor(color[0], color[1], color[2], color[3]);
            RenderSystem.blendFuncSeparate(factors[0], factors[1], factors[2], factors[3]);
            if (!blending)
                RenderSystem.disableBlend();
        }
    }
}
