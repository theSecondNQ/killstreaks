package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

/** Opt-in startup check in an isolated game directory; excluded from the release JAR. */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class ForgeClientSmoke {
    private static int ticks;
    private static boolean complete;

    private ForgeClientSmoke() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (!Boolean.getBoolean("killstreakcod.clientSmoke") || event.phase != TickEvent.Phase.END || complete)
            return;
        Minecraft minecraft = Minecraft.getInstance();
        GLFW.glfwHideWindow(minecraft.getWindow().getWindow());
        if (minecraft.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
                && minecraft.getOverlay() == null) {
            minecraft.setScreen(new TitleScreen());
            return;
        }
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null || ++ticks < 10)
            return;
        if (Files.exists(minecraft.gameDirectory.toPath().resolve("resource-smoke.enabled"))
                && !ForgeResourceReloadSmoke.check(minecraft)) {
            return;
        }
        var models = minecraft.getModelManager();
        var terminal = models.getModel(new ModelResourceLocation(
                ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "headquarters_terminal"), "inventory"));
        if (terminal == models.getMissingModel())
            throw new IllegalStateException("Headquarters terminal model did not bake");
        if (terminal.getQuads(null, null, net.minecraft.util.RandomSource.create()).isEmpty())
            throw new IllegalStateException("Headquarters terminal baked without visible faces");
        shader(ThermalVision.class, "maskShader");
        shader(ThermalVision.class, "compositeShader");
        shader(TracerEffects.class, "shader");
        shader(NativeFxRenderer.class, "shader");
        shader(NativeFxRenderer.class, "glowShader");
        int imported = 0;
        try (var resources = minecraft.getResourceManager().listResources("mw/models", path -> path.getPath().endsWith(".mesh.gz"))
                .keySet().stream()) {
            for (ResourceLocation path : resources.toList()) {
                String id = path.getPath().substring("mw/models/".length(), path.getPath().length() - ".mesh.gz".length());
                if (ImportedCastModel.get(id) == null)
                    throw new IllegalStateException("Imported mesh did not decode: " + path);
                imported++;
                ImportedCastModel.clearCache();
            }
        }
        if (imported == 0)
            throw new IllegalStateException("No imported models found");
        if (Files.exists(minecraft.gameDirectory.toPath().resolve("earned-ui-smoke.enabled")))
            ForgeEarnedUiSmoke.check(minecraft);
        if (Files.exists(minecraft.gameDirectory.toPath().resolve("nuke-ui-smoke.enabled")))
            ForgeNukeUiSmoke.check(minecraft);
        Path report = minecraft.gameDirectory.toPath().resolve("client-smoke.json");
        Files.writeString(report, "{\"titleScreen\":true,\"terminalModel\":true,\"shaders\":5,\"importedModels\":" + imported + "}\n");
        complete = true;
        minecraft.stop();
    }

    private static void shader(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        if (field.get(null) == null)
            throw new IllegalStateException("Shader did not compile: " + owner.getSimpleName() + "." + name);
    }
}
