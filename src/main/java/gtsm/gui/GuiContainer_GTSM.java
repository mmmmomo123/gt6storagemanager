package gtsm.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import gtsm.Config;
import gtsm.network.GTSM_Network;
import gtsm.network.PacketRangeChange;
import gtsm.tile.TileEntityStorageManager;
import gtsm.client.RangeClientData;

import static gregapi.data.CS.*;

/** 范围控制 GUI 的客户端界面：6 个偏移控制 + 半径 + 两个开关。数值以服务端同步回来的缓存为准。 */
@SideOnly(Side.CLIENT)
public final class GuiContainer_GTSM extends GuiContainer {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("gregtech", "textures/gui/machines/Default.png");

    private final Container_GTSM mContainer;
    private RangeClientData.Info mData;

    public GuiContainer_GTSM(Container_GTSM aContainer) {
        super(aContainer);
        mContainer = aContainer;
        xSize = 176;
        ySize = 165;
    }

    /** 客户端按钮矩形定义（相对 guiLeft/guiTop） */
    private static final int B_X = 60, B_W = 20, V_X = 85, V_W = 36, P_X = 125;
    private static final int ROW_H = 16;
    private static final int ROW_X = 22, ROW_Y = 38, ROW_Z = 54, ROW_R = 70, ROW_TR = 88, ROW_TF = 104;

    @Override
    protected void drawGuiContainerBackgroundLayer(float aPartialTicks, int aMouseX, int aMouseY) {
        TileEntityStorageManager tTE = mContainer.mTE;
        mData = RangeClientData.get(tTE.xCoord, tTE.yCoord, tTE.zCoord);

        // 本地镜像：缓存优先（联机/单服真实数据），缺失时退化为 TE 自身值（单人开局瞬间）
        int tOX = mData != null ? mData.offsetX : 0;
        int tOY = mData != null ? mData.offsetY : 0;
        int tOZ = mData != null ? mData.offsetZ : 0;
        int tRadius = mData != null ? mData.radius : Config.scanRadius;
        boolean tRangeOn = mData != null ? mData.rangeEnabled : T;
        boolean tFrameOn = mData != null ? mData.showFrame : T;

        Minecraft tMC = Minecraft.getMinecraft();
        tMC.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        FontRenderer tFont = fontRendererObj;
        tFont.drawStringWithShadow("Storage Manager", guiLeft + 8, guiTop + 6, 0xFFFFFF);

        label(tFont, "X Offset", guiLeft + 8, guiTop + ROW_X + 4);
        label(tFont, "Y Offset", guiLeft + 8, guiTop + ROW_Y + 4);
        label(tFont, "Z Offset", guiLeft + 8, guiTop + ROW_Z + 4);
        label(tFont, "Radius", guiLeft + 8, guiTop + ROW_R + 4);

        // 偏移/半径 行
        stepRow(guiLeft + B_X, guiTop + ROW_X); valueBox(tFont, Integer.toString(tOX), guiLeft + V_X, guiTop + ROW_X); stepButton(guiLeft + P_X, guiTop + ROW_X);
        stepRow(guiLeft + B_X, guiTop + ROW_Y); valueBox(tFont, Integer.toString(tOY), guiLeft + V_X, guiTop + ROW_Y); stepButton(guiLeft + P_X, guiTop + ROW_Y);
        stepRow(guiLeft + B_X, guiTop + ROW_Z); valueBox(tFont, Integer.toString(tOZ), guiLeft + V_X, guiTop + ROW_Z); stepButton(guiLeft + P_X, guiTop + ROW_Z);
        stepRow(guiLeft + B_X, guiTop + ROW_R); valueBox(tFont, Integer.toString(tRadius), guiLeft + V_X, guiTop + ROW_R); stepButton(guiLeft + P_X, guiTop + ROW_R);

        // 开关按钮
        toggleButton(tFont, "Custom Range: " + (tRangeOn ? "ON" : "OFF"), tRangeOn, guiLeft + 30, guiTop + ROW_TR);
        toggleButton(tFont, "Show Frame: " + (tFrameOn ? "ON" : "OFF"), tFrameOn, guiLeft + 30, guiTop + ROW_TF);

        // 当前范围文本
        int tEX1 = tTE.xCoord + (tRangeOn ? tOX : 0) - (tRangeOn ? tRadius : Config.scanRadius);
        int tEY1 = tTE.yCoord + (tRangeOn ? tOY : 0) - (tRangeOn ? tRadius : Config.scanRadius);
        int tEZ1 = tTE.zCoord + (tRangeOn ? tOZ : 0) - (tRangeOn ? tRadius : Config.scanRadius);
        int tEX2 = tTE.xCoord + (tRangeOn ? tOX : 0) + (tRangeOn ? tRadius : Config.scanRadius);
        int tEY2 = tTE.yCoord + (tRangeOn ? tOY : 0) + (tRangeOn ? tRadius : Config.scanRadius);
        int tEZ2 = tTE.zCoord + (tRangeOn ? tOZ : 0) + (tRangeOn ? tRadius : Config.scanRadius);
        tFont.drawStringWithShadow("Effective Range:", guiLeft + 8, guiTop + 126, 0xCCCCCC);
        tFont.drawStringWithShadow("(" + tEX1 + ", " + tEY1 + ", " + tEZ1 + ") -> (" + tEX2 + ", " + tEY2 + ", " + tEZ2 + ")", guiLeft + 8, guiTop + 138, 0x888888);
    }

