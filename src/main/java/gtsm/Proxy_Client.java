package gtsm;

import gregapi.api.Abstract_Proxy;
import gtsm.client.IconStitchHandler;
import net.minecraftforge.common.MinecraftForge;

/**
 * 客户端代理：
 * - IconStitchHandler：每次图集重建时重新注册方块图标（防“有碰撞不渲染”）
 * - 大世界范围画框走 TESR（见 TileEntityStorageManager.onRegistrationFirstClient → gtsm.client.RangeFrameRenderer）
 */
public final class Proxy_Client extends Abstract_Proxy {
    public Proxy_Client() {
        MinecraftForge.EVENT_BUS.register(new IconStitchHandler());
    }
}
