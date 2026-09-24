package gtsm.tile;

import gregapi.GT_API;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_AddToolTips;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_GetBlockHardness;
import gregapi.block.multitileentity.IMultiTileEntity.IMTE_GetComparatorInputOverride;
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
                   IMTE_GetBlockHardness, IMTE_GetExplosionResistance, IMTE_GetComparatorInputOverride {

    /** 通用路由槽的下标 */
    public static final int SLOT_ROUTER = 0;
    /** 第一个储物桶幻影槽的下标（= 槽 1） */
    public static final int SLOT_BOX_BASE = 1;
    /** 箱子 GUI/自动化里玩家与箱体的交互距离上限（原版常量） */
    private static final double USE_DISTANCE_SQ = 64.0D;

    /** 储物桶贴图容器（由 GT6 的 GT_API.sBlockIconload 在客户端自动注册） */
    public static IIconContainer ICON_STORAGE_MANAGER = new IIconContainer() {
        private IIcon mIcon;
        { if (GT_API.sBlockIconload != null) GT_API.sBlockIconload.add(this); } // 注册到客户端贴图加载队列

        @Override public IIcon getIcon(int aRenderPass) { return mIcon; }
        @Override public short[] getIconColor(int aRenderPass) { return UNCOLOURED; }
        @Override public int getIconPasses() { return 1; }
        @Override public boolean isUsingColorModulation(int aRenderPass) { return T; }
        @Override public ResourceLocation getTextureFile() { return TextureMap.locationBlocksTexture; }
        @Override public void registerIcons(IIconRegister aIconRegister) { mIcon = aIconRegister.registerIcon(StorageManager_Mod.MOD_ID + ":" + StorageManager_Mod.TEXTURE_NAME); }
        @Override public void run() { registerIcons(GT_API.sBlockIcons); }
    };

    /** 当前管理的储物桶列表（列表下标 i 对应幻影槽 SLOT_BOX_BASE + i） */
    private final List<MultiTileEntityMassStorage> mBoxes = new ArrayListNoNulls<>();

    private boolean mNeedsRescan = T;
    private boolean mFillEmptyBoxes = Config.fillEmptyBoxes;
    private float mHardness = 6.0F, mResistance = 6.0F;

    /** 上次比较器输出，用于检测变化时刷新红石（避免每 tick 刷方块更新） */
    private int mLastComparatorOutput = -1;

    // --------------------------------------------------------------
    //  注册与持久化
    // --------------------------------------------------------------

    @Override
    public String getTileEntityName() {
        return "gtsm.storage.manager"; // 不能以 "gt." 开头（GT6 保留）
    }

    @Override
    public void readFromNBT2(NBTTagCompound aNBT) {
        super.readFromNBT2(aNBT);
        if (aNBT.hasKey(NBT_HARDNESS)) mHardness = aNBT.getFloat(NBT_HARDNESS);
        if (aNBT.hasKey(NBT_RESISTANCE)) mResistance = aNBT.getFloat(NBT_RESISTANCE);
        if (aNBT.hasKey("gtsm.fillEmpty")) mFillEmptyBoxes = aNBT.getBoolean("gtsm.fillEmpty");
        mNeedsRescan = T;
    }

    @Override
    public void writeToNBT2(NBTTagCompound aNBT) {
        super.writeToNBT2(aNBT);
        aNBT.setBoolean("gtsm.fillEmpty", mFillEmptyBoxes);
    }

    /** 无 GUI、无客户端同步需求，直接返回 null（onTickCheck 默认 false，不会被调用） */
    @Override
    public IPacket getClientDataPacket(boolean aSendAll) {
        return null;
    }

    // --------------------------------------------------------------
    //  扫描
    // --------------------------------------------------------------

    /** 重新扫描范围内的储物桶。在服务端运行；客户端只有渲染需要，不扫描也不会出错 */
    private void rescan() {
        mBoxes.clear();
        if (worldObj == null) return;
        int tRadius = Config.scanRadius;
        for (int tX = -tRadius; tX <= tRadius; tX++)
            for (int tY = -tRadius; tY <= tRadius; tY++)
                for (int tZ = -tRadius; tZ <= tRadius; tZ++) {
                    if (tX == 0 && tY == 0 && tZ == 0) continue; // 跳过自己
                    Object tTileEntity = worldObj.getTileEntity(xCoord + tX, yCoord + tY, zCoord + tZ);
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
        if (tBox == null || ((TileEntity) tBox).isDead()) { // isDead 是 MC 方法 → 强转原版类型调用
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
        return ((IInventory) tBox).decrStackSize(1, aAmount); // MC 方法 → 强转原版类型调用
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int aSlot) {
        return null; // 没有真实库存，关闭时不掉落任何东西
    }

    @Override
    public void setInventorySlotContents(int aSlot, ItemStack aStack) {
        if (aSlot == SLOT_ROUTER) {
            routeInsert(aStack); // 路由槽：统一路由
            return;
        }
        MultiTileEntityMassStorage tBox = box(aSlot);
        if (tBox == null) {
            routeInsert(aStack); // 对应桶不存在，走路由兜底
            return;
        }
        ItemStack tLeftover = insertIntoBox(tBox, aStack); // 幻影槽：放进对应桶
        if (tLeftover != null) routeInsert(tLeftover);     // 该桶放不进就尝试其他桶，避免丢失
    }

    @Override
    public String getInventoryName() {
        return LH.get("gtsm.storage.manager", "Storage Manager");
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
        // 管理器没有自己的库存，桶的同步由桶自己负责，这里无需做事
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
        if (aSlot == SLOT_ROUTER) return T; // 路由槽收一切
        MultiTileEntityMassStorage tBox = box(aSlot);
        if (tBox == null || isTaped(tBox)) return F;
        if (!tBox.slotHas(1)) return T; // 空桶接受任何东西
        return ST.equal(aStack, tBox.slot(1)); // 已有品种的桶只接受同品种
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

    // --------------------------------------------------------------
    //  玩家交互
    // --------------------------------------------------------------

    @Override
    public boolean onBlockActivated2(EntityPlayer aPlayer, byte aSide, float aHitX, float aHitY, float aHitZ) {
        if (!isServerSide() || aPlayer == null) return F;
        ItemStack tStack = aPlayer.getCurrentEquippedItem();
        if (ST.invalid(tStack)) return F;

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
                    ? LH.get("gtsm.chat.fill.on" , "Will fill empty Storage Boxes with new Item Types")
                    : LH.get("gtsm.chat.fill.off", "Won't fill empty Storage Boxes with new Item Types"));
            return 1;
        }
        if (aTool.equals(TOOL_softhammer)) {
            mNeedsRescan = T;
            rescan();
            mNeedsRescan = F;
            if (aChatReturn != null) aChatReturn.add(LH.get("gtsm.chat.rescan", "Rescanned Area, found") + " " + mBoxes.size() + " " + LH.get("gtsm.chat.boxes", "Storage Boxes"));
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
                aChatReturn.add(LH.get("gtsm.storage.manager", "Storage Manager"));
                aChatReturn.add(LH.get("gtsm.chat.scope", "Storage Boxes") + ": " + mBoxes.size() + " (" + LH.get("gtsm.chat.used", "in use") + ": " + tUsed + ", " + LH.get("gtsm.chat.empty", "empty") + ": " + (mBoxes.size() - tUsed) + ", " + LH.get("gtsm.chat.locked", "locked") + ": " + tLocked + ")");
                aChatReturn.add(LH.get("gtsm.chat.range", "Range") + ": " + Config.scanRadius + "  " + LH.get("gtsm.chat.oredict", "OreDict Unify") + ": " + Config.oreDictUnify + "  " + LH.get("gtsm.chat.fill", "Fill Empty") + ": " + mFillEmptyBoxes);
            }
            return 1;
        }
        return 0;
    }

    @Override
    public void addToolTips(List<String> aList, ItemStack aStack, boolean aF3_H) {
        aList.add(Chat.CYAN + LH.get("gtsm.tooltip.1", "Manages GT6 Storage Boxes in range and exposes one unified Automation Interface"));
        aList.add(Chat.GRAY + LH.get("gtsm.tooltip.2", "Rightclick to insert held Itemstack, Sneak-Rightclick to insert all matching Stacks"));
        aList.add(Chat.DGRAY + LH.get("gtsm.tooltip.3", "Screwdriver: toggle filling empty Boxes | Soft Hammer: rescan | Magnifying Glass: details"));
    }

    // --------------------------------------------------------------
    //  Tick / 邻居变化
    // --------------------------------------------------------------

    @Override
    public void onTick(long aTimer, boolean aIsServerSide) {
        if (!aIsServerSide) return;
        if (mNeedsRescan || aTimer % 256 == 0) {
            rescan();
            mNeedsRescan = F;
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
    public float getExplosionResistance2() {
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
