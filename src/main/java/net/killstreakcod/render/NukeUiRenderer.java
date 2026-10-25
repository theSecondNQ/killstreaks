package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * MP nuclear warning composition; the live timer is supplied separately from the animation clock.
 * 多人模式核弹提示组合；实时倒计时独立于装饰动画，由服务端时间线提供。
 */
public final class NukeUiRenderer {
    private record Image(ResourceLocation texture, int width, int height) {
    }

    private static final Map<String, Image> images = new HashMap<>();
    private static NukeUiAnimation animation;
    private static boolean loaded;

    private NukeUiRenderer() {}

    static void reload() {
        loaded = false;
        animation = null;
        images.clear();
    }

    private static NukeUiAnimation animation() {
        if (!loaded) {
            loaded = true;
            var location = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/nuke-ui.json");
            try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location)
                    .openAsReader()) {
                var data = JsonParser.parseReader(reader).getAsJsonObject();
                animation = new NukeUiAnimation(data);
                for (var entry : data.getAsJsonObject("images").entrySet()) {
                    var image = entry.getValue().getAsJsonObject();
                    images.put(entry.getKey(), new Image(ResourceLocation.parse(image.get("texture").getAsString()),
                            image.get("width").getAsInt(), image.get("height").getAsInt()));
                }
            }
            catch (Exception failure) {
                animation = null;
                images.clear();
                LogUtils.getLogger().warn("Killstreaks: MP nuke UI resources unavailable", failure);
            }
        }
        return animation;
    }

    static void render(GuiGraphics graphics, float milliseconds, float remainingTicks, String caller) {
        try (UiLayout.Scope layoutFrame = UiLayout.frame("hud.nuke");
                UiLayout.Scope group = UiLayout.group(graphics, "warning", new UiLayout.Box(graphics.guiWidth() * .25F,
                        0, graphics.guiWidth() * .5F, graphics.guiHeight() * .28F))) {
            if (remainingTicks <= 0)
                return;
            NukeUiAnimation timeline = animation();
            if (timeline == null)
                return;
            float scale = Math.min(graphics.guiWidth() / 1920F, graphics.guiHeight() / 1080F);
            graphics.pose().pushPose();
            graphics.pose().translate((graphics.guiWidth() - 1920 * scale) / 2, 0, 600);
            graphics.pose().scale(scale, scale, 1);
            try {
                for (String id : timeline.elements()) {
                    try (UiLayout.Scope itemTag = UiLayout.tag(id)) {
                        var frame = timeline.frame(id, milliseconds);
                        var box = frame.bounds();
                        if (!frame.visible() || frame.alpha() < .002F || box.width() <= 0 || box.height() <= 0)
                            continue;
                        String text = switch (id) {
                            case "TacticalNukeText" -> localized("mgb_inbound");
                            case "CountdownElement" -> String.format(Locale.ROOT, "%04.1f", remainingTicks / 20F);
                            case "Gamertag" -> caller;
                            case "Message" -> localized("mgb_called");
                            case "Warning1", "Warning2" -> localized("mgb_warning");
                            default -> null;
                        };
                        if (text != null) {
                            text(graphics, frame, text);
                        } else if (!frame.image().isEmpty()) {
                            var image = images.get(frame.image());
                            if (image != null)
                                OriginalRewardUi.textureSurface(graphics, image.texture, image.width, image.height,
                                        box.x(), box.y(), box.width(), box.height(), 0xff000000 | frame.rgb(),
                                        frame.alpha(), frame.additive());
                        } else {
                            UiDraw.fill(graphics, "NukeUiRenderer.render.0", Math.round(box.x()), Math.round(box.y()),
                                    Math.round(box.x() + box.width()), Math.round(box.y() + box.height()),
                                    color(frame));
                        }
                    }
                }
            } finally {
                graphics.flush();
                graphics.pose().popPose();
            }

        } finally {
            UiLayout.flush(graphics);
        }
    }

    private static String localized(String suffix) {
        return Component.translatable("hud.killstreakcod." + suffix).getString().toUpperCase(Locale.ROOT);
    }

    private static int color(NukeUiAnimation.Frame frame) {
        return Math.round(Mth.clamp(frame.alpha(), 0, 1) * 255) << 24 | frame.rgb() & 0xffffff;
    }

    private static void text(GuiGraphics graphics, NukeUiAnimation.Frame frame, String value) {
        var font = Minecraft.getInstance().font;
        var box = frame.bounds();
        float scale = Math.min(frame.textSize() / font.lineHeight, box.height() / font.lineHeight);
        // 按原布局对齐；本地化长句只在自己的框内缩放，不影响全局字体或资源包。
        scale = Math.min(scale, box.width() / Math.max(1, font.width(value)));
        float x = switch (frame.alignment()) {
            case "Right" -> box.x() + box.width() - font.width(value) * scale;
            case "Center" -> box.x() + (box.width() - font.width(value) * scale) / 2;
            default -> box.x();
        };
        graphics.pose().pushPose();
        graphics.pose().translate(x, box.y() + (box.height() - font.lineHeight * scale) / 2, 0);
        graphics.pose().scale(scale, scale, 1);
        try {
            UiDraw.drawString(graphics, "NukeUiRenderer.text.0", font, value, 0, 0, color(frame), false);
        } finally {
            graphics.pose().popPose();
        }
    }
}
