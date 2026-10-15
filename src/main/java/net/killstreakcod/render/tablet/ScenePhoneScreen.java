package net.killstreakcod.render.tablet;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.NativeSceneUi;
import net.killstreakcod.render.TacticalClient;

import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Slot06's native 300 by 525 activation screen, composed on the original phone UVs.
 * 将 Slot06 原始 300×525 激活界面合成到原手机 UV 上。
 */
public final class ScenePhoneScreen {
    private static final int WIDTH = 300, HEIGHT = 525;
    private static DynamicTexture surface;
    private static ResourceLocation location;
    private static String reward = "", locale = "";
    private static int frame = -1;
    private static JsonArray labels;

    private ScenePhoneScreen() {}

    public static Map<String, ResourceLocation> materials(String id, float ticks) {
        var mc = Minecraft.getInstance();
        String language = mc.getLanguageManager().getSelected();
        int next = (int) (ticks / 20 * 30);
        if (surface == null) {
            surface = new DynamicTexture(new NativeImage(WIDTH, HEIGHT, false));
            surface.setFilter(true, false);
            location = mc.getTextureManager().register("mwii_native_phone", surface);
        }
        if (next != frame || !id.equals(reward) || !language.equals(locale)) {
            frame = next;
            reward = id;
            locale = language;
            paint(id, ticks);
            surface.upload();
        }
        return Map.of("native/phone_screen", location);
    }

    private static void paint(String id, float ticks) {
        NativeImage target = surface.getPixels();
        target.fillRect(0, 0, WIDTH, HEIGHT, 0xff0a0f0b);
        TabletCanvas canvas = new TabletCanvas(target);
        float time = ticks * 50;
        String phase = "Activate";
        for (var layer : NativeSceneUi.layers("StreakActivationPhone", phase, time, WIDTH, HEIGHT, false))
            canvas.image(NativeSceneUi.pixels(layer.image()), layer.rect(), layer.alpha(), layer.rotation(),
                    layer.scale(), -1);
        var icon = NativeSceneUi.state("StreakActivationPhone", "StreakImage", phase, time, WIDTH, HEIGHT);
        String image = switch (id) {
            case "advanced_uav" -> "hud_icon_killstreak_auav";
            case "counter_uav" -> "hud_icon_killstreak_cuav";
            case "overwatch_helo" -> "hud_icon_killstreak_chopper_support";
            default -> "hud_icon_killstreak_uav";
        };
        if (icon != null) {
            float alpha = icon.alpha();
            if (time >= 850) {
                var idle = NativeSceneUi.state("StreakActivationPhone", "StreakImage", "Idle", (time - 850) % 800,
                        WIDTH, HEIGHT);
                if (idle != null)
                    alpha = idle.alpha();
            }
            canvas.image(NativeSceneUi.pixels(image), icon.rect(), alpha, icon.rotation(), icon.scale(), -1);
        }
        var button = NativeSceneUi.state("StreakActivationPhone", "ButtonSlide", phase, time, WIDTH, HEIGHT);
        if (button != null && button.alpha() > 0)
            fill(canvas, button, 0xff8aaa5e);
        var title = NativeSceneUi.state("StreakActivationPhone", "Title", phase, time, WIDTH, HEIGHT);
        if (title != null)
            canvas.text(Component.translatable("streak.killstreakcod." + id), box(title.rect()), 24, 0xffb7d889,
                    title.alpha(), true);
        var text = NativeSceneUi.state("StreakActivationPhone", "Text", phase, time, WIDTH, HEIGHT);
        if (text != null) {
            String value = label(time >= 560 ? 1 : 0);
            String[] lines = value.split("\n", -1);
            var rect = text.rect();
            for (int i = 0; i < lines.length; i++)
                canvas.text(lines[i],
                        new TabletCanvas.Box(Math.round(rect.x()), Math.round(rect.y() + i * rect.h() / lines.length),
                                Math.round(rect.w()), Math.round(rect.h() / lines.length)),
                        36, 0xffb7d889, text.alpha(), true);
        }
        if (TacticalClient.radar.jammed())
            canvas.fill(new TabletCanvas.Box(0, 0, WIDTH, HEIGHT), 0xff000000);
    }

    private static TabletCanvas.Box box(NativeSceneUi.Rect r) {
        return new TabletCanvas.Box(Math.round(r.x()), Math.round(r.y()), Math.round(r.w()), Math.round(r.h()));
    }

    private static void fill(TabletCanvas c, NativeSceneUi.Layer layer, int color) {
        c.fill(box(layer.rect()), Math.round(layer.alpha() * 255) << 24 | (color & 0xffffff));
    }

    private static String label(int index) {
        if (labels == null)
            try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(
                    ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/cruise-phone-text.json"))) {
                labels = JsonParser.parseReader(reader).getAsJsonArray();
            }
            catch (Exception failure) {
                labels = new JsonArray();
            }
        if (labels.size() <= index)
            return "";
        return labels.get(index).getAsJsonObject().get(locale.startsWith("zh") ? "zh_Hans" : "english").getAsString()
                .toUpperCase(Locale.ROOT);
    }

    public static void reset() {
        if (location != null)
            Minecraft.getInstance().getTextureManager().release(location);
        else if (surface != null)
            surface.close();
        surface = null;
        location = null;
        labels = null;
        reward = locale = "";
        frame = -1;
    }
}
