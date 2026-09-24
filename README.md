# GT6 储物桶管理器（Storage Manager）

一个 Minecraft 1.7.10 + GregTech 6 的附属 mod。提供类似 Storage Drawers「抽屉管理器」的方块，
管理其范围内的 **GT6 储物桶/箱（`MultiTileEntityMassStorage` 全家族）**，并把它们对外暴露成
一个统一的 `IInventory`/`ISidedInventory`，让所有认标准库存接口的自动化（BC 管道、AE2、
GT 输送带、物品管道、物流管道……）能统一存取。

**特点：管理器自身不保存任何物品**——它是一个「幻影视图」，直接读写背后储物桶的内容，
所以既不会复制物品，也不会在管理器被破坏时丢失物品。

## 功能

- **扫描**：以管理器为中心的立方体范围，半径可配置（默认 1 = 仅相邻 26 格，最大 8），最多管 26 个桶。
- **槽 0 通用路由槽**：插入任意物品 → 先找已有同品种的桶；没有再（可选）占用空桶装新品种。
- **槽 1..N 幻影槽**：一一对应每个储物桶，读取直接反映桶内 `slot 1`，抽取直接转发 `decrStackSize`。
- **右键存入**：手持物品右键管理器；潜行右键把背包内全部堆叠一次性塞进去。
- **自动化兼容**：实现 `ISidedInventory`，六面全开放，胶带锁定的桶自动拒绝存取。
- **矿物词典统一**：插入时用 GT6 的 `OM.get` 统一（和 GT6 设备行为一致），配置可关。
- **GT 工具交互**：
  - 螺丝刀：切换「是否填充空桶」
  - 软锤：立即重新扫描
  - 放大镜：查看管理范围/已占用/空闲/锁定数量
- **比较器输出**：按已占用桶比例输出 0~15 红石信号。
- **零配置可用**：所有行为有合理默认，`config/gtsm.cfg` 可调。

## 构建

需要 **JDK 8**（1.7.10 mod 必需）。

```bash
# 1. 确认 Java 版本
java -version          # 必须是 1.8.x

# 2. 放入 GT6 jar（已经放在 libs/ 里了；如需替换，覆盖 libs/gregtech_1.7.10-6.17.06.jar）

# 3. 首次构建（会下载 Minecraft、Forge、MCP 映射，需要联网，约 500MB~1GB 流量）
./gradlew setupDecompWorkspace --refresh-dependencies

# 4. 打包
./gradlew build
# 产物：build/libs/gtsm-1.0.0.jar

# 5. 游戏内测试
./gradlew runClient
# 然后把 GT6 release jar 放进 run/mods/，进创造模式摆储物桶 + 管理器测试
```

> 如果 `gradlew` 没有执行权限：`chmod +x gradlew`
> Gradle 版本：wrapper 自带 2.14.1（ForgeGradle 1.2 配现代 JDK 8 最稳的版本）。

## 文件结构

```
gt6storagemanager/
├── build.gradle                              # ForgeGradle 1.2 构建脚本
├── gradle/wrapper/                           # Gradle 2.14.1 wrapper
├── libs/gregtech_1.7.10-6.17.06.jar          # 编译期依赖（provided，不打包）
└── src/main/
    ├── java/gtsm/
    │   ├── StorageManager_Mod.java           # 主类：继承 Abstract_Mod，注册方块
    │   ├── Proxy_Client.java / Proxy_Server.java
    │   ├── Config.java                       # 配置值
    │   └── tile/TileEntityStorageManager.java # 核心：幻影视图 + 扫描路由 + 交互
    └── resources/
        ├── mcmod.info
        └── assets/gtsm/
            ├── lang/{zh_cn,en_us}.lang
            └── textures/blocks/machine_storage_manager.png   # 占位贴图，建议替换成正式美术
```

## 编译期对 GT6 的依赖说明（1.7.10 老问题）

GT6 的 release jar 是 **SRG 混淆**的（里面调 Minecraft 方法用的是 `func_xxxxx` 名字），
而 ForgeGradle 开发环境里的 Minecraft 是 **MCP 名**。所以本工程遵守一条规则：

> 凡是调用 Minecraft 原生方法，一律先强转成原版类型（`(IInventory) box`、`(TileEntity) box`）；
> GT6 自家声明的方法（`insertItems`、`slot`、`slotHas`）和字段（`mMode`）不参与混淆，可直接调用。

`build.gradle` 里用 `provided` 配置引入 GT6 jar 并追加到 classpath **末尾**，
保证 ForgeGradle 提供的 MCP 版 Minecraft 在编译期优先解析。

## 配置项（`config/gtsm.cfg`）

| 键 | 默认 | 说明 |
|---|---|---|
| `scanRadius` | 1 | 扫描半径，1~8（1 = 仅相邻 26 格） |
| `maxBoxes` | 26 | 最多同时管理的储物桶数量 |
| `fillEmptyBoxes` | true | 是否允许把新品种物品自动放进空储物桶 |
| `oreDictUnify` | true | 插入时是否走矿物词典统一 |

## 玩法示例

```
        [桶A:铁锭]        [桶B:空]
              ↘              ↙
        ┌─────────────────────┐
        │   储物桶管理器 🧰    │ ← 管理器（半径 1，管相邻 26 格）
        └─────────────────────┘
              ↑ 管道/AE2 接入
```

- 漏斗/管道往管理器塞铁锭 → 自动进桶A；塞金锭 → 占用桶B。
- 抽取管理器 → 从桶里按 64/次抽出。
- 胶带锁定的桶：自动拒绝存入和抽出。

## 已知限制 / 二期

- 无 GUI（按设计）。二期可加汇总面板。
- 贴图是程序生成的占位图，建议替换成正式美术。
- 每个管理器最多 26 个桶（受槽位数量限制）；二期可加「从机」方块扩展网络。
