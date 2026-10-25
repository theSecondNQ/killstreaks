package net.killstreakcod.render;

import java.util.function.Supplier;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;

/** 1.20.1 preview with the selected local texture and the correct wide/slim geometry. */
public final class PlayerSkinWidget extends AbstractWidget {
    private final Supplier<PlayerSkin> skin;
    private final PlayerModel<AbstractClientPlayer> wide, slim;

    public PlayerSkinWidget(int width, int height, EntityModelSet models, Supplier<PlayerSkin> skin) {
        super(0, 0, width, height, Component.translatable("screen.killstreakcod.skins_title"));
        this.skin = skin;
        wide = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        PlayerSkin selected = skin.get();
        PlayerModel<AbstractClientPlayer> model = selected.model() == PlayerSkin.Model.SLIM ? slim : wide;
        // 预览始终使用成年玩家比例，避免 AgeableListModel 默认幼体缩放造成大头、小身体。
        model.young = false;
        model.riding = false;
        model.attackTime = 0;
        var player = Minecraft.getInstance().player;
        if (player != null)
            model.setupAnim(player, 0, 0, 0, 0, 0);
        var pose = graphics.pose();
        graphics.flush();
        Lighting.setupForEntityInInventory();
        RenderSystem.enableDepthTest();
        pose.pushPose();
        try {
            // 模型顶点已换算为世界单位；按约两格身高适配预览区域，避免再次按像素缩小。
            float scale = Math.min((width - 12) / 1.1F, (height - 12) / 2F);
            pose.translate(getX() + width / 2F, getY() + height / 4F, 150);
            pose.scale(scale, scale, -scale);
            pose.mulPose(Axis.YP.rotationDegrees(
                    25 + net.minecraft.util.Mth.clamp((mouseX - getX() - width / 2F) * .15F, -20, 20)));
            model.renderToBuffer(pose,
                    graphics.bufferSource().getBuffer(RenderType.entityTranslucent(selected.texture())), 15728880,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            graphics.flush();
        } finally {
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
