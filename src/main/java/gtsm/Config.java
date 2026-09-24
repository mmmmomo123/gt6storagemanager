package gtsm;

/**
 * 全局配置值，由 StorageManager_Mod 在 PreInit 从 config/gtsm.cfg 读取。
 * 保持非 final，方便后续在游戏内通过命令/工具调整（当前仅配置文件）。
 */
public class Config {
    /** 管理器扫描半径（格），8 = 上下左右各 8 格 */
    public static int scanRadius = 8;
    /** 最多同时管理的储物桶数量 */
    public static int maxBoxes = 26;
    /** 是否允许把新品种物品自动放进空储物桶 */
    public static boolean fillEmptyBoxes = true;
    /** 插入时是否走 GT6 矿物词典统一 */
    public static boolean oreDictUnify = true;
}
