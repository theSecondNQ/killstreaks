package net.killstreakcod.render;

import net.killstreakcod.mixin.client.BlendModeAccess;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Restore both tracked Blaze3D state and the framebuffer/program bindings used by Iris.
 * 同时恢复 Blaze3D 跟踪的状态，以及 Iris 使用的帧缓冲和着色程序绑定。
 */
public final class ThermalRenderState implements AutoCloseable {
    private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
            draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
            array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
            depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE),
            scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), offset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
    private final float offsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR),
            offsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
            dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
            dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
            equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final int[] viewport = new int[4], textures = new int[2];
    private final float[] clearColor = new float[4];
    private final byte[] colorMask = new byte[4];
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
    private final Matrix4f textureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
    private final VertexSorting sorting = RenderSystem.getVertexSorting();
    private final ShaderInstance shader = RenderSystem.getShader();
    private final BlendMode blendMode = BlendModeAccess.killstreak$getLastApplied();

    ThermalRenderState() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            mask.get(colorMask);
        }
        for (int i = 0; i < textures.length; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }

        // 帧缓冲构造器会在当前纹理单元上绑定纹理；本模组所有分配和释放都固定在零号单元，因为 Iris 可能留下更高编号单元为活动状态。
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    @Override
    public void close() {
        RenderSystem.setProjectionMatrix(projection, sorting);
        RenderSystem.setTextureMatrix(textureMatrix);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        RenderSystem.setShader(() -> shader);
        GlStateManager._glUseProgram(program);
        if (depth)
            RenderSystem.enableDepthTest();
        else
            RenderSystem.disableDepthTest();
        if (stencil)
            GL11.glEnable(GL11.GL_STENCIL_TEST);
        else
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        if (blend)
            RenderSystem.enableBlend();
        else
            RenderSystem.disableBlend();
        if (cull)
            RenderSystem.enableCull();
        else
            RenderSystem.disableCull();
        if (scissor)
            GlStateManager._enableScissorTest();
        else
            RenderSystem.disableScissor();
        if (offset)
            RenderSystem.enablePolygonOffset();
        else
            RenderSystem.disablePolygonOffset();
        RenderSystem.polygonOffset(offsetFactor, offsetUnits);
        RenderSystem.depthFunc(depthFunc);
        RenderSystem.depthMask(depthWrite);
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        // Otherwise a later core shader can overwrite the GUI vignette's custom blend factors.
        BlendModeAccess.killstreak$setLastApplied(blendMode);
        RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
        RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        GlStateManager._clearDepth(clearDepth);
        for (int i = 0; i < textures.length; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(textures[i]);
        }
        GlStateManager._activeTexture(activeTexture);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        // 恢复顶点数组对象绑定时，还必须使 BufferUploader 缓存的即时缓冲失效。
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, array);
    }
}
