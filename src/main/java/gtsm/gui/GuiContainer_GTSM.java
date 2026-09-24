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
 *   顶部：主标题 Storage Manager + 副标题
 *   中部：X/Y/Z/Radius 四行表单（右对齐标签 + 胶囊步进器 [-][值][+]）
 *   下部：两个拨动开关（Custom Range / Show Frame）
 *   底部：Effective Range 代码块（Min/Max 两行）
 */
@SideOnly(Side.CLIENT)
public final class GuiContainer_GTSM extends GuiContainer {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("gtsm", "textures/gui/range.png");

    private final Container_GTSM mContainer;
    private RangeClientData.Info mData;

    // ---- 布局常量（绘制与命中检测共用） ----
    private static final int ROW_H = 18;
    private static final int LABEL_R = 94;                    // 标签右对齐边缘
    private static final int STEP_X = 98, STEP_W = 64;        // 步进器整体
    private static final int MINUS_X = STEP_X,      MINUS_W = 20;
    private static final int VALUE_X = STEP_X + 20, VALUE_W = 20;
    private static final int PLUS_X  = STEP_X + 40, PLUS_W  = 20;
    private static final int ROW_X = 42, ROW_Y = 62, ROW_Z = 82, ROW_R = 102;
    private static final int TOGGLE_Y_RANGE = 126, TOGGLE_Y_FRAME = 148;
    private static final int TOGGLE_LABEL_X = 14;
    private static final int SWITCH_X = 130, SWITCH_W = 30, SWITCH_H = 14;
    private static final int BLOCK_X = 14, BLOCK_Y = 170, BLOCK_W = 148, BLOCK_H = 26;

    // ---- 配色（现代暗色系） ----
    private static final int C_TITLE     = 0xFFFFFFFF;
    private static final int C_SUBTITLE  = 0xFF8A93A0;
    private static final int C_LABEL     = 0xFFD7DCE4;
    private static final int C_VALUE     = 0xFF7FE7D2; // 数值：柔和薄荷青
    private static final int C_BTN_TXT   = 0xFFC9CDD4;
    private static final int C_STEP_BG   = 0xFF2A2C34; // 步进器容器
    private static final int C_STEP_TOP  = 0x18FFFFFF; // 顶部微高光
    private static final int C_STEP_DIV  = 0x14FFFFFF; // 分隔(低透明)
    private static final int C_STEP_HOVER= 0x36FFFFFF;
    private static final int C_PILL_ON   = 0xFF3FB96F; // 开关开：柔和翠绿
    private static final int C_PILL_OFF  = 0xFF454852; // 开关关：深灰
    private static final int C_KNOB      = 0xFFF2F5F7;
    private static final int C_BLOCK_BG  = 0x9912161D; // 范围信息块
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
        FontRenderer tFont = fontRendererObj;

