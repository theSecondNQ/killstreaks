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
        // 清理任务属于重载的 apply 阶段，重载完成前必须等待清理结束，避免下一帧仍使用旧缓存。
        event.registerReloadListener((barrier, resources, prepareProfiler, applyProfiler, prepareExecutor,
                applyExecutor) -> barrier.wait(null).thenRunAsync(ClientResourceReload::invalidate, applyExecutor));
    }

    private static void invalidate() {
        RenderSystem.assertOnRenderThread();
        net.killstreakcod.render.compat.OptionalMapTiles.clear();
        ImportedUnits.clearCache();
        ImportedCastModel.clearCache();
        OriginalStreakAnimations.clearCache();
        TargetingDeviceAnimations.reload();

        OriginalRewardUi.reload();
        NukeUiRenderer.reload();
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
