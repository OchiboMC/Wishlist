package net.ochibo.wishlist.client.ui.independent;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Loader hooks call this after GameRenderer has finished the normal GUI. */
public final class IndependentUIRenderer {
    private IndependentUIRenderer() {}

    public static void render(float partialTick) {
        IndependentUIManager manager = IndependentUIManager.getInstance();
        if (!manager.hasVisibleUi()) return;
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        int width = window.getGuiScaledWidth(), height = window.getGuiScaledHeight();
        if (width <= 0 || height <= 0 || window.getWidth() <= 0 || window.getHeight() <= 0) return;
        int mouseX = (int) (mc.mouseHandler.xpos() * width / window.getScreenWidth());
        int mouseY = (int) (mc.mouseHandler.ypos() * height / window.getScreenHeight());
        Screen screen = mc.screen;
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        float[] color = RenderSystem.getShaderColor().clone();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int[] viewport = new int[4], scissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (scissor) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            modelView.translate(0, 0, -10000);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, 1000, 21000),
                    VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
            manager.drawVisible(ui -> {
                RenderSystem.disableScissor();
                RenderSystem.depthMask(true);
                // Previously rendered Screen/tooltips must not occlude this layer through depth.
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                try {
                    ui.render(graphics, screen, mouseX, mouseY, partialTick);
                } finally {
                    // Submit while this layer's drawing context and clipping are still active.
                    graphics.flush();
                    RenderSystem.disableScissor();
                }
            });
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.depthMask(depthMask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            else RenderSystem.disableScissor();
        }
    }
}
