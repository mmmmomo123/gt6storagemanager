package gtsm.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import gtsm.tile.TileEntityStorageManager;

/** 客户端 → 服务端：GUI 上的范围控制操作。guiId 见 TileEntityStorageManager.handleGuiAction。 */
public final class PacketRangeChange implements IMessage {
    public int x, y, z, value;
    public byte guiId;

    public PacketRangeChange() {}

    public PacketRangeChange(int aX, int aY, int aZ, byte aGuiId, int aValue) {
        x = aX; y = aY; z = aZ; guiId = aGuiId; value = aValue;
    }

    @Override public void fromBytes(ByteBuf aBuf) {
        x = aBuf.readInt(); y = aBuf.readInt(); z = aBuf.readInt();
        guiId = aBuf.readByte(); value = aBuf.readInt();
    }

    @Override public void toBytes(ByteBuf aBuf) {
        aBuf.writeInt(x); aBuf.writeInt(y); aBuf.writeInt(z);
        aBuf.writeByte(guiId); aBuf.writeInt(value);
    }

    public static final class Handler implements IMessageHandler<PacketRangeChange, IMessage> {
        @Override public IMessage onMessage(final PacketRangeChange aMsg, MessageContext aCtx) {
            final net.minecraft.world.World tWorld = aCtx.getServerHandler().playerEntity.worldObj;
            final net.minecraft.tileentity.TileEntity tTileEntity = tWorld.getTileEntity(aMsg.x, aMsg.y, aMsg.z);
            if (tTileEntity instanceof TileEntityStorageManager) {
                // 网络线程 → 主线程，避免与世界 tick 竞争
                cpw.mods.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(new Runnable() {
                    @Override public void run() { ((TileEntityStorageManager) tTileEntity).handleGuiAction(aMsg.guiId, aMsg.value); }
                });
            }
            return null;
        }
    }
}
