package gtsm.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import gtsm.Config;
import gtsm.network.GTSM_Network;
import gtsm.network.PacketRangeChange;
import gtsm.tile.TileEntityStorageManager;
import gtsm.client.RangeClientData;
import org.lwjgl.opengl.GL11;

import static gregapi.data.CS.*;

/**
 * 范围控制 GUI 的客户端界面：6 个偏移控制 + 半径 + 两个开关。
 * 数值以服务端同步回来的缓存为准（{@link RangeClientData}）。
 *
 * 布局（相对 guiLeft/guiTop，面板 176x165）：
 *   标题 / 副标题
 *   X/Y/Z/R 四行：右对齐标签 + 减钮 + 数值框 + 加钮
 *   分隔线
 *   两个整行开关按钮（Custom Range / Show Frame）
 *   分隔线 + Effective Range（Min/Max 两行）
 */
@SideOnly(Side.CLIENT)
public final class GuiContainer_GTSM extends GuiContainer {
    private final Container_GTSM mContainer;
    private RangeClientData.Info mData;

    // ---- 布局常量（绘制与命中检测共用） ----
    private static final int COL_LABEL_RIGHT = 74;   // 标签右对齐边缘
    private static final int BTN_MINUS_X = 78,  BTN_W = 20;
    private static final int VAL_X = 102,        VAL_W = 38;
    private static final int BTN_PLUS_X = 144,  BTN_PLUS_W = 20;
    private static final int ROW_H = 18;
    private static final int ROW_X = 30, ROW_Y = 48, ROW_Z = 66, ROW_R = 84;
    private static final int ROW_T_RANGE = 108, ROW_T_FRAME = 126;
    private static final int TOGGLE_X = 14, TOGGLE_W = 148;
    private static final int SEP1_Y = 100, SEP2_Y = 142;
    private static final int MIN_Y = 147, MAX_Y = 157;

    // ---- 配色 ----
    private static final int C_PANEL      = 0xF00C0C10; // 半透明深色面板
    private static final int C_BORDER     = 0xFF4A6B8A; // 面板边框（冷蓝灰）
    private static final int C_TITLE      = 0xFFFFFFFF;
    private static final int C_SUBTITLE   = 0xFF9AA5B1;
    private static final int C_LABEL      = 0xFFC9D1D9;
    private static final int C_VALUE      = 0xFF33FFCC; // 数值：亮青绿
    private static final int C_BUTTON     = 0xFF23262B; // 按钮底
    private static final int C_BUTTON_HI  = 0xFF3A4048; // 按钮高光边
    private static final int C_BUTTON_TXT = 0xFFFFFFFF;
    private static final int C_ON         = 0xFF1B5E20; // 开关：开
    private static final int C_OFF        = 0xFF4E342E; // 开关：关
    private static final int C_LED_ON     = 0xFF55FF77;
    private static final int C_LED_OFF    = 0xFF8A8A8A;
    private static final int C_RANGETXT   = 0xFF8899AA;

