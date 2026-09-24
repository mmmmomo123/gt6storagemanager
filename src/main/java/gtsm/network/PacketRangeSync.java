package gtsm.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import gtsm.client.RangeClientData;

/** 服务端 → 客户端：管理器范围数据同步（GUI 镜像 + 大世界画框数据源）。 */
public final class PacketRangeSync implements IMessage {
    public int x, y, z, offsetX, offsetY, offsetZ, radius;
    public byte flags; // bit0 = rangeEnabled, bit1 = showFrame

    public PacketRangeSync() {}

    public PacketRangeSync(int aX, int aY, int aZ, int aOX, int aOY, int aOZ, int aRadius, boolean aRangeEnabled, boolean aShowFrame) {
        x = aX; y = aY; z = aZ; offsetX = aOX; offsetY = aOY; offsetZ = aOZ; radius = aRadius;
        flags = (byte) ((aRangeEnabled ? 1 : 0) | (aShowFrame ? 2 : 0));
    }

    public boolean rangeEnabled() { return (flags & 1) != 0; }
    public boolean showFrame() { return (flags & 2) != 0; }

    @Override public void fromBytes(ByteBuf aBuf) {
        x = aBuf.readInt(); y = aBuf.readInt(); z = aBuf.readInt();
        offsetX = aBuf.readInt(); offsetY = aBuf.readInt(); offsetZ = aBuf.readInt();
        radius = aBuf.readInt(); flags = aBuf.readByte();
    }

    @Override public void toBytes(ByteBuf aBuf) {
        aBuf.writeInt(x); aBuf.writeInt(y); aBuf.writeInt(z);
        aBuf.writeInt(offsetX); aBuf.writeInt(offsetY); aBuf.writeInt(offsetZ);
        aBuf.writeInt(radius); aBuf.writeByte(flags);
    }

    public static final class Handler implements IMessageHandler<PacketRangeSync, IMessage> {
        @Override public IMessage onMessage(PacketRangeSync aMsg, MessageContext aCtx) {
            // ConcurrentHashMap 线程安全；无需切主线程
            RangeClientData.put(aMsg.x, aMsg.y, aMsg.z, aMsg.offsetX, aMsg.offsetY, aMsg.offsetZ, aMsg.radius, aMsg.rangeEnabled(), aMsg.showFrame());
            return null;
        }
    }
}
