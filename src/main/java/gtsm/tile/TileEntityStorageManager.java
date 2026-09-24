package gtsm.tile;

import gregapi.GT_API;
import appeng.util.inv.IInventoryWrapper;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_AddToolTips;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_GetBlockHardness;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_GetComparatorInputOverride;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_GetItemName;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_RegisterIcons;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_OnRegistrationFirstClient;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_GetExplosionResistance;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_OnToolClick;
import gregapi.code.ArrayListNoNulls;
import gregapi.data.LH;
import gregapi.network.IPacket;
import gregapi.render.BlockTextureDefault;
import gregapi.render.IIconContainer;
import gregapi.render.ITexture;
import gregapi.tileentity.base.TileEntityBase04MultiTileEntities;
import gregapi.tileentity.inventories.MultiTileEntityMassStorage;
import gregapi.util.OM;
import gregapi.util.ST;
import gtsm.Config;
import gtsm.StorageManager_Mod;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import java.util.List;

import static gregapi.data.CS.*;

/**
 * 储物桶管理器的核心方块实体。
 *
 * <p>设计：自身<b>不保存任何物品</b>，它的 {@link IInventory} 是一层「幻影视图」，
 * 直接映射扫描范围内的 GT6 储物桶（{@link MultiTileEntityMassStorage} 家族）：
 * <ul>
 *   <li>槽 0 = 通用路由槽：插入任意物品 → 先找已有同品种桶，没有再找空桶；右键存入也走这里。</li>
 *   <li>槽 1..N = 幻影槽：一一对应每个储物桶，读取/抽出直接转发到桶的 slot 1。</li>
 * </ul>
 * 因为 GT6 储物桶本身就是标准 {@link IInventory}，所以转发逻辑非常薄。</p>
 *
 * <p><b>编译期注意事项</b>：GT6 的 release jar 是 SRG 混淆的，凡是对 Minecraft 方法的调用
 * 一律先强转成原版类型（{@code (IInventory) box}、{@code (TileEntity) box}）再调用，
 * GT 自家方法（{@code insertItems}、{@code slot}、{@code slotHas}、字段 {@code mMode} 等
 * 不参与混淆）可直接调用。</p>
 */
