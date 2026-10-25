package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** 编辑入口同时支持普通视角、遥控 HUD 与本模组的菜单；不接管其他模组的绘制。 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class UiLayoutClient {
    private UiLayoutClient() {}

    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        var mc = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS && mc.player != null && !(mc.screen instanceof UiLayoutEditorScreen)
                && KillstreakKeyMappings.EDIT_UI.isConflictContextAndModifierActive()
                && KillstreakKeyMappings.EDIT_UI.matches(event.getKey(), event.getScanCode()))
            mc.setScreen(new UiLayoutEditorScreen(mc.screen));
    }
}
