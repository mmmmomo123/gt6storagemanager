package gtsm.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

/**
 * 生效范围「火柴盒」渲染器（TESR 版）。
 *
 * 为什么不用 RenderWorldLastEvent：该事件在 GTNH 的 Angelica/Hodgepodge 渲染管线里
 * 模型视图语义会变，绝对/相对坐标都不稳。TESR 由原版调度器调用，
 * renderTileEntityAt 收到的 (x,y,z) 就是「Tile 坐标 - 相机坐标」的相机相对值，
 * 在任何渲染管线下都是稳定契约；x/y/z 为 0 的旧版签名由编译器保留兼容，实际使用四参版本。
 */
@SideOnly(Side.CLIENT)
public final class RangeFrameRenderer extends TileEntitySpecialRenderer {
    public static final RangeFrameRenderer INSTANCE = new RangeFrameRenderer();

    @Override
    public void renderTileEntityAt(TileEntity aTileEntity, double aX, double aY, double aZ, float aPartialTicks) {
        if (!(aTileEntity instanceof gtsm.tile.TileEntityStorageManager)) return;
        gtsm.tile.TileEntityStorageManager tTE = (gtsm.tile.TileEntityStorageManager) aTileEntity;

        RangeClientData.Info tInfo = RangeClientData.get(tTE.xCoord, tTE.yCoord, tTE.zCoord);
        boolean tShow = tInfo != null ? tInfo.showFrame : tTE.showFrame();
        if (!tShow) return;
        int tOX = tInfo != null ? tInfo.offsetX : tTE.offsetX();
        int tOY = tInfo != null ? tInfo.offsetY : tTE.offsetY();
        int tOZ = tInfo != null ? tInfo.offsetZ : tTE.offsetZ();
        int tRadius = tInfo != null ? tInfo.radius : tTE.radius();
        boolean tEnabled = tInfo != null ? tInfo.rangeEnabled : tTE.rangeEnabled();

        // 相机相对坐标：以 TESR 传入位置为 Tile 原点，偏移到盒角
        double x1 = aX + tOX - tRadius;
        double y1 = aY + tOY - tRadius;
        double z1 = aZ + tOZ - tRadius;
        double x2 = aX + tOX + tRadius + 1;
        double y2 = aY + tOY + tRadius + 1;
        double z2 = aZ + tOZ + tRadius + 1;

        float r, g, b, a;
        if (tEnabled) { r = 0.30F; g = 1.00F; b = 0.65F; a = 0.9F; }
        else { r = 0.55F; g = 0.58F; b = 0.62F; a = 0.55F; }

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(1.6F);
        GL11.glColor4f(r, g, b, a);

        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
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

    private static void edge(Tessellator aT, double aX1, double aY1, double aZ1, double aX2, double aY2, double aZ2) {
        aT.addVertex(aX1, aY1, aZ1);
        aT.addVertex(aX2, aY2, aZ2);
    }
}