    private void label(FontRenderer aFont, String aText, int aX, int aY) {
        aFont.drawStringWithShadow(aText, aX, aY, 0xCCCCCC);
    }

    /** 「-」按钮 */
    private void stepRow(int aX, int aY) { button(aX, aY, "-"); }

    private void stepButton(int aX, int aY) { button(aX, aY, "+"); }

    private void button(int aX, int aY, String aText) {
        drawRect(aX, aY, aX + B_W, aY + ROW_H, 0xAA333333);
        drawRect(aX, aY, aX + B_W - 1, aY + 1, 0xAA666666);
        fontRendererObj.drawStringWithShadow(aText, aX + 7, aY + 4, 0xFFFFFF);
    }

    private void valueBox(FontRenderer aFont, String aText, int aX, int aY) {
        drawRect(aX, aY, aX + V_W, aY + ROW_H, 0xAA222222);
        aFont.drawStringWithShadow(aText, aX + V_W / 2 - aFont.getStringWidth(aText) / 2, aY + 4, 0x33FF88);
    }

    private void toggleButton(FontRenderer aFont, String aText, boolean aEnabled, int aX, int aY) {
        drawRect(aX, aY, aX + 116, aY + ROW_H, aEnabled ? 0xAA1B5E20 : 0xAA4E342E);
        aFont.drawStringWithShadow(aText, aX + 8, aY + 4, 0xFFFFFF);
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
        if (hit(rx, ry, B_X, ROW_X)) { tId = TileEntityStorageManager.GUI_OFF_X_DEC; tValue = tOX - 1; }
        else if (hit(rx, ry, P_X, ROW_X)) { tId = TileEntityStorageManager.GUI_OFF_X_INC; tValue = tOX + 1; }
        else if (hit(rx, ry, B_X, ROW_Y)) { tId = TileEntityStorageManager.GUI_OFF_Y_DEC; tValue = tOY - 1; }
        else if (hit(rx, ry, P_X, ROW_Y)) { tId = TileEntityStorageManager.GUI_OFF_Y_INC; tValue = tOY + 1; }
        else if (hit(rx, ry, B_X, ROW_Z)) { tId = TileEntityStorageManager.GUI_OFF_Z_DEC; tValue = tOZ - 1; }
        else if (hit(rx, ry, P_X, ROW_Z)) { tId = TileEntityStorageManager.GUI_OFF_Z_INC; tValue = tOZ + 1; }
        else if (hit(rx, ry, B_X, ROW_R)) { tId = TileEntityStorageManager.GUI_RADIUS_DEC; tValue = tRadius - 1; }
        else if (hit(rx, ry, P_X, ROW_R)) { tId = TileEntityStorageManager.GUI_RADIUS_INC; tValue = tRadius + 1; }
        else if (hit(rx, ry, 30, ROW_TR)) { tId = TileEntityStorageManager.GUI_TOGGLE_RANGE; tValue = (mData != null && mData.rangeEnabled) ? 0 : 1; }
        else if (hit(rx, ry, 30, ROW_TF)) { tId = TileEntityStorageManager.GUI_TOGGLE_FRAME; tValue = (mData != null && mData.showFrame) ? 0 : 1; }
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

    private boolean hit(int aRX, int aRY, int aBX, int aBY) {
        return aRX >= aBX && aRX <= aBX + B_W && aRY >= aBY && aRY <= aBY + ROW_H;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }
}
