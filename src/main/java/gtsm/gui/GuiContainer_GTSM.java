package gtsm.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import gtsm.Config;
import gtsm.network.GTSM_Network;
import gtsm.network.PacketRangeChange;
import gtsm.tile.TileEntityStorageManager;
import gtsm.client.RangeClientData;

import static gregapi.data.CS.*;

/**
 * 范围控制 GUI（客户端）。数值以服务端同步缓存 {@link RangeClientData} 为准。
 *
 * 布局（面板 176x200，圆角深色卡片）：
 *   顶部：主标题 储物管理器 + 副标题 范围配置
 *   中部：X/Y/Z/半径 四行表单（右对齐标签 + 胶囊步进器 [－][值][＋]）
 *   下部：两个拨动开关（自定义范围 / 显示边框）
 *   底部：实际生效范围 代码块（最小/最大 两行）
 *
 * 坐标约定：绘制用绝对坐标（guiLeft/guiTop + 相对偏移）；
 * 命中检测一律用【相对坐标】（鼠标 - guiLeft/guiTop 后与同一组相对偏移比较），
 * 绘制与命中共用同一套行偏移常量 ROW_*，保证悬停高亮与点击落点完全重合。
 */
@SideOnly(Side.CLIENT)
public final class GuiContainer_GTSM extends GuiContainer {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("gtsm", "textures/gui/range.png");

    private final Container_GTSM mContainer;
    private RangeClientData.Info mData;

    // ---- 布局常量（绘制与命中检测共用，全部为面板内相对坐标） ----
    private static final int ROW_H = 18;
    private static final int LABEL_R = 92;                    // 标签右对齐边缘（相对）
    private static final int STEP_X = 98, STEP_W = 64;        // 步进器整体（相对）
    private static final int MINUS_X = STEP_X,      MINUS_W = 20;
    private static final int VALUE_X = STEP_X + 20, VALUE_W = 20;
    private static final int PLUS_X  = STEP_X + 40, PLUS_W  = 20;
    private static final int ROW_X = 42, ROW_Y = 62, ROW_Z = 82, ROW_R = 102;
    private static final int TOGGLE_Y_RANGE = 126, TOGGLE_Y_FRAME = 148;
    private static final int SWITCH_X = 130, SWITCH_W = 30, SWITCH_H = 14;
    private static final int BLOCK_X = 14, BLOCK_Y = 170, BLOCK_W = 148, BLOCK_H = 26;

    // ---- 配色（现代暗色系） ----
    private static final int C_TITLE     = 0xFFFFFFFF;
    private static final int C_SUBTITLE  = 0xFF8A93A0;
    private static final int C_LABEL     = 0xFFD7DCE4;
    private static final int C_VALUE     = 0xFF7FE7D2; // 数值：柔和薄荷青
    private static final int C_BTN_TXT   = 0xFFC9CDD4;
    private static final int C_STEP_BG   = 0xFF2A2C34;
    private static final int C_STEP_TOP  = 0x18FFFFFF;
    private static final int C_STEP_DIV  = 0x14FFFFFF;
    private static final int C_STEP_HOVER= 0x36FFFFFF;
    private static final int C_PILL_ON   = 0xFF3FB96F;
    private static final int C_PILL_OFF  = 0xFF454852;
    private static final int C_KNOB      = 0xFFF2F5F7;
    private static final int C_BLOCK_BG  = 0x9912161D;
    private static final int C_BLOCK_ED  = 0x22FFFFFF;
    private static final int C_BLOCK_TXT = 0xFF9AA7B8;
    private static final int C_TAG       = 0xFF7FE7D2;

    public GuiContainer_GTSM(Container_GTSM aContainer) {
        super(aContainer);
        mContainer = aContainer;
        xSize = 176;
        ySize = 200;
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
        int mX = aMouseX - L, mY = aMouseY - Tp; // 面板内相对坐标
        FontRenderer tFont = fontRendererObj;

        // ---- 圆角深色面板（PNG 贴图，含透明圆角） ----
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(BACKGROUND);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        drawTexturedModalRect(L, Tp, 0, 0, xSize, ySize);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);

        // ---- 标题区 ----
        tFont.drawStringWithShadow("储物管理器", L + 14, Tp + 10, C_TITLE);
        tFont.drawStringWithShadow("范围配置", L + 14, Tp + 22, C_SUBTITLE);