    public GuiContainer_GTSM(Container_GTSM aContainer) {
        super(aContainer);
        mContainer = aContainer;
        xSize = 176;
        ySize = 165;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float aPartialTicks, int aMouseX, int aMouseY) {
        TileEntityStorageManager tTE = mContainer.mTE;
        mData = RangeClientData.get(tTE.xCoord, tTE.yCoord, tTE.zCoord);

        int tOX = mData != null ? mData.offsetX : 0;
        int tOY = mData != null ? mData.offsetY : 0;
        int tOZ = mData != null ? mData.offsetZ : 0;
        int tRadius = mData != null ? mData.radius : Config.scanRadius;
        boolean tRangeOn = mData != null ? mData.rangeEnabled : T;
        boolean tFrameOn = mData != null ? mData.showFrame : T;
        int tEffR = tRangeOn ? tRadius : Config.scanRadius;

        int L = guiLeft, Tp = guiTop;
        FontRenderer tFont = fontRendererObj;

        // ---- 半透明面板 + 边框（不再使用网格背景图） ----
        GL11.glEnable(GL11.GL_BLEND);
        drawRect(L, Tp, L + xSize, Tp + ySize, C_PANEL);
        GL11.glDisable(GL11.GL_BLEND);
        // 上/左亮边、下右暗边，制造浮起感
        drawRect(L, Tp, L + xSize, Tp + 1, C_BORDER);
        drawRect(L, Tp, L + 1, Tp + ySize, C_BORDER);
        drawRect(L, Tp + ySize - 1, L + xSize, Tp + ySize, 0x80000000);
        drawRect(L + xSize - 1, Tp, L + xSize, Tp + ySize, 0x80000000);

        // ---- 标题 ----
        tFont.drawStringWithShadow("Storage Manager", L + 10, Tp + 7, C_TITLE);
        tFont.drawStringWithShadow("Range Configuration", L + 10, Tp + 17, C_SUBTITLE);

        // ---- 参数行 ----
        paramRow(tFont, "X Offset", tOX, L, Tp + ROW_X);
        paramRow(tFont, "Y Offset", tOY, L, Tp + ROW_Y);
        paramRow(tFont, "Z Offset", tOZ, L, Tp + ROW_Z);
        paramRow(tFont, "Radius",   tRadius, L, Tp + ROW_R);

        // ---- 分隔线 1 ----
        drawRect(L + 8, Tp + SEP1_Y, L + xSize - 8, Tp + SEP1_Y + 1, 0x44FFFFFF);

        // ---- 开关行 ----
        toggleRow(tFont, "Custom Range", tRangeOn, L + TOGGLE_X, Tp + ROW_T_RANGE, TOGGLE_W);
        toggleRow(tFont, "Show Frame",  tFrameOn, L + TOGGLE_X, Tp + ROW_T_FRAME, TOGGLE_W);

        // ---- 分隔线 2 ----
        drawRect(L + 8, Tp + SEP2_Y, L + xSize - 8, Tp + SEP2_Y + 1, 0x44FFFFFF);

        // ---- Effective Range（Min/Max 分行，等宽展示） ----
        tFont.drawStringWithShadow("Effective Range:", L + 10, Tp + MIN_Y - 9, C_SUBTITLE);
        tFont.drawStringWithShadow("Min  " + (tTE.xCoord + (tRangeOn ? tOX : 0) - tEffR) + ", " + (tTE.yCoord + (tRangeOn ? tOY : 0) - tEffR) + ", " + (tTE.zCoord + (tRangeOn ? tOZ : 0) - tEffR), L + 10, Tp + MIN_Y, C_RANGETXT);
        tFont.drawStringWithShadow("Max  " + (tTE.xCoord + (tRangeOn ? tOX : 0) + tEffR) + ", " + (tTE.yCoord + (tRangeOn ? tOY : 0) + tEffR) + ", " + (tTE.zCoord + (tRangeOn ? tOZ : 0) + tEffR), L + 10, Tp + MAX_Y, C_RANGETXT);
    }

    /** 一行参数：右对齐标签 + 减钮 + 数值框 + 加钮 */
    private void paramRow(FontRenderer aFont, String aLabel, int aValue, int aL, int aRowY) {
        aFont.drawStringWithShadow(aLabel, aL + COL_LABEL_RIGHT - aFont.getStringWidth(aLabel), aRowY + 5, C_LABEL);
        stepButton(aL + BTN_MINUS_X, aRowY, "-");
        valueBox(aFont, Integer.toString(aValue), aL + VAL_X, aRowY);
        stepButton(aL + BTN_PLUS_X, aRowY, "+");
    }

    private void stepButton(int aX, int aY, String aText) {
        drawRect(aX, aY, aX + BTN_W, aY + ROW_H, C_BUTTON);
        drawRect(aX, aY, aX + BTN_W, aY + 1, C_BUTTON_HI);
        drawRect(aX, aY, aX + 1, aY + ROW_H, C_BUTTON_HI);
        fontRendererObj.drawStringWithShadow(aText, aX + BTN_W / 2 - 3, aY + 5, C_BUTTON_TXT);
    }

    private void valueBox(FontRenderer aFont, String aText, int aX, int aY) {
        drawRect(aX, aY, aX + VAL_W, aY + ROW_H, 0xFF101418);
        drawRect(aX, aY, aX + VAL_W, aY + 1, C_BUTTON_HI);
        aFont.drawStringWithShadow(aText, aX + VAL_W / 2 - aFont.getStringWidth(aText) / 2, aY + 5, C_VALUE);
    }

    /** 整行开关：底色区分开关态 + 左侧 LED + 文字 */
    private void toggleRow(FontRenderer aFont, String aLabel, boolean aOn, int aX, int aY, int aW) {
        drawRect(aX, aY, aX + aW, aY + ROW_H, aOn ? C_ON : C_OFF);
        drawRect(aX, aY, aX + aW, aY + 1, C_BUTTON_HI);
        drawRect(aX, aY, aX + 1, aY + ROW_H, C_BUTTON_HI);
        // LED
        drawRect(aX + 7, aY + 5, aX + 15, aY + 13, aOn ? C_LED_ON : C_LED_OFF);
        aFont.drawStringWithShadow(aLabel + ": " + (aOn ? "ON" : "OFF"), aX + 22, aY + 5, 0xFFFFFFFF);
    }

