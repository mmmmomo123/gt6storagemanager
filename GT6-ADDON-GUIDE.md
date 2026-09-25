# GT6 (GregTech 6, 1.7.10) 附属 Mod 开发避坑指南

> 基于 GTSM（储物桶管理器）从 0 到 v1.0.9 的全部实战经验整理。
> 新开对话写别的附属 mod 时，先让 AI 读这份文档，可避开 90% 的坑。

---

## 一、工程骨架

- **JDK 8 硬性要求**（1.7.10 mod 统一）；Gradle wrapper 用 2.14.1 + **ForgeGradle 1.2**（`net.minecraftforge.gradle:ForgeGradle:1.2-SNAPSHOT`，repo `http://files.minecraftforge.net/maven`）。
- `mcmod.info` + `@Mod(..., dependencies = "required-after:gregapi_post")`——**必须依赖 gregapi_post**，这是 GT6 官方推荐的后置加载顺序。
- MTE 注册两段式（**顺序不能乱**）：
  1. `onModPreInit2`：`new MultiTileEntityRegistry(REGISTRY_NAME)` **+ `MultiTileEntityBlock.getOrCreate(...)`** ← 方块只能在 preInit 建！Init 里建会崩：`IllegalStateException: Blocks can only be initialised within preInit!`
  2. `onModInit2`：`tRegistry.add(名称, 分类, mTE_ID, 创造栏ID, TE类, meta, 堆叠数, tBlock, NBT参数, 配方...)`（OreDict 原料要等 Init 才齐）
- `Abstract_Mod` 有 8 个抽象方法必须全实现：`getModID/getModName/getModNameForLog/getProxy` + `onModPreInit2/onModInit2/onModPostInit2` + `onModServerStarting2/Started2/Stopping2/Stopped2`（缺一个编译就报）。
- `getTileEntityName()` 不能以 `gt.` 开头（GT6 保留前缀）。

## 二、构建期三大坑（附修复代码）

### 1. ForgeGradle 1.2 写死的 Mojang 下载地址已 404
`http://s3.amazonaws.com/Minecraft.Download/versions/1.7.10/1.7.10.jar` 早被 Mojang 关闭。在 build.gradle 打补丁：

```groovy
afterEvaluate {
    tasks.withType(net.minecraftforge.gradle.tasks.abstractutil.DownloadTask).all { t ->
        def u = t.url == null ? "" : t.url.toString()
        if (u.startsWith("http://s3.amazonaws.com/Minecraft.Download")) {
            def nu = null
            if (u.contains("minecraft_server"))            nu = "https://launcher.mojang.com/v1/objects/952438ac4e01b4d115c5fc38f891710c4941df29/server.jar"  // 注意：server 判断必须在 .jar 判断之前！
            else if (u.endsWith("1.7.10.json"))            nu = "https://piston-meta.mojang.com/v1/packages/ed5d8789ed29872ea2ef1c348302b0c55e3f3468/1.7.10.json"
            else if (u.endsWith("1.7.10.jar"))             nu = "https://launcher.mojang.com/v1/objects/e80d9b3bf5085002218d4be59e668bac718abbc6/client.jar"
            if (nu != null) t.url = new net.minecraftforge.gradle.delayed.DelayedString(project, nu)  // 必须包装 DelayedString，直接赋 String 会 GroovyCastException
        }
    }
}
```
新地址从官方 manifest 查：`https://piston-meta.mojang.com/mc/game/version_manifest.json`（piston-meta=元数据，launcher.mojang.com=/v1/objects/…=文件本体）。

### 2. GT6 继承链引用的外部 API（编译报 `cannot access IMovableTile` 等）
`TileEntityBase01Root implements appeng.api.movable.IMovableTile`，还有 `ic2.api.tile.IWrenchable`、`ic2.api.recipe.IMachineRecipeManager`、`buildcraft.api.*`、`cofh.api.*`、`mekanism.api.*`、`codechicken.nei.api.API` 等。**加空桩**（放 src/main/java 下，用 jar 排除）：

```java
package appeng.api.movable;  public interface IMovableTile { boolean prepareToMove(); void doneMoving(); }
```
```groovy
jar { exclude 'appeng/**'; exclude 'ic2/**'; exclude 'buildcraft/**'; exclude 'cofh/**'; exclude 'codechicken/**'; exclude 'mekanism/**' }
```
注意：报 cannot access X 会**级联**把整条继承链的 @Override 全炸掉，先修第一个再看剩余。

### 3. GT6 各版本 API 差异（6.17.06 实测）
- `MT.DATA.CIRCUITS` 数组实际排布：`[0]Primitive [1]Basic(基础) [2]Good(低级) [3]Advanced [4]Elite [5]Master [6]Ultimate(究极) [7..15]Quantum`——别凭印象猜索引，用 `javap -c 'gregapi.data.MT$DATA'` 看 putstatic 顺序。
- `TileEntityBase05` 的 `func_70298_a` 就是 decrStackSize，语义正常；`slot(int)/slotHas(int)` 在 Base05 上。
- 图标：6.17.06 起 `IIconContainer` **没有 run()**；`GT_API.sBlockIconload` 是 `Set<Runnable>`。
- 配方引用别的 MTE 物品：`MultiTileEntityRegistry.getRegistry("gt.multitileentity").getItem(32751)`（GT6 单一注册表，脚本源码在 GitHub `GregTech6/gregtech6` 的 `Loader_MultiTileEntities.java`）。
- 查任何 API 签名：`javap -p -classpath <gt6.jar> <全类名>`。

