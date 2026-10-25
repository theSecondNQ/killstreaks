package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import java.util.Locale;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Rebuild the earned splash with recovered layers, layouts and native keyframes.
 * 使用恢复的原图层、布局与关键帧重建获取提示；仅操作自己的 GUI 姿态，不改字体注册。
 */
public final class EarnedSplashRenderer {
    private static final int WHITE = 0xffffffff, ACCENT = 0xff79c370, SUPPORT = 0xff368947, COOL = 0xff141313;
    private static EarnedSplashAnimation animation;
    private static boolean loaded;

    private EarnedSplashRenderer() {}

    static void reload() {
        animation = null;
        loaded = false;
    }

    static EarnedSplashAnimation animation() {
        if (!loaded) {
            loaded = true;
            var resource = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/earned-splash.json");
            try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource)
                    .openAsReader()) {
                animation = new EarnedSplashAnimation(JsonParser.parseReader(reader).getAsJsonObject());
            }
            catch (Exception failure) {
                LogUtils.getLogger().warn("Killstreaks: earned splash resources unavailable", failure);
            }
        }
        return animation;
    }

    static void render(GuiGraphics graphics, String reward, String hint, float milliseconds) {
        try (UiLayout.Scope layoutFrame = UiLayout.frame("hud.earned");
                UiLayout.Scope group = UiLayout.group(graphics, "notice", new UiLayout.Box(graphics.guiWidth() * .32F,
                        graphics.guiHeight() * .04F, graphics.guiWidth() * .36F, graphics.guiHeight() * .22F))) {
            var nativeAnimation = animation();
            if (nativeAnimation == null || !nativeAnimation.visible(milliseconds))
                return;
            String title = Component.translatable("streak.killstreakcod." + reward).getString()
                    .toUpperCase(Locale.ROOT);
            hint = hint.toUpperCase(Locale.ROOT);
            float scale = Math.min(graphics.guiWidth() / 1920F, graphics.guiHeight() / 1080F);
            var font = Minecraft.getInstance().font;
            float titleScale = Math.min(36F / font.lineHeight, 310F / Math.max(1, font.width(bold(title))));
            float hintScale = Math.min(18F / font.lineHeight, 310F / Math.max(1, font.width(bold(hint))));
            float textWidth = Math.max(font.width(bold(title)) * titleScale, font.width(bold(hint)) * hintScale);
            graphics.pose().pushPose();
            graphics.pose().translate(graphics.guiWidth() / 2F - 960 * scale, nativeAnimation.top() * scale, 150);
            graphics.pose().scale(scale, scale, 1);
            try {
                var content = nativeAnimation.bounds("splash.MainContent", milliseconds);
                graphics.pose().pushPose();
                graphics.pose().translate(content.x(), content.y(), 0);
                try {
                    content(graphics, nativeAnimation, milliseconds, title, hint, titleScale, hintScale);
                } finally {
                    graphics.pose().popPose();
                }
                longBanner(graphics, nativeAnimation, milliseconds, textWidth, 960 + graphics.guiWidth() / (2 * scale));
                iconAndDiamond(graphics, nativeAnimation, reward, milliseconds);
            } finally {
                graphics.flush();
                graphics.pose().popPose();
            }

        }
    }

    private static void longBanner(GuiGraphics graphics, EarnedSplashAnimation nativeAnimation, float time,
            float textWidth, float screenRight) {
        // 原闭包依据标题和按键提示的最大宽度计算 _bannerEndPos。
        float end = 960 - (150 + textWidth + 72) / 2 + 135;
        float left = Mth.lerp(outQuadratic(time / 200), screenRight, end);
        float alpha = 1 - Mth.clamp(time / 350, 0, 1);
        float outro = time - nativeAnimation.displayTime();
        if (outro >= 200) {
            left = Mth.lerp(outQuadratic((outro - 200) / 100), screenRight, end);
            alpha = 1;
            if (outro >= 300)
                left = Mth.lerp(outQuadratic((outro - 300) / 100), end, screenRight);
        }
        fill(graphics, left, 23, screenRight, 63, ACCENT, alpha);
    }

    private static void content(GuiGraphics graphics, EarnedSplashAnimation animation, float time, String title,
            String hint, float titleScale, float hintScale) {
        var upper = animation.bounds("content.UpperBacker", time);
        float shrink = outQuadratic((time - animation.displayTime() - 300) / 100);
        float x = Mth.lerp(shrink, upper.x(), upper.x() + upper.width() - 1);
        int upperColor = 0xff000000 | (int) animation.value("content.UpperBacker", "RGB", time, SUPPORT & 0xffffff);
        float upperAlpha = animation.value("content.UpperBacker", "Alpha", time, 0);
        OriginalRewardUi.notchedLayer(graphics, "hud_splash_notched_background_5", x, upper.y(),
                upper.x() + upper.width() - x, upper.height(), upperColor, upperAlpha, 22.5F, .3F);

        var divider = animation.bounds("content.Divider", time);
        if (time >= 200)
            fill(graphics, divider.x(), divider.y(), divider.x() + divider.width(), divider.y() + divider.height(),
                    WHITE, .85F);
        var lower = animation.bounds("content.LowerBacker", time);
        OriginalRewardUi.surface(graphics, "widg_gradient_left_to_right", lower.x(), lower.y(), lower.width(),
                lower.height(), COOL, animation.value("content.LowerBacker", "Alpha", time, 0));
        for (String element : new String[] { "content.Chevron1", "content.Chevron2" }) {
            var bounds = animation.bounds(element, time);
            OriginalRewardUi.surface(graphics, "hud_splash_accent_chevrons_single", bounds.x(), bounds.y(),
                    bounds.width(), bounds.height(), SUPPORT, animation.value(element, "Alpha", time, 0));
        }
        text(graphics, title, animation.bounds("content.MainText", time), titleScale, ACCENT,
                animation.value("content.MainText", "Alpha", time, 0));
        text(graphics, hint, animation.bounds("content.HintText", time), hintScale, WHITE,
                animation.value("content.HintText", "Alpha", time, 0));
    }

    private static void iconAndDiamond(GuiGraphics graphics, EarnedSplashAnimation animation, String reward,
            float time) {
        // LUI 的 Scale=0 为原尺寸，Scale=2 为额外放大两倍，不能把零当作不可见。
        var icon = animation.bounds("splash.StreakIcon", time);
        float iconScale = 1 + animation.value("splash.StreakIcon", "Scale", time, 0);
        OriginalRewardUi.iconSurface(graphics, reward, icon.x() - icon.width() * (iconScale - 1) / 2,
                icon.y() - icon.height() * (iconScale - 1) / 2, icon.width() * iconScale, icon.height() * iconScale,
                WHITE, animation.value("splash.StreakIcon", "Alpha", time, 0));
        var diamond = animation.bounds("splash.Diamond", time);
        float diamondScale = 1 + animation.value("splash.Diamond", "Scale", time, 0);
        OriginalRewardUi.surface(graphics, "hud_splash_accent_diamond",
                diamond.x() - diamond.width() * (diamondScale - 1) / 2,
                diamond.y() - diamond.height() * (diamondScale - 1) / 2, diamond.width() * diamondScale,
                diamond.height() * diamondScale, COOL, animation.value("splash.Diamond", "Alpha", time, 0));
    }

    private static Component bold(String text) {
        return Component.literal(text).withStyle(ChatFormatting.BOLD);
    }

    private static void text(GuiGraphics graphics, String text, EarnedSplashAnimation.Rect bounds, float scale,
            int tint, float alpha) {
        if (alpha < .005F)
            return;
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x(), bounds.y() + (bounds.height() - 9 * scale) / 2, 0);
        graphics.pose().scale(scale, scale, 1);
        try {
            UiDraw.drawString(graphics, "EarnedSplashRenderer.text.0", Minecraft.getInstance().font, bold(text), 0, 0,
                    Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24 | tint & 0xffffff, false);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void fill(GuiGraphics graphics, float left, float top, float right, float bottom, int tint,
            float alpha) {
        if (alpha > .002F && right > left && bottom > top)
            UiDraw.fill(graphics, "EarnedSplashRenderer.fill.0", Math.round(left), Math.round(top), Math.round(right),
                    Math.round(bottom), Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24 | tint & 0xffffff);
    }

    private static float outQuadratic(float value) {
        value = Mth.clamp(value, 0, 1);
        return 1 - (1 - value) * (1 - value);
    }
}
