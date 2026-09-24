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
import gregapi.util.UT;
import gtsm.tile.TileEntityStorageManager;
import net.minecraft.block.Block;
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
        Config.scanRadius     = tConfig.getInt  ("scanRadius",     "general", 1,     1, 8,  "管理器扫描半径（1 = 仅相邻 26 格）");
        Config.maxBoxes       = tConfig.getInt  ("maxBoxes",       "general", 26,    1, 64, "最多同时管理的储物桶数量");
        Config.fillEmptyBoxes = tConfig.getBoolean("fillEmptyBoxes","general", true,    "允许把新品种物品自动放进空储物桶");
        Config.oreDictUnify   = tConfig.getBoolean("oreDictUnify",  "general", true,    "插入时走 GT6 的矿物词典统一（和 GT6 设备一致）");
        if (tConfig.hasChanged()) tConfig.save();

        // ---------- 本地化默认值（英文 fallback，lang 文件优先于这里） ----------
        LH.add("gtsm.storage.manager", "Storage Manager");
        LH.add("gtsm.tooltip.1", "Manages GT6 Storage Boxes in range and exposes one unified Automation Interface");
        LH.add("gtsm.tooltip.2", "Rightclick to insert held Itemstack, Sneak-Rightclick to insert all matching Stacks");
        LH.add("gtsm.tooltip.3", "Screwdriver: toggle filling empty Boxes | Soft Hammer: rescan | Magnifying Glass: details");
        LH.add("gtsm.chat.fill.on", "Will fill empty Storage Boxes with new Item Types");
        LH.add("gtsm.chat.fill.off", "Won't fill empty Storage Boxes with new Item Types");
        LH.add("gtsm.chat.rescan", "Rescanned Area, found");
        LH.add("gtsm.chat.boxes", "Storage Boxes");
        LH.add("gtsm.chat.scope", "Storage Boxes");
        LH.add("gtsm.chat.used", "in use");
        LH.add("gtsm.chat.empty", "empty");
        LH.add("gtsm.chat.locked", "locked");
        LH.add("gtsm.chat.range", "Range");
        LH.add("gtsm.chat.oredict", "OreDict Unify");
        LH.add("gtsm.chat.fill", "Fill Empty");

        // ---------- MultiTileEntity 注册表与方块（必须在 PreInit） ----------
        new MultiTileEntityRegistry(REGISTRY_NAME);
        MultiTileEntityBlock.getOrCreate(MOD_ID, "machine", MaterialMachines.instance, Block.soundTypeMetal, CS.TOOL_wrench, 0, 0, 15, F, F);
    }

    @Override
    public void onModInit2(FMLInitializationEvent aEvent) {
        MultiTileEntityRegistry tRegistry = MultiTileEntityRegistry.getRegistry(REGISTRY_NAME);
        MultiTileEntityBlock tBlock = MultiTileEntityBlock.getOrCreate(MOD_ID, "machine", MaterialMachines.instance, Block.soundTypeMetal, CS.TOOL_wrench, 0, 0, 15, F, F);

        // 注册储物桶管理器。aLocalised 会作为英文默认名注册到 LH，lang 文件可覆盖。
        tRegistry.add("Storage Manager", "Storage", MTE_ID, 0, TileEntityStorageManager.class, 0, 16, tBlock,
                UT.NBT.make(CS.NBT_TEXTURE, TEXTURE_NAME, CS.NBT_HARDNESS, 6.0F, CS.NBT_RESISTANCE, 6.0F),
                "PPP", "CMC", "SSS",
                'P', OP.plate.dat(MT.Steel),
                'C', MT.DATA.CIRCUITS[0],
                'M', OP.casingMachine.dat(MT.Steel),
                'S', OP.stick.dat(MT.Steel));
    }
}
