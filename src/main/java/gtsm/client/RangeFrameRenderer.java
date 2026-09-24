package gtsm.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/**
 * 在「视野内」的管理器位置绘制生效范围线框（火柴盒）。
 * 数据来自 {@link RangeClientData}（服务端每 10 tick 同步一次 64 格内的管理器）。
 */
@SideOnly(Side.CLIENT)
public final class RangeFrameRenderer {
    private static final double RENDER_DISTANCE_SQ = 64.0D * 64.0D;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent aEvent) {
        Entity rv = Minecraft.getMinecraft().renderViewEntity;
        if (rv == null) return;
        // 直接用实体当前坐标（不做插值），线框随视角平移的 1 格内抖动在可接受范围内
        double rpX = rv.posX;
        double rpY = rv.posY;
        double rpZ = rv.posZ;

        for (RangeClientData.Info tInfo : RangeClientData.all()) {
            if (!tInfo.showFrame) continue;
            double cX = tInfo.x + tInfo.offsetX + 0.5D, cY = tInfo.y + tInfo.offsetY + 0.5D, cZ = tInfo.z + tInfo.offsetZ + 0.5D;
            if ((cX-rpX)*(cX-rpX) + (cY-rpY)*(cY-rpY) + (cZ-rpZ)*(cZ-rpZ) > RENDER_DISTANCE_SQ) continue;

            double x1 = tInfo.x + tInfo.offsetX - tInfo.radius - rpX;
            double y1 = tInfo.y + tInfo.offsetY - tInfo.radius - rpY;
            double z1 = tInfo.z + tInfo.offsetZ - tInfo.radius - rpZ;
            double x2 = tInfo.x + tInfo.offsetX + tInfo.radius + 1 - rpX;
            double y2 = tInfo.y + tInfo.offsetY + tInfo.radius + 1 - rpY;
            double z2 = tInfo.z + tInfo.offsetZ + tInfo.radius + 1 - rpZ;

            float r, g, b, a;
            if (tInfo.rangeEnabled) { r = 0.15F; g = 1.00F; b = 0.45F; a = 0.85F; }
            else { r = 0.55F; g = 0.55F; b = 0.55F; a = 0.55F; }

            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(1.6F);
            GL11.glColor4f(r, g, b, a);

            net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.instance;
            t.startDrawing(GL11.GL_LINES);
            // 12 条棱
            edge(t, x1, y1, z1, x2, y1, z1);
            edge(t, x1, y2, z1, x2, y2, z1);
            edge(t, x1, y1, z2, x2, y1, z2);
            edge(t, x1, y2, z2, x2, y2, z2);
            edge(t, x1, y1, z1, x1, y2, z1);
            edge(t, x2, y1, z1, x2, y2, z1);
            edge(t, x1, y1, z2, x1, y2, z2);
            edge(t, x2, y1, z2, x2, y2, z2);
            edge(t, x1, y1, z1, x1, y1, z2);
            edge(t, x2, y1, z1, x2, y1, z2);
            edge(t, x1, y2, z1, x1, y2, z2);
            edge(t, x2, y2, z1, x2, y2, z2);
            t.draw();

            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }
    }

    private static void edge(net.minecraft.client.renderer.Tessellator aT, double aX1, double aY1, double aZ1, double aX2, double aY2, double aZ2) {
        aT.addVertex(aX1, aY1, aZ1);
        aT.addVertex(aX2, aY2, aZ2);
    }
}
