package gtsm.gui;

import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;
import gtsm.StorageManager_Mod;
import gtsm.tile.TileEntityStorageManager;

/** GUI 路由：id = StorageManager_Mod.GUI_ID_RANGE。 */
public final class GTSM_GuiHandler implements IGuiHandler {
    private static TileEntityStorageManager teAt(World aWorld, int aX, int aY, int aZ) {
        TileEntity tTileEntity = aWorld.getTileEntity(aX, aY, aZ);
        return tTileEntity instanceof TileEntityStorageManager ? (TileEntityStorageManager) tTileEntity : null;
    }

    @Override
    public Object getServerGuiElement(int aID, EntityPlayer aPlayer, World aWorld, int aX, int aY, int aZ) {
        if (aID == StorageManager_Mod.GUI_ID_RANGE) {
            TileEntityStorageManager tTE = teAt(aWorld, aX, aY, aZ);
            if (tTE != null) return new Container_GTSM(aPlayer.inventory, tTE);
        }
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int aID, EntityPlayer aPlayer, World aWorld, int aX, int aY, int aZ) {
        if (aID == StorageManager_Mod.GUI_ID_RANGE) {
            TileEntityStorageManager tTE = teAt(aWorld, aX, aY, aZ);
            if (tTE != null) return new GuiContainer_GTSM(new Container_GTSM(aPlayer.inventory, tTE));
        }
        return null;
    }
}
