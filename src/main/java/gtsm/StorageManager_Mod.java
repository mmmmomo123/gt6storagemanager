package gtsm;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import gregapi.api.Abstract_Mod;
import gregapi.api.Abstract_Proxy;
import gregapi.block.MaterialMachines;
import gregapi.block.multitileentity.MultiTileEntityBlock;
import gregapi.block.multitileentity.MultiTileEntityRegistry;
import gregapi.code.ModData;
import gregapi.data.CS;
import gregapi.data.LH;
import gregapi.data.MT;
import gregapi.data.OP;
import gregapi.util.CR;
import gregapi.util.ST;
import gregapi.util.UT;
import gtsm.tile.TileEntityStorageManager;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;

import static gregapi.data.CS.*;

/**
 * GT6 附属 Mod：储物桶管理器（Storage Manager）
 *
 * 一个类似 Storage Drawers「抽屉管理器」的方块，本身不存物品，
 * 而是把自身的 IInventory 做成映射范围内 GT6 储物桶的「幻影视图」，
 * 让所有认 IInventory/ISidedInventory 的自动化（管道/AE2/GT 输送带）能统一存取。
 *
 * 依赖 gregapi_post（GT6 的后置加载阶段），这是 GT6 官方推荐的附属加载顺序。
 */
@Mod(modid = StorageManager_Mod.MOD_ID, name = StorageManager_Mod.MOD_NAME, version = StorageManager_Mod.VERSION, dependencies = "required-after:gregapi_post")
public final class StorageManager_Mod extends Abstract_Mod {
    /** Mod-ID 必须全小写、无空格（vanilla 资源包限制） */
    public static final String MOD_ID = "gtsm";
    public static final String MOD_NAME = "GT6 Storage Manager";
    public static final String VERSION = "1.0.0";

    /** GT 的 ModData 对象 */
    public static final ModData MOD_DATA = new ModData(MOD_ID, MOD_NAME);

    /** MultiTileEntity 注册表名称与方块内 ID */
    public static final String REGISTRY_NAME = MOD_ID + ".multitileentity";
    public static final int MTE_ID = 1;

    /** 范围控制 GUI 的 id（见 gtsm.gui.GTSM_GuiHandler） */
    public static final int GUI_ID_RANGE = 0;

    /** 方块贴图名（对应 assets/gtsm/textures/blocks/machine_storage_manager.png） */
    public static final String TEXTURE_NAME = "machine_storage_manager";

    @SidedProxy(modId = MOD_ID, clientSide = "gtsm.Proxy_Client", serverSide = "gtsm.Proxy_Server")
    public static Abstract_Proxy PROXY;

    public static StorageManager_Mod instance;

    public StorageManager_Mod() {
        instance = this;
    }

    @Override public String getModID() {return MOD_ID;}
    @Override public String getModName() {return MOD_NAME;}
    @Override public String getModNameForLog() {return "StorageManager";}
    @Override public Abstract_Proxy getProxy() {return PROXY;}

    // Abstract_Mod 的其余抽象方法：本 mod 无对应逻辑，空实现即可
    @Override public void onModPostInit2(FMLPostInitializationEvent aEvent) {}
    @Override public void onModServerStarting2(FMLServerStartingEvent aEvent) {}
    @Override public void onModServerStarted2(FMLServerStartedEvent aEvent) {}
    @Override public void onModServerStopping2(FMLServerStoppingEvent aEvent) {}
    @Override public void onModServerStopped2(FMLServerStoppedEvent aEvent) {}

    // 这 7 个方法保持原样，只做事件转发
    @Mod.EventHandler public final void onPreLoad       (FMLPreInitializationEvent   aEvent) {onModPreInit(aEvent);}
    @Mod.EventHandler public final void onLoad          (FMLInitializationEvent      aEvent) {onModInit(aEvent);}
    @Mod.EventHandler public final void onPostLoad      (FMLPostInitializationEvent  aEvent) {onModPostInit(aEvent);}
    @Mod.EventHandler public final void onServerStarting(FMLServerStartingEvent      aEvent) {onModServerStarting(aEvent);}
    @Mod.EventHandler public final void onServerStarted (FMLServerStartedEvent       aEvent) {onModServerStarted(aEvent);}
    @Mod.EventHandler public final void onServerStopping(FMLServerStoppingEvent      aEvent) {onModServerStopping(aEvent);}
    @Mod.EventHandler public final void onServerStopped (FMLServerStoppedEvent       aEvent) {onModServerStopped(aEvent);}