## 三、MTE 生存法则（最痛的五条）

1. **永远不要重写 `getClientDataPacket()` 返回 null！** GT6 靠它（`PacketSyncDataIDs`：注册表 id + mTE id）向客户端同步 TE 身份。返回 null 的后果：服务端 TE 正常（功能/碰撞都在），**客户端永不创建 TE → 方块不渲染、不能右键**。新放置时走方块更新包看不到问题，重进游戏必炸。血的教训。
2. **图标必须每次图集缝合重新注册**。1.7.10 每次进游戏都重建方块图集，`GT_API.sBlockIconload` 一次性队列只入队一次 → 重载后失效 → “隐形但有碰撞”。正确做法双保险：
   ```java
   // a) TE 实现 IMTE_RegisterIcons（自有方块注册表时，块每次缝合会回调）
   @Override public void registerIcons(IIconRegister r) { ICON.registerIcons(r); }
   // b) 客户端 Forge 事件兜底（不依赖块注册表）
   @SubscribeEvent public void onStitch(TextureStitchEvent.Pre e) {
       if (e.map.getTextureType() == 0) ICON.registerIcons(e.map);   // 0=方块图集
   }
   // 在 Proxy_Client 构造里 MinecraftForge.EVENT_BUS.register(new IconStitchHandler())
   ```
3. **不要把 MTE 注册到 GT6 的机器块上**（`getOrCreate("gregtech","machine",...)` 会返回 GT6 的块）。我们的 block 元数据（add() 第 6 参）若传 0，会跟 GT6 自家机器撞车 → 重进游戏位置被当成 GT6 机器 → 不渲染/不能交互。**用自己 modid 的 getOrCreate**。顺带：旧存档（建世界时没有本 mod）里新块 ID 不在世界 ID 映射表中会漂移，但只要不改注册参数，映射会稳定（日志 `Fixed block id mismatch ... (init) -> (map)` 可查）。
4. **方块只在 preInit 创建**（见一）。
5. **物品显示名要中文/稳定：实现 `IMTE_GetItemName.getItemName(stack, default)` 代码直返**，别依赖 lang 文件（en_us locale 下 lang key 查找链容易断）。

## 四、AE2 交互（GTNH rv3-beta 实测，幻影库存类 mod 通用）

AE2 rv3 的 `AdaptorIInventory` **不走 decrStackSize**：
- **取出（ME→世界）**：读 `getStackInSlot` → 调 `setInventorySlotContents(slot, 剩余堆)` 写回扣除。
- **放入（世界→ME）**：`isItemValidForSlot` 准入 → 空槽直接 `setInventorySlotContents(slot, stack)`；同品种且未满才合并写回。

所以 **`setInventorySlotContents` 必须实现 GT6 官方储物桶的“差值语义”**：

```java
public void setInventorySlotContents(int slot, ItemStack aStack) {
    var box = box(slot);
    ItemStack cur = box.slot(1);
    boolean same = ST.valid(cur) && ST.valid(aStack) && ST.equal(cur, aStack);
    if (same) {  // 同品种才按数量差值解释
        int now = cur.stackSize, target = aStack == null ? 0 : aStack.stackSize;
        if (target < now) box.removeStackFromConnectedInventory((byte)0, ST.amount(now - target, cur), F); // 取
        else if (target > now) dropLeftover(routeInsert(ST.amount(target - now, aStack)));                // 放
    } else if (!ST.valid(aStack)) {  // 清空
        box.removeStackFromConnectedInventory((byte)0, ST.amount(cur.stackSize, cur), F);
    } else {  // 不同品种/单位（小撮 vs 锭）→ 按插入+打包处理，绝不能按裸数量算差
        dropLeftover(routeInsert(insertIntoBox(box, aStack)));
    }
}
```

配套三原则：
- **写入路径唯一**：幻影槽（背后桶的视图）只读，`isItemValidForSlot` 返回 F；写入全走一个“路由槽”（否则 AE2/管道会对同一物品写两次 → 1→3 虚高）。抽取走 decrStackSize/canExtractItem，不受影响。
- **打包准入**：想支持 GT6 的“小撮→粉碎矿石、粒→锭”打包，准入判断用桶自己的 `box.allowInsertion(stack)`（内含同品种/可打包单位/胶带锁定判定），**不要用 ST.equal 死判**。
- **多实例防重复认领**：多个管理器扫描范围重叠时，同一桶被多方认领 → AE 数量×管理器数。每个桶归**最近的管理器**（距离比较 + 坐标字典序兜底，保证各管理器判定一致）。

## 五、渲染与客户端

