package gtsm;

import gregapi.api.Abstract_Proxy;

/**
 * 客户端代理。贴图注册由 GT6 的 GT_API.sBlockIconload 机制处理
 * （见 TileEntityStorageManager.ICON_STORAGE_MANAGER）；
 * 大世界范围画框走 TESR（TileEntitySpecialRenderer，见 TileEntityStorageManager.
 * onRegistrationFirstClient → gtsm.client.RangeFrameRenderer）。
 */
public final class Proxy_Client extends Abstract_Proxy {
    // 客户端专属逻辑（当前无）
}
