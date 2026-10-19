package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.DroneDeploymentSounds;
import net.killstreakcod.audio.NativeMinigunSounds;
import net.killstreakcod.audio.NativeRadioClient;
import net.killstreakcod.audio.NativeRadioSubtitles;
import net.killstreakcod.render.tablet.ScenePhoneScreen;
import net.killstreakcod.render.tablet.SceneTabletScreen;
import net.killstreakcod.render.tablet.TabletCanvas;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

/**
 * Owns client resource invalidation order, including native images, dynamic textures and GPU buffers.
 * 统一管理客户端资源失效顺序，包括原图、动态纹理及 GPU 缓冲；全部清理在渲染线程执行。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientResourceReload {
    private ClientResourceReload() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(OperatorSkins::initialize);
    }

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            if (RenderSystem.isOnRenderThread())
                invalidate();
            else
                RenderSystem.recordRenderCall(ClientResourceReload::invalidate);
        });
    }

    private static void invalidate() {
        ImportedUnits.clearCache();
        ImportedCastModel.clearCache();
        OriginalStreakAnimations.clearCache();
        TargetingDeviceAnimations.reload();

        OriginalRewardUi.reload();
        NativeRemoteHud.reload();
        NativeRadioSubtitles.reload();
        NativeSceneUi.reset();
        TabletCanvas.reset();
        SceneTabletScreen.reset();
        ScenePhoneScreen.reset();
        NativeCruiseHud.reset();
        SceneEntryRenderer.reset();
        NativePrecisionScopeHud.reload();
        OperatorSkins.invalidateTextures();

        ThermalVision.invalidate();
        TracerEffects.clear();
        NativeFxRenderer.reload();
        NativeRadioClient.clear();
        NativeMinigunSounds.clear();
        DroneDeploymentSounds.stop();
    }
}