- **世界内画框/辅助渲染用 TESR**（`ClientRegistry.bindTileEntitySpecialRenderer(TE.class, renderer)` + `renderTileEntityAt`），**不要用 RenderWorldLastEvent**——GTNH 的 Angelica/Hodgepodge 渲染管线会改变世界事件的模型视图语义（绝对/相对坐标都不稳，表现为框“跟着人物移动”或直接消失）。TESR 收到的 (x,y,z) 就是“Tile-相机”相对值，稳定且天然世界锚定。
- TE 客户端钩子：`onRegistrationFirstClient`（@SideOnly(CLIENT)）。
- GUI 总线用 FML `SimpleNetworkWrapper` + 自定 IMessage（客户端→服务端传 GUI 操作）；C→S 包里带 TE 坐标，服务端查 TE 应用；**网络线程里只做幂等字段写入**（1.7.10 FML 的消息线程语义天然安全，纯配置类数据无需切主线程）。

## 六、GUI（vanilla 容器 + 暗色面板）

- 圆角深色卡片：程序生成 176×N PNG（PIL 两行 rounded_rectangle），`drawTexturedModalRect` 铺底 + 按钮/文字画在上面。别用 GT6 的 Default.png 当底（网格背景糊文字）。
- **坐标纪律（血泪）**：绘制用 `guiLeft/guiTop + 相对偏移`；命中检测也必须用 `mouse - guiLeft/guiTop` 后的**相对坐标**。两套基准混用 = 悬停高亮和点击落点错位。
- 悬停高亮：drawGuiContainerBackgroundLayer 的 (mouseX,mouseY) 参数直接可用，和 mouseClicked 用同一套 inRect 常量。
- 组件：胶囊步进器（一个连续容器 + 低透明分隔线 + 同 hover 高亮）、拨动开关（pill+ knob，ON=柔和绿 #3FB96F，忌高饱和纯色）、范围数值用 Min/Max 分行。
- 本地即时预测：点击后先在客户端缓存里改值，服务端同步包（~1 tick）到达后覆盖，保持手感。

## 七、汉化（针对 en_us locale 的整合包）

三重保险全上：
1. GUI 文本硬编码中文。
2. `IMTE_GetItemName` 代码直返中文名。
3. lang 文件（zh_cn + en_us 都放中文）+ `LH.add` 默认值改中文（GT6 的 translate 查找链：LanguageRegistry → StatCollector → LH 的 BACKUPMAP → 传入的 default，任一路通即可）。
- `ItemData` 世界映射表：GTNH 的 level.dat_mcr 按 mod 分节存 ID，**建世界时没有的 mod 不在表里**——表现就是旧块 ID 漂移，别轻率下“变空气”结论（先看有没有碰撞）。

## 八、调试方法（性价比最高的一条）

**客户端/服务端双端打点**。在 `readFromNBT2` / `onTick(aTimer, aIsServerSide)` 里按 `remote` 标记打印位置、方块 ID、方块类名、图标状态。本次“重进游戏隐形”就是靠 `[Server thread] TE loaded` 有、`[Client thread]` 一次没有，直接锁定“客户端 TE 未创建”，再顺藤摸到 getClientDataPacket。日志关键字 `[GTSM-DIAG]` 前缀，方便用户 grep 后整段发回。

## 九、GitHub Actions 构建（JDK 8）

```yaml
- uses: actions/setup-java@v4  with: {java-version: '8', distribution: 'temurin'}
- run: sed -i 's/\r$//' gradlew        # Windows 提交的 gradlew 修 CRLF
- run: ./gradlew setupCIWorkspace --no-daemon --stacktrace
- run: ./gradlew build --no-daemon --stacktrace
- uses: actions/upload-artifact@v4  with: {name: gtsm-jar, path: build/libs/*.jar}
```
- `runs-on: ubuntu-22.04`；Gradle daemon 关掉；setupCIWorkspace 每次都要重新下载 MC/反编译（约 1-2 分钟），没有缓存也很快。
- jar 名带版本号：build.gradle `version = "x.y.z"` → `archivesBaseName-version.jar`。

## 十、配方速查（写新机器常用）
```java
'P', OP.plate.dat(MT.Al)                     // XX板
'C', OP.screw.dat(MT.Steel)                  // 螺丝刀
'B', MT.DATA.CIRCUITS[2]                     // 高级电路起
tRegistry.add("名称", "分类", ID, 0, TE类, 0, 64, tBlock,
    UT.NBT.make(CS.NBT_TEXTURE, "mytex", CS.NBT_HARDNESS, 6.0F, CS.NBT_RESISTANCE, 6.0F),
    "PPP","CMC","PPP", 'P', OP.plate.dat(MT.Al), 'C', tPipe, 'M', OP.casingMachine.dat(MT.Steel));
// 多份配方（如 t3+ 全部电路）：首份用 add()，其余 CR.shaped(self, CR.DEF, ...)
```

> 经验总结：**MTE 附属 mod 的坑 80% 集中在“客户端/服务端双端一致性”和“GT6 继承链的隐式契约”**。凡是新写一个接口实现，先问：这个方法 GT6 会在哪一侧、什么时机调用？返回值 null/默认值会不会掐断某个同步链？
