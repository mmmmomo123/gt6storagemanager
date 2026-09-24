package gtsm;

import gregapi.api.Abstract_Proxy;
import gtsm.client.RangeFrameRenderer;
import net.minecraftforge.common.MinecraftForge;

/**
 * 客户端代理。贴图注册由 GT6 的 GT_API.sBlockIconload 机制处理
 * （见 TileEntityStorageManager.ICON_STORAGE_MANAGER）；
 * 大世界范围画框由 {@link RangeFrameRenderer}（Forge 渲染事件）负责。
 */
public final class Proxy_Client extends Abstract_Proxy {
    public Proxy_Client() {
        MinecraftForge.EVENT_BUS.register(new RangeFrameRenderer());
    }
}
