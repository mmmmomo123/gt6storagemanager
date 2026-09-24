package gtsm.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.client.event.TextureStitchEvent;

/**
 * 图集缝合钩子。
 *
 * 1.7.10 每次资源重载（进游戏、F3+T、切资源包）都会【重建】方块图集，
 * 之前注册的 IIcon 引用全部失效。GT6 自家的机器靠 IMTE_RegisterIcons
 * 每次缝合重新注册，本 mod 的 MTE 现在挂在 GT6 的机器块上（注册表不同，
 * 不会收到它的 registerIcons 回调），所以这里用自己的事件监听兜底：
 * 每次 PRE 缝合都给 ICON_STORAGE_MANAGER 重新 registerIcon。
 * 少了这一步，方块会「有碰撞、不渲染」(看起来像空气)。
 */
@SideOnly(Side.CLIENT)
public final class IconStitchHandler {
    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre aEvent) {
        if (aEvent.map == null) return;
        int tType = aEvent.map.getTextureType();
        // 0 = 方块图集，1 = 物品图集；两个都注册无害（registerIcon 幂等）
        if (tType == 0 || tType == 1) {
            try {
                gtsm.tile.TileEntityStorageManager.ICON_STORAGE_MANAGER.registerIcons(aEvent.map);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
