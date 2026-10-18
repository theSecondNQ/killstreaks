# 开发结构

## 运行源码

沿用用户建立的 `src/main/java/net/killstreakcod` 根目录与四个分类，Java 包名和文件目录保持一致。共有 201 个顶层 Java 文件：

| 包／目录 | 数量 | 职责 |
| --- | ---: | --- |
| `net.killstreakcod` | 1 | 模组入口 `KillstreakcodMod` |
| `net.killstreakcod.audio` | 17 | 音效、无线电、字幕、音频消息和播放管理 |
| `net.killstreakcod.killstreaks` | 24 | 连杀定义、专属飞行、载具实体、武器、物品及总部通讯终端方块 |
| `net.killstreakcod.render` | 68 | 模型、动画、光照、过场、HUD、界面和客户端遥控 |
| `net.killstreakcod.render.compat` | 2 | GD656／武器模组的客户端兼容桥接，以及 Iris 光影 API 适配 |
| `net.killstreakcod.render.tablet` | 7 | 平板画布、显示材质、手持模型、手机和平板屏幕合成、屏幕路由与表面标定 |
| `net.killstreakcod.universal` | 55 | 公共玩法服务、网络消息、配置、队友、战斗、空域、菜单、研究系统和共享资源读取 |
| `net.killstreakcod.universal.compat` | 2 | TaCZ 事件、遥控限制、卓越前线载具伤害及击杀结算兼容 |
| `net.killstreakcod.mixin` | 12 | 通用 Mixin 及选择插件 |
| `net.killstreakcod.mixin.client` | 7 | 客户端 Mixin |
| `net.killstreakcod.mixin.compat` | 6 | TaCZ 兼容 Mixin |

跨包代码使用显式导入；只为已有跨分类调用开放必要访问权限。Mixin 配置和插件内完整类名、MCreator 入口均已迁移。注册 ID、资源命名空间与网络消息 ID 使用 `killstreakcod`。网络协议注册入口为 `KillstreakcodMod` 和 `KillstreakClientPayloadHandlers`，共用 `NETWORK_VERSION = "1.0.0"`。

`render.compat`、`render.tablet` 和父包是三个独立的 Java 包。GD656 桥接通过公开的 `RemoteHudLayout.Box` 与字幕布局共享屏幕占用区域；平板与主渲染器通过表面顶点、UI 记录、状态采样、文字绘制和屏幕重置入口协作。缓存、内部会话和字体实现继续保持内部访问。

沿用用户当前的 `render.compat` 目录名，归入 `OptionalClientIntegrations`、`OptionalShaderCompat`；`SceneTabletScreen`、`ScenePhoneScreen` 归入 `render.tablet`。

`universal.compat` 集中 `OptionalIntegrations`、`SuperbWarfareCompat`，通过 `KillstreakService.queueVehicleKill` 与 `SuperbWarfareCompat.intercept` 两个跨包入口对接公共击杀结算和命中计算。

| 职责 | 主要入口 |
| --- | --- |
| 注册、规则、奖励库存 | `KillstreakcodMod`、`KillstreakContent`、`KillstreakService`、`RewardLedger`、`StreakServerConfig` |
| 部署、遥控、伤害、空域 | `TacticalRuntime`、`StreakCombat`、`AircraftAirspace`、`TacticalUnitEntity` |
| 专属载具逻辑 | `PrecisionFlight`、`GunshipFlight`、`ChopperIngress`、`NativeVtolFlight`、`NativeCruiseFlight`、`MortarStrikeFlight` |
| 界面、键位、皮肤 | `KillstreakTerminalScreen`、`KillstreakWhitelistScreen`、`KillstreakKeyMappings`、`OperatorSkinScreen` |
| 客户端遥控、地形、HUD | `TacticalClient`、`TacticalHud`、`RemoteTerrainRange`、`NativeRemoteHud` |
| 模型、动画、座舱、平板 | `ImportedCastModel`、`OriginalStreakAnimations`、`SceneEntryRenderer`、`SceneTabletScreen` |
| 音效、语音、字幕、特效 | `NativeAudio`、`NativeRadioClient`、`NativeRadioSubtitles`、`NativeFxRenderer` |
| 可选模组兼容 | `OptionalIntegrations`、`OptionalClientIntegrations`、`OptionalShaderCompat`、`SuperbWarfareCompat` 和相关 Mixin |
| 客户端资源生命周期 | `ClientResourceReload`，统一清理模型、动画、平板、HUD、音效及 GPU 缓冲 |

上表入口分布于对应分类。通过 NeoForge 事件订阅加载的类和通过 JSON 注册的 Mixin，也属于必要运行代码；不应仅根据“没有显式调用”删除。

排版、命名与冗余清理约定见 [编码约定](CODING-STYLE.md)。4.30.0 清理时删除了 50 个已无调用的方法和旧的程序化座舱辅助类；实际原资源座舱继续通过 `SceneEntryRenderer` 等入口加载。

4.32.0 的职责划分、缓存失效及存档约束见 [模块设计](ARCHITECTURE.md)。`RewardLedger` 只在一次服务端操作内使用，集中处理旧标签迁移、奖励获取、扣除、退款和重置；`KillstreakService` 负责事件、命令、部署检查及网络通知。`ClientResourceReload` 取代原来分散的七个资源重载监听，着色器注册和退出世界事件仍使用各自的实际生命周期。

## 构建

- Java Toolchain：21；Gradle Wrapper：9.6.1；NeoForge 编译依赖：21.1.232。
- 默认只编译 `src/main`。客户端和服务端开发启动任务分别为 `runClient`、`runServer`。
- `build` 与 `.gradle` 均为自动生成目录，不进入源码发行包。
- 根目录不需要放置第三方兼容模组 JAR；兼容逻辑通过已有事件、反射和可选 Mixin 加载。
- 规则通过原生 SERVER 配置注册。服务器向客户端同步玩法状态。
- `[rewards]` 的 `mgbEnabled`、`juggernautEnabled` 均默认关闭。服务端统一校验获取及使用；终端状态消息同步无畏战甲可用性，已禁用库存不作为可用奖励发送。

## 发布

版本号修改 `build.gradle`、`src/main/resources/META-INF/neoforge.mods.toml`、`killstreakcod.mcreator` 和当前说明，当前为 `1.1.0.4341`。构建及安装工具支持四段数字版本号。网络协议位于服务端与客户端的 Payload 注册入口，仅在通信格式变化时升级。

构建后运行通用 `tools/release.py`。它按当前资源和源码生成核对结果，不依赖历史版本脚本、旧备份或 MW2022 导出目录。

语言键与字幕回退的维护约定见 [语言支持](LOCALIZATION.md)。源码同步到 `D:\Projects\killstreaks` 时排除 `resources` 目录及包含资源的生成文件，完整运行资源仍由主工程提供。

今后构建日志和临时分析放入 `build/logs`、`build/reports` 或 `build/work`；历史发行文件保存在工程外。避免再次向源码目录堆积逐版本安装器、复制文档或完整源码 ZIP。
