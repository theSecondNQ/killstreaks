package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.TacticalRuntime;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Samples the final screen of a real server-authorized cruise launch. Excluded from release JARs. */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class ForgeCruiseRenderSmoke {
    private static final int[] SAMPLES = { 0, 10, 20, 30, 40, 50, 56 };
    private static final List<String> RESULTS = new ArrayList<>();
    private static boolean deployed, entered, complete, rendered, missingBody;
    private static int next;
    private static long started;
    private static float renderedTime;

    private ForgeCruiseRenderSmoke() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void renderedWorld(RenderLevelStageEvent event) {
        if (Boolean.getBoolean("killstreakcod.cruiseSmoke")
                && event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            rendered = true;
            renderedTime = StreakPresentation.elapsed();
        }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) throws Exception {
        if (!Boolean.getBoolean("killstreakcod.cruiseSmoke") || complete)
            return;
        if (event.phase == TickEvent.Phase.START) {
            rendered = false;
            return;
        }
        var mc = Minecraft.getInstance();
        if (!rendered || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null)
            return;
        if (!deployed) {
            deployed = true;
            started = System.nanoTime();
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                player.serverLevel().setDayTime(6000);
                player.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
                TacticalRuntime.deploy(player, StreakReward.CRUISE_MISSILE, false);
            });
        }
        if (System.nanoTime() - started > 90_000_000_000L)
            throw new IllegalStateException("Cruise launch did not complete: " + RESULTS);
        if (EntryScenes.active() && StreakPresentation.currentReward().equals("cruise_missile")) {
            entered = true;
            while (next < SAMPLES.length && renderedTime >= SAMPLES[next]) {
                int sample = SAMPLES[next++];
                var directory = mc.gameDirectory.toPath().resolve("cruise-smoke");
                Files.createDirectories(directory);
                int bodyPixels = 0;
                try (ThermalRenderState ignored = new ThermalRenderState();
                        var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(directory.resolve("cruise-" + sample + ".png"));
                    // Neutral, dark body pixels in the central actor area exclude grass, sky and HUD text.
                    for (int y = image.getHeight() / 5; y < image.getHeight() * 7 / 10; y++) {
                        for (int x = image.getWidth() * 3 / 10; x < image.getWidth() * 7 / 10; x++) {
                            int rgba = image.getPixelRGBA(x, y);
                            int r = rgba & 255, g = rgba >> 8 & 255, b = rgba >> 16 & 255;
                            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
                            if (max < 110 && max - min < 24)
                                bodyPixels++;
                        }
                    }
                }
                if ((sample == 10 || sample == 20) && bodyPixels < 500
                        || sample == 30 && bodyPixels < 200 || sample == 40 && bodyPixels < 50)
                    missingBody = true;
                RESULTS.add("{\"tick\":" + sample + ",\"sampledAt\":" + renderedTime
                        + ",\"bodyPixels\":" + bodyPixels + "}");
            }
        } else if (entered) {
            String result = "{\"shaders\":" + OptionalShaderCompat.shaderPackActive()
                    + ",\"samples\":[" + String.join(",", RESULTS) + "]}\n";
            Files.writeString(mc.gameDirectory.toPath().resolve("cruise-smoke.json"), result);
            if (next != SAMPLES.length || missingBody)
                throw new IllegalStateException("Cruise body disappeared during separation: " + result);
            complete = true;
            mc.stop();
        }
    }
}