    @Override
    public void onModPreInit2(FMLPreInitializationEvent aEvent) {
        // ---------- 配置 ----------
        Configuration tConfig = new Configuration(aEvent.getSuggestedConfigurationFile());
        tConfig.load();
        Config.scanRadius     = tConfig.getInt  ("scanRadius",     "general", 8,     1, 8, "管理器扫描半径（8 = 上下左右各 8 格）");
        Config.maxBoxes       = tConfig.getInt  ("maxBoxes",       "general", 64,    1, 256, "最多同时管理的储物桶数量 [range: 1 ~ 256, default: 64]");
        Config.fillEmptyBoxes = tConfig.getBoolean("fillEmptyBoxes","general", true,    "允许把新品种物品自动放进空储物桶");
        Config.oreDictUnify   = tConfig.getBoolean("oreDictUnify",  "general", true,    "插入时走 GT6 的矿物词典统一（和 GT6 设备一致）");
        if (tConfig.hasChanged()) tConfig.save();

        // ---------- 本地化默认值（中文；zh_cn/en_us lang 文件可覆盖） ----------
        LH.add("gtsm.storage.manager", "储物管理器");
        LH.add("gtsm.tooltip.1", "管理范围内的GT6储物箱，并暴露统一的自动化接口");
        LH.add("gtsm.tooltip.2", "右键：插入手中物品 | 潜行+右键：插入所有匹配物品");
        LH.add("gtsm.tooltip.3", "螺丝刀：切换是否填充空箱 | 软锤：重新扫描 | 放大镜：查看详情");
        LH.add("gtsm.tooltip.4", "空手右键：打开范围配置界面");
        LH.add("gtsm.chat.fill.on", "将允许把新品种物品放入空储物桶");
        LH.add("gtsm.chat.fill.off", "将不会占用空储物桶");
        LH.add("gtsm.chat.rescan", "已重新扫描区域，找到");
        LH.add("gtsm.chat.boxes", "个储物桶");
        LH.add("gtsm.chat.scope", "储物桶");
        LH.add("gtsm.chat.used", "已占用");
        LH.add("gtsm.chat.empty", "空闲");
        LH.add("gtsm.chat.locked", "已锁定");
        LH.add("gtsm.chat.range", "范围");
        LH.add("gtsm.chat.radius", "半径");
        LH.add("gtsm.chat.offset", "偏移");
        LH.add("gtsm.chat.range.custom", "自定义范围");
        LH.add("gtsm.chat.range.default", "默认范围");
        LH.add("gtsm.chat.oredict", "矿物词典统一");
        LH.add("gtsm.chat.fill", "填充空桶");

        // ---------- 网络通道与 GUI 路由 ----------
        gtsm.network.GTSM_Network.init();
        cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(instance, new gtsm.gui.GTSM_GuiHandler());

        // ---------- MultiTileEntity 注册表与方块（必须在 PreInit） ----------
        new MultiTileEntityRegistry(REGISTRY_NAME);
        MultiTileEntityBlock.getOrCreate(MOD_ID, "machine", MaterialMachines.instance, Block.soundTypeMetal, CS.TOOL_wrench, 0, 0, 15, F, F);
    }

    @Override
    public void onModInit2(FMLInitializationEvent aEvent) {
        MultiTileEntityRegistry tRegistry = MultiTileEntityRegistry.getRegistry(REGISTRY_NAME);
        MultiTileEntityBlock tBlock = MultiTileEntityBlock.getOrCreate(MOD_ID, "machine", MaterialMachines.instance, Block.soundTypeMetal, CS.TOOL_wrench, 0, 0, 15, F, F);

        // 配方材料（GT6 6.17.06，单一 MTE 注册表 "gt.multitileentity"）
        MultiTileEntityRegistry tGT = MultiTileEntityRegistry.getRegistry("gt.multitileentity");
        ItemStack tInserter = tGT == null ? null : tGT.getItem(32751); // Storage Inserter（存储输入器）
        Object tPipe = OP.pipeMedium.dat(MT.Pt);                    // 铂物品管道（Platinum Item Pipe，OreDictItemData）

        if (tInserter == null || !ST.valid(tInserter)) {
            System.out.println("[GTSM] WARNING: Storage Inserter (mTE 32751) not found - recipe skipped (wrong GT6 version?)");
            return;
        }

        // aca / bdb / aca —— a=铝板 b=t3以上电路 c=铂物品管道 d=存储输入器
        // GT6 电路阶梯：[0]Primitive [1]Basic(基础) [2]Good(低级) [3]Advanced [4]Elite [5]Master [6]Ultimate(究极)
        // 需求：排除基础/低级，接受 t3+（高级/精英/大师/究极），共 4 种
        int[] tCircuitIDs = {3, 4, 5, 6};
        ItemStack tSelf = tRegistry.add("Storage Manager", "Storage", MTE_ID, 0, TileEntityStorageManager.class, 0, 16, tBlock,
                UT.NBT.make(CS.NBT_TEXTURE, TEXTURE_NAME, CS.NBT_HARDNESS, 6.0F, CS.NBT_RESISTANCE, 6.0F),
                "PCP", "BDB", "PCP",
                'P', OP.plate.dat(MT.Al),
                'C', tPipe,
                'B', MT.DATA.CIRCUITS[tCircuitIDs[0]],
                'D', tInserter);
        for (int i = 1; i < tCircuitIDs.length; i++) {
            CR.shaped(tSelf, CR.DEF, "PCP", "BDB", "PCP",
                    'P', OP.plate.dat(MT.Al),
                    'C', tPipe,
                    'B', MT.DATA.CIRCUITS[tCircuitIDs[i]],
                    'D', tInserter);
        }
    }
}