public class TileEntityStorageManager extends TileEntityBase04MultiTileEntities
        implements IInventory, ISidedInventory, IMTE_OnToolClick, IMTE_AddToolTips,
                   IMTE_GetBlockHardness, IMTE_GetExplosionResistance, IMTE_GetComparatorInputOverride,
                   IInventoryWrapper, IMTE_OnRegistrationFirstClient, IMTE_GetItemName, IMTE_RegisterIcons {

    /** 通用路由槽的下标 */
    public static final int SLOT_ROUTER = 0;
    /** 第一个储物桶幻影槽的下标（= 槽 1） */
    public static final int SLOT_BOX_BASE = 1;
    /** 箱子 GUI/自动化里玩家与箱体的交互距离上限（原版常量） */
    private static final double USE_DISTANCE_SQ = 64.0D;

    /** 储物桶贴图容器。注册由 TextureStitchEvent 每缝触发（见 gtsm.client.IconStitchHandler），
     *  不能用 GT_API.sBlockIconload 一次性队列——每次资源重载图集都会重建，只入队一次会失效导致方块隐形 */
    public static IIconContainer ICON_STORAGE_MANAGER = new IIconContainer() {
        private IIcon mIcon;

        @Override public IIcon getIcon(int aRenderPass) { return mIcon; }
        @Override public short[] getIconColor(int aRenderPass) { return UNCOLOURED; }
        @Override public int getIconPasses() { return 1; }
        @Override public boolean isUsingColorModulation(int aRenderPass) { return T; }
        @Override public ResourceLocation getTextureFile() { return TextureMap.locationBlocksTexture; }
        @Override public void registerIcons(IIconRegister aIconRegister) { mIcon = aIconRegister.registerIcon(StorageManager_Mod.MOD_ID + ":" + StorageManager_Mod.TEXTURE_NAME); }
    };

    /** 当前管理的储物桶列表（列表下标 i 对应幻影槽 SLOT_BOX_BASE + i） */
    private final List<MultiTileEntityMassStorage> mBoxes = new ArrayListNoNulls<>();

    private boolean mNeedsRescan = T;
    private boolean mFillEmptyBoxes = Config.fillEmptyBoxes;
    private float mHardness = 6.0F, mResistance = 6.0F;

    // ---- 范围控制（GUI 设置，持久化于 NBT，随区块加载恢复） ----
    /** 扫描中心偏移（相对管理器自身） */
    private int mOffsetX = 0, mOffsetY = 0, mOffsetZ = 0;
    /** 扫描半径（各轴一致） */
    private int mRadius = Config.scanRadius;
    /** 是否启用自定义范围（关闭则回退默认：自身 ±scanRadius 立方体） */
    private boolean mRangeEnabled = T;
    /** 是否在大世界绘制范围框 */
    private boolean mShowFrame = T;

    /** 上次比较器输出，用于检测变化时刷新红石（避免每 tick 刷方块更新） */
    private int mLastComparatorOutput = -1;

    // --------------------------------------------------------------
    //  注册与持久化
    // --------------------------------------------------------------

    @Override
    public String getTileEntityName() {
        return "gtsm.storage.manager"; // 不能以 "gt." 开头（GT6 保留）
    }

    /** GT6 首次注册回调：除父类的 GameRegistry 注册外，显式再注册一次 + 打点（防 GT6 注册链被短路导致客户端 TE 不加载） */
    @Override
    public void onRegistrationFirst(gregapi.block.multitileentity.MultiTileEntityRegistry aRegistry, short aID) {
        super.onRegistrationFirst(aRegistry, aID);
        try {
            cpw.mods.fml.common.registry.GameRegistry.registerTileEntity(TileEntityStorageManager.class, "gtsm.storage.manager");
            System.out.println("[GTSM-DIAG] onRegistrationFirst: explicit registerTileEntity(gtsm.storage.manager) DONE aID=" + aID);
        } catch (Throwable t) {
            System.out.println("[GTSM-DIAG] onRegistrationFirst: register FAILED: " + t);
        }
    }

    /** 物品显示名直接由代码返回中文（不依赖 lang 加载，确保任何 locale 都显示中文） */
    @Override
    public String getItemName(ItemStack aStack, String aDefaultName) {
        return "储物桶管理器";
    }

    @Override
    public void readFromNBT2(NBTTagCompound aNBT) {
        super.readFromNBT2(aNBT);
        if (aNBT.hasKey(NBT_HARDNESS)) mHardness = aNBT.getFloat(NBT_HARDNESS);
        if (aNBT.hasKey(NBT_RESISTANCE)) mResistance = aNBT.getFloat(NBT_RESISTANCE);
        if (aNBT.hasKey("gtsm.fillEmpty")) mFillEmptyBoxes = aNBT.getBoolean("gtsm.fillEmpty");
        if (aNBT.hasKey("gtsm.offX")) mOffsetX = aNBT.getInteger("gtsm.offX");
        if (aNBT.hasKey("gtsm.offY")) mOffsetY = aNBT.getInteger("gtsm.offY");
        if (aNBT.hasKey("gtsm.offZ")) mOffsetZ = aNBT.getInteger("gtsm.offZ");
        if (aNBT.hasKey("gtsm.radius")) mRadius = aNBT.getInteger("gtsm.radius");
        mRangeEnabled = !aNBT.hasKey("gtsm.rangeEnabled") || aNBT.getBoolean("gtsm.rangeEnabled");
        mShowFrame = !aNBT.hasKey("gtsm.showFrame") || aNBT.getBoolean("gtsm.showFrame");
        mNeedsRescan = T;
        System.out.println("[GTSM-DIAG] TE loaded at (" + xCoord + "," + yCoord + "," + zCoord + ") remote=" + (worldObj != null && worldObj.isRemote) + " nbtId=" + aNBT.getString("id"));
    }

    @Override
    public void writeToNBT2(NBTTagCompound aNBT) {
        super.writeToNBT2(aNBT);
        aNBT.setBoolean("gtsm.fillEmpty", mFillEmptyBoxes);
        aNBT.setInteger("gtsm.offX", mOffsetX);
        aNBT.setInteger("gtsm.offY", mOffsetY);
        aNBT.setInteger("gtsm.offZ", mOffsetZ);
        aNBT.setInteger("gtsm.radius", mRadius);
        aNBT.setBoolean("gtsm.rangeEnabled", mRangeEnabled);
        aNBT.setBoolean("gtsm.showFrame", mShowFrame);
    }

    /**
     * 注意：不要重写 getClientDataPacket —— GT6 用它向客户端同步 TE 身份
     * （PacketSyncDataIDs：注册表 id + mTE id），客户端靠它创建自己的 TE。
     * 返回 null 会导致“服务端有 TE、客户端没有”→ 方块不渲染、不能右键（但碰撞还在）。
     */

    // --------------------------------------------------------------
    //  范围控制（GUI）
    // --------------------------------------------------------------

    /** GUI 控制项 id，与 PacketRangeChange.guiId 对应 */
    public static final byte GUI_OFF_X_INC = 0, GUI_OFF_X_DEC = 1, GUI_OFF_Y_INC = 2, GUI_OFF_Y_DEC = 3,
                           GUI_OFF_Z_INC = 4, GUI_OFF_Z_DEC = 5, GUI_RADIUS_DEC = 6, GUI_RADIUS_INC = 7,
                           GUI_TOGGLE_RANGE = 8, GUI_TOGGLE_FRAME = 9;

    /**
     * 应用 GUI 操作（服务端）。值一律传绝对值；开关类用 value != 0 表示目标状态。
     */
    public void handleGuiAction(byte aGuiId, int aValue) {
        switch (aGuiId) {
            case GUI_OFF_X_INC: case GUI_OFF_X_DEC: mOffsetX = clampOffset(aValue); break;
            case GUI_OFF_Y_INC: case GUI_OFF_Y_DEC: mOffsetY = clampOffset(aValue); break;
            case GUI_OFF_Z_INC: case GUI_OFF_Z_DEC: mOffsetZ = clampOffset(aValue); break;
            case GUI_RADIUS_INC: case GUI_RADIUS_DEC: mRadius = clampRadius(aValue); break;
            case GUI_TOGGLE_RANGE: mRangeEnabled = aValue != 0; break;
            case GUI_TOGGLE_FRAME: mShowFrame = aValue != 0; break;
            default: return;
        }
        mNeedsRescan = T;
        broadcastRange();
    }

    private static int clampOffset(int aValue) { return Math.max(-Config.maxOffset, Math.min(Config.maxOffset, aValue)); }
    private static int clampRadius(int aValue) { return Math.max(0, Math.min(Config.maxRadius, aValue)); }

    /** 生效范围（闭区间，方块坐标）；禁用自定义范围时回退到默认立方体 */
    public int rangeMinX() { return xCoord + (mRangeEnabled ? mOffsetX : 0) - effectiveRadius(); }
    public int rangeMinY() { return yCoord + (mRangeEnabled ? mOffsetY : 0) - effectiveRadius(); }
    public int rangeMinZ() { return zCoord + (mRangeEnabled ? mOffsetZ : 0) - effectiveRadius(); }
    public int rangeMaxX() { return xCoord + (mRangeEnabled ? mOffsetX : 0) + effectiveRadius(); }
    public int rangeMaxY() { return yCoord + (mRangeEnabled ? mOffsetY : 0) + effectiveRadius(); }
    public int rangeMaxZ() { return zCoord + (mRangeEnabled ? mOffsetZ : 0) + effectiveRadius(); }
    public int effectiveRadius() { return mRangeEnabled ? mRadius : Config.scanRadius; }

    // ---- 客户端画框/TESR 只读访问器 ----
    public int offsetX() { return mOffsetX; }
    public int offsetY() { return mOffsetY; }
    public int offsetZ() { return mOffsetZ; }
    public int radius() { return mRadius; }
    public boolean rangeEnabled() { return mRangeEnabled; }
    public boolean showFrame() { return mShowFrame; }

    /** TESR 注册：GT6 在客户端首次注册 MTE 时回调（仅客户端） */
    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void onRegistrationFirstClient(gregapi.block.multitileentity.MultiTileEntityRegistry aRegistry, short aID) {
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(getClass(), gtsm.client.RangeFrameRenderer.INSTANCE);
    }

    /** GT6 图集缝合回调（由 MultiTileEntityBlockInternal.registerIcons 在每次缝合时调用） */
    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void registerIcons(net.minecraft.client.renderer.texture.IIconRegister aIconRegister) {
        ICON_STORAGE_MANAGER.registerIcons(aIconRegister);
        System.out.println("[GTSM-DIAG] GT6-path registerIcons called register=" + (aIconRegister == null ? "null" : aIconRegister.getClass().getName()) + " icon=" + (ICON_STORAGE_MANAGER.getIcon(0) == null ? "NULL" : "ok"));
    }

    /** 立即把本管理器的范围数据同步给 64 格内的玩家（GUI/画框数据源） */
    public void broadcastRange() {
        if (worldObj == null || worldObj.isRemote) return;
        gtsm.network.PacketRangeSync tPacket = new gtsm.network.PacketRangeSync(xCoord, yCoord, zCoord, mOffsetX, mOffsetY, mOffsetZ, effectiveRadius(), mRangeEnabled, mShowFrame);
        for (int i = 0; i < worldObj.playerEntities.size(); i++) {
            Object tPlayer = worldObj.playerEntities.get(i);
            if (!(tPlayer instanceof net.minecraft.entity.player.EntityPlayerMP)) continue;
            net.minecraft.entity.player.EntityPlayerMP tEntityPlayer = (net.minecraft.entity.player.EntityPlayerMP) tPlayer;
            double dX = tEntityPlayer.posX - xCoord, dY = tEntityPlayer.posY - yCoord, dZ = tEntityPlayer.posZ - zCoord;
            if (dX*dX + dY*dY + dZ*dZ > 64.0D * 64.0D) continue;
            gtsm.network.GTSM_Network.WRAPPER.sendTo(tPacket, tEntityPlayer);
        }
    }

    // --------------------------------------------------------------
    //  扫描
    // --------------------------------------------------------------

    /** 重新扫描生效范围内的储物桶（轴对齐盒）。服务端执行 */
    private void rescan() {
        mBoxes.clear();
        if (worldObj == null) return;
        int tMinX = rangeMinX(), tMinY = rangeMinY(), tMinZ = rangeMinZ();
        int tMaxX = rangeMaxX(), tMaxY = rangeMaxY(), tMaxZ = rangeMaxZ();
        for (int tX = tMinX; tX <= tMaxX; tX++)
            for (int tY = tMinY; tY <= tMaxY; tY++)
                for (int tZ = tMinZ; tZ <= tMaxZ; tZ++) {
                    if (tX == xCoord && tY == yCoord && tZ == zCoord) continue; // 跳过自己
                    Object tTileEntity = worldObj.getTileEntity(tX, tY, tZ);
                    if (tTileEntity instanceof MultiTileEntityMassStorage) {
                        mBoxes.add((MultiTileEntityMassStorage) tTileEntity);
                        if (mBoxes.size() >= Config.maxBoxes) return;
                    }
                }
    }

    /** 需要时才重扫；否则保证列表里的桶仍然有效（无效则标记重扫并返回 null） */
    private void ensureValid() {
        if (mNeedsRescan) {
            rescan();
            mNeedsRescan = F;
        }
    }

    /** 取幻影槽对应的储物桶；槽非法或桶已失效时返回 null */
    private MultiTileEntityMassStorage box(int aSlot) {
        if (aSlot < SLOT_BOX_BASE) return null;
        ensureValid();
        int tIndex = aSlot - SLOT_BOX_BASE;
        if (tIndex >= mBoxes.size()) return null;
        MultiTileEntityMassStorage tBox = mBoxes.get(tIndex);
        if (tBox == null || tBox.mIsDead) { // mIsDead 是 GT6 自有公开字段（不参与混淆）
            mNeedsRescan = T;
            return null;
        }
        return tBox;
    }

    /** 储物桶是否被胶带锁定（mMode bit 3）。GT 的 mMode 是公开字段，不参与混淆 */
    private static boolean isTaped(MultiTileEntityMassStorage aBox) {
        return (aBox.mMode & B[3]) != 0;
    }

    // --------------------------------------------------------------
    //  插入路由
    // --------------------------------------------------------------

    /**
     * 把一组物品路由进管理范围内的储物桶，返回没能放进去的剩余物品（null 表示全部放入）。
     * 顺序：先找已有同品种且未锁定的桶 → 若允许，再找空桶。
     */
    public ItemStack routeInsert(ItemStack aStack) {
        if (ST.invalid(aStack)) return null;
        ensureValid();

        // 第一轮：已有同品种的桶
        for (MultiTileEntityMassStorage tBox : mBoxes) {
            if (!tBox.slotHas(1)) continue;
            aStack = insertIntoBox(tBox, aStack);
            if (aStack == null) return null;
        }
        // 第二轮：空桶（把新品种物品固化到空桶里）
        if (mFillEmptyBoxes) {
            for (MultiTileEntityMassStorage tBox : mBoxes) {
                if (tBox.slotHas(1)) continue;
                aStack = insertIntoBox(tBox, aStack);
                if (aStack == null) return null;
            }
        }
        return aStack;
    }

    /** 路由槽准入判断：范围内是否有桶能接收该物品（同品种桶、可打包小单位、或允许时空桶） */
    private boolean canRouteInsert(ItemStack aStack) {
        ensureValid();
        for (MultiTileEntityMassStorage tBox : mBoxes) {
            if (isTaped(tBox)) continue;
            if (!tBox.slotHas(1)) { if (mFillEmptyBoxes) return T; continue; }
            // allowInsertion 覆盖：同品种 + 可打包小单位(小撮/粒 → 桶内品种)
            if (tBox.allowInsertion(aStack)) return T;
        }
        return F;
    }

    /** 兜底：实在放不下的剩余物品掉落为实体（绝不静默删除） */
    private void dropLeftover(ItemStack aStack) {
        if (!ST.valid(aStack) || worldObj == null) return;
        ST.drop(worldObj, getCoords(), aStack.copy());
    }

    /**
     * 把物品放进指定储物桶，返回剩余物品（null 表示全部放入）。
     * 直接调用桶的 {@code insertItems}：胶带锁定时它会原样返回 → 剩余 = 全部。
     * 矿物词典统一：用 {@link OM#get} 把物品转成 GT6 的标准形式（和 GT6 设备走 slot 0 的行为一致）。
     */
    private ItemStack insertIntoBox(MultiTileEntityMassStorage aBox, ItemStack aStack) {
        if (ST.invalid(aStack) || isTaped(aBox)) return aStack;
        ItemStack tToInsert = ST.copy(aStack);
        if (Config.oreDictUnify) {
            tToInsert = OM.get(tToInsert);
            if (ST.invalid(tToInsert)) return aStack; // 统一失败按「剩余」返回，绝不丢物品
        }
        return aBox.insertItems(tToInsert, F); // 返回未放入的剩余
    }

    // --------------------------------------------------------------
    //  IInventory（幻影视图）
    // --------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        ensureValid();
        return SLOT_BOX_BASE + Math.min(mBoxes.size(), Config.maxBoxes);
    }

    @Override
    public ItemStack getStackInSlot(int aSlot) {
        MultiTileEntityMassStorage tBox = box(aSlot);
        if (tBox == null) return null;
        // 返回拷贝，防止外部直接修改桶内部堆叠；数量可能极大（GT6 海量存储），调用方自行处理
        return ST.copy(tBox.slot(1));
    }

    @Override
    public ItemStack decrStackSize(int aSlot, int aAmount) {
        MultiTileEntityMassStorage tBox = box(aSlot);
        if (tBox == null || aAmount <= 0) return null;
        if (isTaped(tBox)) return null; // 锁定的桶不允许抽出
        ItemStack tContent = tBox.slot(1);
        if (!ST.valid(tContent)) return null;
        // 走 GT6 官方取出 API（ITileEntityConnectedInventory），保证 mMode/同步语义正确
        int tWant = Math.min(aAmount, tContent.stackSize);
        int tTaken = tBox.removeStackFromConnectedInventory((byte) 0, ST.amount(tWant, tContent), F);
        if (tTaken <= 0) return null;
        return ST.amount(tTaken, tContent);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int aSlot) {
        return null; // 没有真实库存，关闭时不掉落任何东西
    }

    /**
     * 把幻影槽「设置」为 aStack（null = 清空）。
     *
     * <p><b>关键语义</b>：大量自动化（AE2 的 {@code AdaptorIInventory.removeItems/addItems} 等）
     * 并不调用 {@code decrStackSize}，而是把「取出后剩余的堆」通过本方法写回槽位来完成扣除。
     * 因此本方法必须按<b>目标数量与当前数量的差值</b>同步到储物桶：
     * target &lt; current → 从桶取出差值；target &gt; current → 往桶放入差值。
     * 若把 aStack 当作「要放入的物品」处理，物品会被复制/暴涨（详见 GT6 的
     * {@code MultiTileEntityMassStorage.setInventorySlotContents} 的相同写法）。</p>
     */
    @Override
    public void setInventorySlotContents(int aSlot, ItemStack aStack) {
        if (aSlot == SLOT_ROUTER) {
            dropLeftover(routeInsert(aStack)); // 路由槽：统一路由，剩余掉落
            return;
        }
        MultiTileEntityMassStorage tBox = box(aSlot);
        if (tBox == null) {
            dropLeftover(routeInsert(aStack)); // 对应桶不存在，走路由兜底，剩余掉落
            return;
        }
        ItemStack tCurrent = tBox.slot(1);
        // 关键：只有【同品种】才按“差值”语义解释（自动化靠写回剩余堆来扣除）；
        // 不同品种/不同单位（小撮→粉碎矿石、粒→锭）时按裸数量算差值会完全错乱，
        // 必须按“插入该物品(含 GT6 打包)”处理。
        boolean tSame = ST.valid(tCurrent) && ST.valid(aStack) && ST.equal(tCurrent, aStack);
        if (tSame) {
            int tNow = tCurrent.stackSize;
            int tTarget = aStack.stackSize;
            if (tTarget == tNow) return;
            if (tTarget < tNow) {
                // 取出差值（GT6 官方 API；胶带锁定时返回 0，绝不复制物品）
                tBox.removeStackFromConnectedInventory((byte) 0, ST.amount(tNow - tTarget, tCurrent), F);
            } else {
                // 放入差值
                dropLeftover(routeInsert(insertIntoBox(tBox, ST.amount(tTarget - tNow, aStack))));
            }
            return;
        }
        if (ST.invalid(aStack)) {
            // 清空该桶
            if (ST.valid(tCurrent)) tBox.removeStackFromConnectedInventory((byte) 0, ST.amount(tCurrent.stackSize, tCurrent), F);
            return;
        }
        // 不同品种/单位：插入（GT6 自动打包成桶内品种），放不下再路由，仍放不下降落
        dropLeftover(routeInsert(insertIntoBox(tBox, aStack)));
    }

    @Override
    public String getInventoryName() {
        return LH.get("gtsm.storage.manager", "储物桶管理器");
    }

    @Override
    public boolean hasCustomInventoryName() {
        return T; // getInventoryName 已经返回本地化后的名字
    }

    @Override
    public int getInventoryStackLimit() {
        return 64; // 幻影槽保持常规上限；真实数量由桶决定，抽取走 decrStackSize 分批进行
    }

    @Override
    public void markDirty() {
        // 幻影视图：自身无库存，但自动化（AE2/管道）靠 markDirty 感知变化，必须正常标记
        super.markDirty();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer aPlayer) {
        return worldObj != null && aPlayer != null
                && aPlayer.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= USE_DISTANCE_SQ;
    }

    @Override public void openInventory () { /* 无 GUI */ }
    @Override public void closeInventory() { /* 无 GUI */ }

    @Override
    public boolean isItemValidForSlot(int aSlot, ItemStack aStack) {
        if (ST.invalid(aStack)) return F;
        if (aSlot == SLOT_ROUTER) return canRouteInsert(aStack); // 路由槽：唯一写入入口
        // 幻影槽(1..N)只读：它们是背后储物桶的“视图”，与路由槽指向同一批桶。
        // 若同时允许写入，AE2/管道会对同一物品写两次（路由槽一次+幻影槽一次）→ 数量虚高/复制。
        // 抽取不受影响：decrStackSize/canExtractItem 走另一条路。
        return F;
    }

    // --------------------------------------------------------------
    //  ISidedInventory（自动化兼容的关键：BC 管道 / AE2 等都认这个）
    // --------------------------------------------------------------

    @Override
    public int[] getAccessibleSlotsFromSide(int aSide) {
        ensureValid();
        int tSize = getSizeInventory();
        int[] rSlots = new int[tSize];
        for (int i = 0; i < tSize; i++) rSlots[i] = i; // 六面全开放
        return rSlots;
    }

    @Override
    public boolean canInsertItem(int aSlot, ItemStack aStack, int aSide) {
        return isItemValidForSlot(aSlot, aStack);
    }

    @Override
    public boolean canExtractItem(int aSlot, ItemStack aStack, int aSide) {
        MultiTileEntityMassStorage tBox = box(aSlot);
        return tBox != null && !isTaped(tBox);
    }

    /** AE2 的 IInventoryWrapper 钩子：存储总线逐槽判断可否抽取（胶带锁定的桶拒绝） */
    @Override
    public boolean canRemoveItemFromSlot(int aSlot, ItemStack aStack) {
        MultiTileEntityMassStorage tBox = box(aSlot);
        return tBox != null && !isTaped(tBox);
    }

    // --------------------------------------------------------------
    //  玩家交互
    // --------------------------------------------------------------

    @Override
    public boolean onBlockActivated2(EntityPlayer aPlayer, byte aSide, float aHitX, float aHitY, float aHitZ) {
        if (!isServerSide() || aPlayer == null) return F;
        ItemStack tStack = aPlayer.getCurrentEquippedItem();
        if (ST.invalid(tStack)) {
            // 空手右键：打开范围控制 GUI
            broadcastRange(); // 立刻同步一份，GUI 立即可读
            aPlayer.openGui(gtsm.StorageManager_Mod.instance, gtsm.StorageManager_Mod.GUI_ID_RANGE, worldObj, xCoord, yCoord, zCoord);
            return T;
        }

        boolean rInserted = F;
        if (aPlayer.isSneaking()) {
            // 潜行右键：把背包里所有同品种堆叠全部塞进去
            for (int i = 0; i < aPlayer.inventory.mainInventory.length; i++) {
                ItemStack tInvStack = aPlayer.inventory.mainInventory[i];
                if (ST.invalid(tInvStack)) continue;
                ItemStack tLeftover = routeInsert(ST.copy(tInvStack));
                if (tLeftover == null) {
                    aPlayer.inventory.mainInventory[i] = null;
                    rInserted = T;
                } else if (tLeftover.stackSize < tInvStack.stackSize) {
                    tInvStack.stackSize = tLeftover.stackSize;
                    rInserted = T;
                }
            }
        } else {
            // 右键：放入手持物品
            ItemStack tLeftover = routeInsert(ST.copy(tStack));
            if (tLeftover == null) {
                tStack.stackSize = 0;
                rInserted = T;
            } else if (tLeftover.stackSize < tStack.stackSize) {
                tStack.stackSize = tLeftover.stackSize;
                rInserted = T;
            }
        }
        if (rInserted) {
            playCollect();
            return T;
        }
        return F;
    }

    // --------------------------------------------------------------
    //  GT 工具交互（贴合 GT6 习惯）
    // --------------------------------------------------------------

    @Override
    public long onToolClick(String aTool, long aRemainingDurability, long aQuality, Entity aPlayer, List<String> aChatReturn, IInventory aPlayerInventory, boolean aSneaking, ItemStack aStack, byte aSide, float aHitX, float aHitY, float aHitZ) {
        if (aTool.equals(TOOL_screwdriver)) {
            mFillEmptyBoxes = !mFillEmptyBoxes;
            if (aChatReturn != null) aChatReturn.add(mFillEmptyBoxes
                    ? LH.get("gtsm.chat.fill.on" , "将允许把新品种物品放入空储物桶")
                    : LH.get("gtsm.chat.fill.off", "将不会占用空储物桶"));
            return 1;
        }
        if (aTool.equals(TOOL_softhammer)) {
            mNeedsRescan = T;
            rescan();
            mNeedsRescan = F;
            if (aChatReturn != null) aChatReturn.add(LH.get("gtsm.chat.rescan", "已重新扫描区域，找到") + " " + mBoxes.size() + " " + LH.get("gtsm.chat.boxes", "个储物桶"));
            return 1;
        }
        if (aTool.equals(TOOL_magnifyingglass)) {
            if (aChatReturn != null) {
                ensureValid();
                int tUsed = 0, tLocked = 0;
                for (MultiTileEntityMassStorage tBox : mBoxes) {
                    if (tBox.slotHas(1)) tUsed++;
                    if (isTaped(tBox)) tLocked++;
                }
                aChatReturn.add(LH.get("gtsm.storage.manager", "储物桶管理器"));
                aChatReturn.add(LH.get("gtsm.chat.scope", "储物桶") + ": " + mBoxes.size() + " (" + LH.get("gtsm.chat.used", "已占用") + ": " + tUsed + ", " + LH.get("gtsm.chat.empty", "空闲") + ": " + (mBoxes.size() - tUsed) + ", " + LH.get("gtsm.chat.locked", "已锁定") + ": " + tLocked + ")");
                aChatReturn.add((mRangeEnabled ? LH.get("gtsm.chat.range.custom", "自定义范围") : LH.get("gtsm.chat.range.default", "默认范围")) + ": (" + rangeMinX() + ", " + rangeMinY() + ", " + rangeMinZ() + ") - (" + rangeMaxX() + ", " + rangeMaxY() + ", " + rangeMaxZ() + ")  " + LH.get("gtsm.chat.radius", "半径") + ": " + effectiveRadius() + "  " + LH.get("gtsm.chat.offset", "偏移") + ": (" + (mRangeEnabled ? mOffsetX : 0) + ", " + (mRangeEnabled ? mOffsetY : 0) + ", " + (mRangeEnabled ? mOffsetZ : 0) + ")");
                aChatReturn.add(LH.get("gtsm.chat.oredict", "矿物词典统一") + ": " + Config.oreDictUnify + "  " + LH.get("gtsm.chat.fill", "填充空桶") + ": " + mFillEmptyBoxes);
            }
            return 1;
        }
        return 0;
    }

    @Override
    public void addToolTips(List<String> aList, ItemStack aStack, boolean aF3_H) {
        aList.add(LH.Chat.CYAN + LH.get("gtsm.tooltip.1", "管理范围内的GT6储物箱，并暴露统一的自动化接口"));
        aList.add(LH.Chat.GRAY + LH.get("gtsm.tooltip.2", "右键：插入手中物品 | 潜行+右键：插入所有匹配物品"));
        aList.add(LH.Chat.DGRAY + LH.get("gtsm.tooltip.3", "螺丝刀：切换是否填充空箱 | 软锤：重新扫描 | 放大镜：查看详情"));
        aList.add(LH.Chat.DGRAY + LH.get("gtsm.tooltip.4", "空手右键：打开范围配置界面"));
    }

    // --------------------------------------------------------------
    //  Tick / 邻居变化
    // --------------------------------------------------------------

    @Override
    public void onTick(long aTimer, boolean aIsServerSide) {
        if (!aIsServerSide) {
            // 客户端诊断：方块是否在、是什么、图标是否有效
            if (aTimer % 100 == 0 && worldObj != null) {
                Block tBlock = worldObj.getBlock(xCoord, yCoord, zCoord);
                System.out.println("[GTSM-DIAG] CLIENT TE@(" + xCoord + "," + yCoord + "," + zCoord + ") blockId=" + net.minecraft.block.Block.getIdFromBlock(tBlock)
                        + " block=" + (tBlock == null ? "null" : tBlock.getClass().getSimpleName())
                        + " blockClass=" + (tBlock == null ? "null" : tBlock.getClass().getName())
                        + " icon=" + (ICON_STORAGE_MANAGER.getIcon(0) == null ? "NULL" : "ok")
                        + " texFile=" + (ICON_STORAGE_MANAGER.getTextureFile() == null ? "null" : ICON_STORAGE_MANAGER.getTextureFile().toString()));
            }
            return;
        }
        if (mNeedsRescan || aTimer % 256 == 0) {
            rescan();
            mNeedsRescan = F;
        }
        // 每 10 tick 向视野内玩家同步一次范围数据（GUI 镜像 + 画框）
        if (aTimer % 10 == 0) {
            broadcastRange();
        }
        // 比较器输出变化时刷新一次方块更新（相邻比较器/红石需要）
        int tOutput = getComparatorInputOverride((byte) 0);
        if (tOutput != mLastComparatorOutput) {
            mLastComparatorOutput = tOutput;
            causeBlockUpdate();
        }
    }

    @Override
    public void onNeighborBlockChange(net.minecraft.world.World aWorld, Block aBlock) {
        super.onNeighborBlockChange(aWorld, aBlock);
        mNeedsRescan = T; // 旁边摆了/拆了桶，重新扫描
    }

    // --------------------------------------------------------------
    //  渲染与属性
    // --------------------------------------------------------------

    @Override
    public ITexture getTexture(Block aBlock, int aRenderPass, byte aSide, boolean[] aShouldSideBeRendered) {
        if (aSide < 0 || aSide >= aShouldSideBeRendered.length || !aShouldSideBeRendered[aSide]) return null;
        return BlockTextureDefault.get(ICON_STORAGE_MANAGER);
    }

    @Override
    public int getRenderPasses(Block aBlock, boolean[] aShouldSideBeRendered) {
        return 1;
    }

    @Override
    public boolean setBlockBounds(Block aBlock, int aRenderPass, boolean[] aShouldSideBeRendered) {
        return F; // 标准立方体
    }

    @Override
    public float getBlockHardness() {
        return mHardness;
    }

    @Override
    public float getExplosionResistance() {
        return mResistance;
    }

    @Override
    public int getComparatorInputOverride(byte aSide) {
        ensureValid();
        if (mBoxes.isEmpty()) return 0;
        int tUsed = 0;
        for (MultiTileEntityMassStorage tBox : mBoxes) if (tBox.slotHas(1)) tUsed++;
        return (int) Math.round(tUsed * 15.0D / mBoxes.size());
    }
}
