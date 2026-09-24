package gtsm.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import gtsm.tile.TileEntityStorageManager;

/** 范围控制 GUI 的服务端容器（无槽位，纯配置界面）。 */
public final class Container_GTSM extends Container {
    public final TileEntityStorageManager mTE;
    public final InventoryPlayer mPlayer;

    public Container_GTSM(InventoryPlayer aPlayer, TileEntityStorageManager aTE) {
        mTE = aTE;
        mPlayer = aPlayer;
    }

    @Override
    public boolean canInteractWith(EntityPlayer aPlayer) {
        return mTE != null && mTE.getWorld() != null && !mTE.mIsDead;
    }
}
