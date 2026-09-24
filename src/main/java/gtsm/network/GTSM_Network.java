package gtsm.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/** GTSM 的网络通道：0 = 客户端→服务端(GUI 操作)，1 = 服务端→客户端(范围数据同步)。 */
public final class GTSM_Network {
    public static final SimpleNetworkWrapper WRAPPER = NetworkRegistry.INSTANCE.newSimpleChannel("GTSM");

    public static void init() {
        WRAPPER.registerMessage(PacketRangeChange.Handler.class, PacketRangeChange.class, 0, Side.SERVER);
        WRAPPER.registerMessage(PacketRangeSync.Handler.class, PacketRangeSync.class, 1, Side.CLIENT);
    }
}