        // ---- 圆角深色面板（PNG 贴图，含透明圆角） ----
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(BACKGROUND);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        drawTexturedModalRect(L, Tp, 0, 0, xSize, ySize);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);

        // ---- 标题区 ----
        tFont.drawStringWithShadow("Storage Manager", L + 14, Tp + 10, C_TITLE);
        tFont.drawStringWithShadow("Range Configuration", L + 14, Tp + 21, C_SUBTITLE);

        // ---- 参数表单 ----
        paramRow(tFont, "X Offset", tOX, L, Tp + ROW_X, aMouseX - L, aMouseY - Tp);
        paramRow(tFont, "Y Offset", tOY, L, Tp + ROW_Y, aMouseX - L, aMouseY - Tp);
        paramRow(tFont, "Z Offset", tOZ, L, Tp + ROW_Z, aMouseX - L, aMouseY - Tp);
        paramRow(tFont, "Radius",   tRadius, L, Tp + ROW_R, aMouseX - L, aMouseY - Tp);

        // ---- 拨动开关 ----
        toggleRow(tFont, "Custom Range", tRangeOn, L, Tp + TOGGLE_Y_RANGE, aMouseX - L, aMouseY - Tp);
        toggleRow(tFont, "Show Frame",  tFrameOn, L, Tp + TOGGLE_Y_FRAME, aMouseX - L, aMouseY - Tp);

        // ---- Effective Range 代码块 ----
        drawRect(L + BLOCK_X, Tp + BLOCK_Y, L + BLOCK_X + BLOCK_W, Tp + BLOCK_Y + BLOCK_H, C_BLOCK_BG);
        drawRect(L + BLOCK_X, Tp + BLOCK_Y, L + BLOCK_X + BLOCK_W, Tp + BLOCK_Y + 1, C_BLOCK_ED);
        tFont.drawStringWithShadow("Effective Range", L + BLOCK_X + 6, Tp + BLOCK_Y + 4, C_SUBTITLE);
        tFont.drawStringWithShadow("Min", L + BLOCK_X + 6, Tp + BLOCK_Y + 14, C_TAG);
        tFont.drawStringWithShadow("" + (tTE.xCoord + (tRangeOn ? tOX : 0) - tEffR) + ", " + (tTE.yCoord + (tRangeOn ? tOY : 0) - tEffR) + ", " + (tTE.zCoord + (tRangeOn ? tOZ : 0) - tEffR), L + BLOCK_X + 30, Tp + BLOCK_Y + 14, C_BLOCK_TXT);
        tFont.drawStringWithShadow("Max", L + BLOCK_X + 6, Tp + BLOCK_Y + 22, C_TAG);
        tFont.drawStringWithShadow("" + (tTE.xCoord + (tRangeOn ? tOX : 0) + tEffR) + ", " + (tTE.yCoord + (tRangeOn ? tOY : 0) + tEffR) + ", " + (tTE.zCoord + (tRangeOn ? tOZ : 0) + tEffR), L + BLOCK_X + 30, Tp + BLOCK_Y + 22, C_BLOCK_TXT);
    }

    /** 一行参数：右对齐标签 + 胶囊步进器（-[值]-+） */
    private void paramRow(FontRenderer aFont, String aLabel, int aValue, int aL, int aRowY, int aMX, int aMY) {
        aFont.drawStringWithShadow(aLabel, aL + LABEL_R - aFont.getStringWidth(aLabel), aRowY + 5, C_LABEL);

        // 胶囊容器
        drawRect(aL + STEP_X, aRowY, aL + STEP_X + STEP_W, aRowY + ROW_H, C_STEP_BG);
        drawRect(aL + STEP_X, aRowY, aL + STEP_X + STEP_W, aRowY + 1, C_STEP_TOP);
        // 分隔（低透明竖线 + 微高光，做出分区而非硬框）
        drawRect(aL + STEP_X + 19, aRowY + 3, aL + STEP_X + 20, aRowY + ROW_H - 3, C_STEP_DIV);
        drawRect(aL + STEP_X + 40, aRowY + 3, aL + STEP_X + 41, aRowY + ROW_H - 3, C_STEP_DIV);
        // 悬停高亮
        if (inRect(aMX, aMY, MINUS_X, aRowY, MINUS_W)) drawRect(aL + MINUS_X, aRowY, aL + MINUS_X + MINUS_W, aRowY + ROW_H, C_STEP_HOVER);
        if (inRect(aMX, aMY, PLUS_X, aRowY, PLUS_W)) drawRect(aL + PLUS_X, aRowY, aL + PLUS_X + PLUS_W, aRowY + ROW_H, C_STEP_HOVER);

        String tVal = Integer.toString(aValue);
        aFont.drawStringWithShadow("-", aL + MINUS_X + MINUS_W / 2 - 2, aRowY + 5, C_BTN_TXT);
        aFont.drawStringWithShadow(tVal, aL + VALUE_X + VALUE_W / 2 - aFont.getStringWidth(tVal) / 2, aRowY + 5, C_VALUE);
        aFont.drawStringWithShadow("+", aL + PLUS_X + PLUS_W / 2 - 2, aRowY + 5, C_BTN_TXT);
    }

    /** 一行开关：左标签 + 右侧拨动 pill */
    private void toggleRow(FontRenderer aFont, String aLabel, boolean aOn, int aL, int aRowY, int aMX, int aMY) {
        boolean tHover = inRect(aMX, aMY, TOGGLE_LABEL_X, aRowY, SWITCH_X + SWITCH_W - TOGGLE_LABEL_X, ROW_H);
        aFont.drawStringWithShadow(aLabel, aL + TOGGLE_LABEL_X, aRowY + 5, tHover ? C_TITLE : C_LABEL);

        int pX = aL + SWITCH_X, pY = aRowY + 2;
        drawRect(pX, pY, pX + SWITCH_W, pY + SWITCH_H, aOn ? C_PILL_ON : C_PILL_OFF);
        drawRect(pX, pY, pX + SWITCH_W, pY + 1, 0x20FFFFFF);
        // 滑块：开→右侧，关→左侧
        int kX = aOn ? pX + SWITCH_W - SWITCH_H + 2 : pX + 2;
        drawRect(kX, pY + 2, kX + SWITCH_H - 4, pY + SWITCH_H - 2, C_KNOB);
    }

    private boolean inRect(int aMX, int aMY, int aX, int aY, int aW) { return inRect(aMX, aMY, aX, aY, aW, ROW_H); }
    private boolean inRect(int aMX, int aMY, int aX, int aY, int aW, int aH) {
        return aMX >= aX && aMX <= aX + aW && aMY >= aY && aMY <= aY + aH;
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
        boolean tRangeOn = mData != null && mData.rangeEnabled;
        boolean tFrameOn = mData != null && mData.showFrame;

        byte tId = -1;
        int tValue = 0;
        if (inRect(rx, ry, MINUS_X, ROW_X, MINUS_W)) { tId = TileEntityStorageManager.GUI_OFF_X_DEC; tValue = tOX - 1; }
        else if (inRect(rx, ry, PLUS_X, ROW_X, PLUS_W)) { tId = TileEntityStorageManager.GUI_OFF_X_INC; tValue = tOX + 1; }
        else if (inRect(rx, ry, MINUS_X, ROW_Y, MINUS_W)) { tId = TileEntityStorageManager.GUI_OFF_Y_DEC; tValue = tOY - 1; }
        else if (inRect(rx, ry, PLUS_X, ROW_Y, PLUS_W)) { tId = TileEntityStorageManager.GUI_OFF_Y_INC; tValue = tOY + 1; }
        else if (inRect(rx, ry, MINUS_X, ROW_Z, MINUS_W)) { tId = TileEntityStorageManager.GUI_OFF_Z_DEC; tValue = tOZ - 1; }
        else if (inRect(rx, ry, PLUS_X, ROW_Z, PLUS_W)) { tId = TileEntityStorageManager.GUI_OFF_Z_INC; tValue = tOZ + 1; }
        else if (inRect(rx, ry, MINUS_X, ROW_R, MINUS_W)) { tId = TileEntityStorageManager.GUI_RADIUS_DEC; tValue = tRadius - 1; }
        else if (inRect(rx, ry, PLUS_X, ROW_R, PLUS_W)) { tId = TileEntityStorageManager.GUI_RADIUS_INC; tValue = tRadius + 1; }
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