    @Override
    protected void mouseClicked(int aX, int aY, int aButton) {
        super.mouseClicked(aX, aY, aButton);
        int rx = aX - guiLeft, ry = aY - guiTop;
        TileEntityStorageManager tTE = mContainer.mTE;
        if (mData == null) mData = RangeClientData.get(tTE.xCoord, tTE.yCoord, tTE.zCoord);
        int tOX = mData != null ? mData.offsetX : 0;
        int tOY = mData != null ? mData.offsetY : 0;
        int tOZ = mData != null ? mData.offsetZ : 0;
        int tRadius = mData != null ? mData.radius : Config.scanRadius;

        byte tId = -1;
        int tValue = 0;
        if (hit(rx, ry, BTN_MINUS_X, ROW_X, BTN_W)) { tId = TileEntityStorageManager.GUI_OFF_X_DEC; tValue = tOX - 1; }
        else if (hit(rx, ry, BTN_PLUS_X, ROW_X, BTN_PLUS_W)) { tId = TileEntityStorageManager.GUI_OFF_X_INC; tValue = tOX + 1; }
        else if (hit(rx, ry, BTN_MINUS_X, ROW_Y, BTN_W)) { tId = TileEntityStorageManager.GUI_OFF_Y_DEC; tValue = tOY - 1; }
        else if (hit(rx, ry, BTN_PLUS_X, ROW_Y, BTN_PLUS_W)) { tId = TileEntityStorageManager.GUI_OFF_Y_INC; tValue = tOY + 1; }
        else if (hit(rx, ry, BTN_MINUS_X, ROW_Z, BTN_W)) { tId = TileEntityStorageManager.GUI_OFF_Z_DEC; tValue = tOZ - 1; }
        else if (hit(rx, ry, BTN_PLUS_X, ROW_Z, BTN_PLUS_W)) { tId = TileEntityStorageManager.GUI_OFF_Z_INC; tValue = tOZ + 1; }
        else if (hit(rx, ry, BTN_MINUS_X, ROW_R, BTN_W)) { tId = TileEntityStorageManager.GUI_RADIUS_DEC; tValue = tRadius - 1; }
        else if (hit(rx, ry, BTN_PLUS_X, ROW_R, BTN_PLUS_W)) { tId = TileEntityStorageManager.GUI_RADIUS_INC; tValue = tRadius + 1; }
        else if (hit(rx, ry, TOGGLE_X, ROW_T_RANGE, TOGGLE_W)) { tId = TileEntityStorageManager.GUI_TOGGLE_RANGE; tValue = (mData != null && mData.rangeEnabled) ? 0 : 1; }
        else if (hit(rx, ry, TOGGLE_X, ROW_T_FRAME, TOGGLE_W)) { tId = TileEntityStorageManager.GUI_TOGGLE_FRAME; tValue = (mData != null && mData.showFrame) ? 0 : 1; }
        else return;

        GTSM_Network.WRAPPER.sendToServer(new PacketRangeChange(tTE.xCoord, tTE.yCoord, tTE.zCoord, tId, tValue));

        // 本地即时预测（服务端同步包 ~1 tick 后到达，保持手感）
        if (mData != null) {
            RangeClientData.put(tTE.xCoord, tTE.yCoord, tTE.zCoord,
                tId == TileEntityStorageManager.GUI_OFF_X_DEC || tId == TileEntityStorageManager.GUI_OFF_X_INC ? tValue : mData.offsetX,
                tId == TileEntityStorageManager.GUI_OFF_Y_DEC || tId == TileEntityStorageManager.GUI_OFF_Y_INC ? tValue : mData.offsetY,
                tId == TileEntityStorageManager.GUI_OFF_Z_DEC || tId == TileEntityStorageManager.GUI_OFF_Z_INC ? tValue : mData.offsetZ,
                tId == TileEntityStorageManager.GUI_RADIUS_DEC || tId == TileEntityStorageManager.GUI_RADIUS_INC ? tValue : mData.radius,
                tId == TileEntityStorageManager.GUI_TOGGLE_RANGE ? tValue != 0 : mData.rangeEnabled,
                tId == TileEntityStorageManager.GUI_TOGGLE_FRAME ? tValue != 0 : mData.showFrame);
        }
    }

    private boolean hit(int aRX, int aRY, int aBX, int aBY, int aBW) {
        return aRX >= aBX && aRX <= aBX + aBW && aRY >= aBY && aRY <= aBY + ROW_H;
    }
}
