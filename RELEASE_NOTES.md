# GTSM v1.0.9 - GT6 储物桶管理器（汇总版）

Minecraft 1.7.10 + GregTech 6 (6.17.06) 附属 Mod。把管理范围内 GT6 储物桶对外暴露成统一 `IInventory`/`ISidedInventory`（幻影视图，自身零库存），供 BC 管道 / AE2 / GT 输送带统一存取。

## 本版包含：多管理器重复计数修复（v1.0.9 新增）

- **多管理器扫描范围重叠 → 物品数量×管理器数量**：同一储物桶被多个管理器同时认领，AE2 网络重复计数。
  **修复**：每个储物桶只归「最近的管理器」所有（距离近者赢，距离相同按坐标字典序，判定全局一致无歧义）。多个管理器可用 GUI 偏移量把范围错开，分管不同区域的桶。

---

## 历史问题与修复记录（v1.0.0 → v1.0.9 全记录）

### 环境/构建
| 问题 | 原因 | 修复 |
|---|---|---|
| 游戏起不来：`Blocks can only be initialised within preInit!` | v1.0.4 把方块创建放进了 Init 阶段，GT6 强制只能在 preInit 建方块 | 方块创建移回 preInit（v1.0.5 热修） |
| ForgeGradle 1.2 下载 404 | Mojang 早已关闭 `s3.amazonaws.com/Minecraft.Download` | build.gradle 里把下载 URL 修补到 piston-meta/launcher.mojang.com（DelayedString 包装，含 minecraft_server 匹配顺序修正） |
| 编译报错一堆（`IMovableTile`/`IWrenchable` 等不存在） | GT6 release jar 的继承链引用 AE2/IC2/BC/CoFH/NEI/Mekanism API，classpath 上没有 | 添加外部 Mod API 编译期桩（空实现，不打进 jar）；顺带修正 `IIconContainer` 无 `run()`、`sBlockIconload` 是 `Set<Runnable>`、`getExplosionResistance2`→`getExplosionResistance`、补全 `Abstract_Mod` 的 5 个抽象方法、低级/究极电路索引对齐 GT6 数组 |

### AE2 存取（最核心，反复多轮）
| 问题 | 原因 | 修复 |
|---|---|---|
| AE 读取后储物桶不减少、还能凭空取出（**复制物品**） | AE2 rv3 `AdaptorIInventory.removeItems` **不调用 decrStackSize**，而是 `setInventorySlotContents(slot, 剩余堆)` 写回扣除；原实现把剩余堆当成要放入的物品又塞回桶里（1000 取 64 → 写回 936 → 桶变 1936） | `setInventorySlotContents` 改为**差值语义**（GT6 官方储物桶同款）：target<当前→取差值；target>当前→放差值（v1.0.0） |
| 往 AE 放物品虚无消失 | 路由槽兜底丢失：放不下时代码直接丢弃剩余 | 路由槽加准入门槛 + 剩余物品**掉落成实体**，绝不静默删除 |
| 小撮/粒无法通过 AE2 打包进桶 | `isItemValidForSlot` 用 `ST.equal` 死板判断（只有同品种才准入） | 改用 GT6 官方 `allowInsertion()`：同品种、可打包小单位（小撮→粉碎矿石、粒→锭）、胶带锁定全按官方语义 |
| 取出再放入 AE 数量虚高（1→3） | 同一物品有**两条写入路径**：路由槽(0)和幻影槽(1..N)都能收，AE2 写两次 | **幻影槽只读**（纯展示视图），路由槽(0)唯一写入入口；抽取不受影响（v1.0.8） |
| 多管理器时物品×管理器数量 | 多个管理器范围重叠，同一桶被重复认领 | 储物桶归最近的管理器所有（v1.0.9） |
| AE2 存取后客户端/服务端计数不一致 | `setInventorySlotContents` 差值语义假设同品种，写入不同单位时按裸数量误判（36<100 触发“抽取”） | 先判同品种再走差值；不同品种/单位走打包插入（v1.0.7） |

### 重进游戏方块“消失”（最诡异，日志实锤）
最终根因是**两个 bug 叠加**：
1. **客户端 TE 从未创建**（真凶）：我们把 `getClientDataPacket()` 重写为返回 null —— GT6 靠这个包（PacketSyncDataIDs：注册表 id + mTE id）向客户端同步 TE 身份。掐断后：服务端有 TE（功能正常、有碰撞），客户端没有 TE → 不渲染、不能右键。新放置时走方块更新包所以当场能看到，重进游戏才暴露。**修复：删除该重写**（v1.0.8）。
2. **图标只注册一次**：1.7.10 每次进游戏都重建方块图集，旧实现用 `GT_API.sBlockIconload` 一次性队列，重载后图标失效 → 隐形但有碰撞。**修复：`TextureStitchEvent.Pre` 每次缝合重新注册 + TE 实现 `IMTE_RegisterIcons` 双保险**（v1.0.3 起）。
3. 中途走过的弯路：v1.0.2/v1.0.3 曾把 MTE 注册到 GT6 机器块上图 ID 稳定，结果我们的 block 元数据 0 与 GT6 自家机器撞车（重进游戏被当成 GT6 机器，不渲染/不能交互），v1.0.4 回退自有方块。

### 界面与汉化
- GUI 重设计：圆角深色卡片（程序生成 176×200 贴图）、胶囊步进器、拨动开关、Min/Max 范围代码块
- 修悬停高亮错位：绘制用绝对坐标、命中用相对坐标，两套基准混用 → 统一为同一套相对坐标
- 全汉化：GUI 硬编码中文 + `IMTE_GetItemName` 代码直返中文名 + lang 双 locale + LH.add 默认值改中文（ locale 为 en_us 的游戏也显示中文）
- 范围画框从 RenderWorldLastEvent 改为 **TESR**（GTNH 的 Angelica/Hodgepodge 渲染管线会改变世界事件矩阵语义，TESR 是管道无关的稳定契约，世界锚定）

## 功能清单
- 扫描范围内 GT6 储物桶（默认上下左右各 8 格；最多 64 个，可配至 256；偏移 ±32、半径 0~16 每管理器独立可调）
- AE2 存储总线存取（差值语义，无复制/无丢失；胶带锁定桶拒绝）
- 空手右键打开范围 GUI；大世界范围线框（火柴盒，视野内全部绘制）
- 幻影槽只读 + 路由槽单写入口 + GT6 打包（小撮/粒自动打包）
- 储物桶归属最近管理器（多管理器无重复计数）

## 安装
放入整合包 `mods/`，需 GT6 6.17.06。jar 名带版本号（gtsm-1.0.9.jar），升级请删除旧 gtsm-*.jar。