        // ---- 参数表单（绘制+命中共用相对行偏移） ----
        paramRow(tFont, "X轴偏移", tOX, L, Tp, ROW_X, mX, mY);
        paramRow(tFont, "Y轴偏移", tOY, L, Tp, ROW_Y, mX, mY);
        paramRow(tFont, "Z轴偏移", tOZ, L, Tp, ROW_Z, mX, mY);
        paramRow(tFont, "半径",     tRadius, L, Tp, ROW_R, mX, mY);

        // ---- 拨动开关 ----
        toggleRow(tFont, "自定义范围", tRangeOn, L, Tp, TOGGLE_Y_RANGE, mX, mY);
        toggleRow(tFont, "显示边框",  tFrameOn, L, Tp, TOGGLE_Y_FRAME, mX, mY);

        // ---- 实际生效范围 代码块 ----
        drawRect(L + BLOCK_X, Tp + BLOCK_Y, L + BLOCK_X + BLOCK_W, Tp + BLOCK_Y + BLOCK_H, C_BLOCK_BG);
        drawRect(L + BLOCK_X, Tp + BLOCK_Y, L + BLOCK_X + BLOCK_W, Tp + BLOCK_Y + 1, C_BLOCK_ED);
        tFont.drawStringWithShadow("实际生效范围", L + BLOCK_X + 6, Tp + BLOCK_Y + 3, C_SUBTITLE);
        tFont.drawStringWithShadow("最小", L + BLOCK_X + 6, Tp + BLOCK_Y + 13, C_TAG);
        tFont.drawStringWithShadow("" + (tTE.xCoord + (tRangeOn ? tOX : 0) - tEffR) + ", " + (tTE.yCoord + (tRangeOn ? tOY : 0) - tEffR) + ", " + (tTE.zCoord + (tRangeOn ? tOZ : 0) - tEffR), L + BLOCK_X + 30, Tp + BLOCK_Y + 13, C_BLOCK_TXT);
        tFont.drawStringWithShadow("最大", L + BLOCK_X + 6, Tp + BLOCK_Y + 22, C_TAG);
        tFont.drawStringWithShadow("" + (tTE.xCoord + (tRangeOn ? tOX : 0) + tEffR) + ", " + (tTE.yCoord + (tRangeOn ? tOY : 0) + tEffR) + ", " + (tTE.zCoord + (tRangeOn ? tOZ : 0) + tEffR), L + BLOCK_X + 30, Tp + BLOCK_Y + 22, C_BLOCK_TXT);
    }

    /** 一行参数：右对齐标签 + 胶囊步进器（－ 值 ＋）。aRowY 为面板内相对坐标 */
    private void paramRow(FontRenderer aFont, String aLabel, int aValue, int aL, int aT, int aRowY, int aMX, int aMY) {
        int y = aT + aRowY;
        aFont.drawStringWithShadow(aLabel, aL + LABEL_R - aFont.getStringWidth(aLabel), y + 5, C_LABEL);

        // 胶囊容器
        drawRect(aL + STEP_X, y, aL + STEP_X + STEP_W, y + ROW_H, C_STEP_BG);
        drawRect(aL + STEP_X, y, aL + STEP_X + STEP_W, y + 1, C_STEP_TOP);
        // 分隔（低透明竖线，做出分区而非硬框）
        drawRect(aL + STEP_X + 19, y + 3, aL + STEP_X + 20, y + ROW_H - 3, C_STEP_DIV);
        drawRect(aL + STEP_X + 40, y + 3, aL + STEP_X + 41, y + ROW_H - 3, C_STEP_DIV);
        // 悬停高亮（与 mouseClicked 使用完全相同的相对坐标）
        if (inRect(aMX, aMY, MINUS_X, aRowY, MINUS_W, ROW_H)) drawRect(aL + MINUS_X, y, aL + MINUS_X + MINUS_W, y + ROW_H, C_STEP_HOVER);
        if (inRect(aMX, aMY, PLUS_X, aRowY, PLUS_W, ROW_H)) drawRect(aL + PLUS_X, y, aL + PLUS_X + PLUS_W, y + ROW_H, C_STEP_HOVER);

        String tVal = Integer.toString(aValue);
        aFont.drawStringWithShadow("－", aL + MINUS_X + MINUS_W / 2 - 2, y + 5, C_BTN_TXT);
        aFont.drawStringWithShadow(tVal, aL + VALUE_X + VALUE_W / 2 - aFont.getStringWidth(tVal) / 2, y + 5, C_VALUE);
        aFont.drawStringWithShadow("＋", aL + PLUS_X + PLUS_W / 2 - 2, y + 5, C_BTN_TXT);
    }

    /** 一行开关：左标签 + 右侧拨动 pill。aRowY 为面板内相对坐标 */
    private void toggleRow(FontRenderer aFont, String aLabel, boolean aOn, int aL, int aT, int aRowY, int aMX, int aMY) {
        int y = aT + aRowY;
        boolean tHover = inRect(aMX, aMY, 14, aRowY, SWITCH_X + SWITCH_W - 14, ROW_H);
        aFont.drawStringWithShadow(aLabel, aL + 14, y + 5, tHover ? C_TITLE : C_LABEL);

        int pX = aL + SWITCH_X, pY = y + 2;
        drawRect(pX, pY, pX + SWITCH_W, pY + SWITCH_H, aOn ? C_PILL_ON : C_PILL_OFF);
        drawRect(pX, pY, pX + SWITCH_W, pY + 1, 0x20FFFFFF);
        // 滑块：开→右侧，关→左侧
        int kX = aOn ? pX + SWITCH_W - SWITCH_H + 2 : pX + 2;
        drawRect(kX, pY + 2, kX + SWITCH_H - 4, pY + SWITCH_H - 2, C_KNOB);
    }

    /** 面板内相对矩形命中（x,y 均为相对 guiLeft/guiTop 的坐标） */
    private boolean inRect(int aMX, int aMY, int aX, int aY, int aW, int aH) {
        return aMX >= aX && aMX <= aX + aW && aMY >= aY && aMY <= aY + aH;
    }

    @Override
    protected void mouseClicked(int aX, int aY, int aButton) {
        super.mouseClicked(aX, aY, aButton);
        int rx = aX - guiLeft, ry = aY - guiTop; // 与绘制一致的相对坐标
        TileEntityStorageManager tTE = mContainer.mTE;
        if (mData == null) mData = RangeClientData.get(tTE.xCoord, tTE.yCoord, tTE.zCoord);
        int tOX = mData != null ? mData.offsetX : 0;
        int tOY = mData != null ? mData.offsetY : 0;
        int tOZ = mData != null ? mData.offsetZ : 0;
        int tRadius = mData != null ? mData.radius : Config.scanRadius;
        boolean tRangeOn = mData != null && mData.rangeEnabled;
        boolean tFrameOn = mData != null && mData.showFrame;

        byte tId = -1;
        int tValue = 0;
        if (inRect(rx, ry, MINUS_X, ROW_X, MINUS_W, ROW_H)) { tId = TileEntityStorageManager.GUI_OFF_X_DEC; tValue = tOX - 1; }
        else if (inRect(rx, ry, PLUS_X, ROW_X, PLUS_W, ROW_H)) { tId = TileEntityStorageManager.GUI_OFF_X_INC; tValue = tOX + 1; }
        else if (inRect(rx, ry, MINUS_X, ROW_Y, MINUS_W, ROW_H)) { tId = TileEntityStorageManager.GUI_OFF_Y_DEC; tValue = tOY - 1; }
        else if (inRect(rx, ry, PLUS_X, ROW_Y, PLUS_W, ROW_H)) { tId = TileEntityStorageManager.GUI_OFF_Y_INC; tValue = tOY + 1; }
        else if (inRect(rx, ry, MINUS_X, ROW_Z, MINUS_W, ROW_H)) { tId = TileEntityStorageManager.GUI_OFF_Z_DEC; tValue = tOZ - 1; }
        else if (inRect(rx, ry, PLUS_X, ROW_Z, PLUS_W, ROW_H)) { tId = TileEntityStorageManager.GUI_OFF_Z_INC; tValue = tOZ + 1; }
        else if (inRect(rx, ry, MINUS_X, ROW_R, MINUS_W, ROW_H)) { tId = TileEntityStorageManager.GUI_RADIUS_DEC; tValue = tRadius - 1; }
        else if (inRect(rx, ry, PLUS_X, ROW_R, PLUS_W, ROW_H)) { tId = TileEntityStorageManager.GUI_RADIUS_INC; tValue = tRadius + 1; }
        else if (inRect(rx, ry, SWITCH_X, TOGGLE_Y_RANGE, SWITCH_W, SWITCH_H)) { tId = TileEntityStorageManager.GUI_TOGGLE_RANGE; tValue = tRangeOn ? 0 : 1; }
        else if (inRect(rx, ry, SWITCH_X, TOGGLE_Y_FRAME, SWITCH_W, SWITCH_H)) { tId = TileEntityStorageManager.GUI_TOGGLE_FRAME; tValue = tFrameOn ? 0 : 1; }
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
}
