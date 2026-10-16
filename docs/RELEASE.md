# 1.0.1.4322 多语言与源码同步

## 繁中补全及文言（2026-10-16）

- 台湾繁中校订简繁混用和界面用语；新增香港繁中 `zh_hk`，覆盖原版两个独立繁中选项。
- 新增文言（华夏）`lzh`，含完整 423 个界面键、694 个无线电台词键及 34 个人物键；装备专名与型号保持可识别。
- 英语、简中、台湾繁中、香港繁中、文言均覆盖完整的 1,151 个键，其他七种语言保留现有界面及英文播报字幕回退。
- 版本保持 **1.0.1.4322**，补齐 MCreator 语言登记与交付核对规则。安装器以显式 `-ReplaceSameVersion` 支持同版本资源修订，仍备份旧包并保留回滚。
- 按既有要求同步源码、工具和说明到 `D:\Projects\killstreaks`，语言资源留在主工程，不复制 `resources`。

修改前资源及交付包保存在 `D:\Projects\killstreakcod-archive\20261016-traditional-lzh`。构建与资源核对记录位于 `build/logs`、`build/reports`，未启动 Minecraft 或执行游戏测试。

## 首次多语言更新

- 补齐简体中文和英语，新增繁体中文、日语、韩语、德语、法语、西班牙语、俄语及巴西葡萄牙语；每种语言覆盖 423 个界面键。
- 遥控 HUD、状态消息及配置界面接入翻译键；操作提示采用实际绑定按键。终端按钮按可用宽度缩放，长说明换行，奖励条件限制在所在单元格内。
- 694 条原无线电台词新增英文和繁体字幕，保留原简体台词及岗位名称。其他界面语言回退到英文字幕，音频保持英文。
- 发行清单工具增加语言覆盖、占位符、重复键、源代码引用、原字幕和播报人物核对。
- 构建、NeoForge 元数据、MCreator 和当前说明统一为 **1.0.1.4322**，网络协议仍为 **1.0.0**。
- 同步源码、版本、工具和文档到 `D:\Projects\killstreaks`，排除 `resources`、生成的发行包和运行缓存；保留目标 Git 与许可证。语言资源留在完整主工程。

语言及回退范围见 [语言支持](LOCALIZATION.md)。修改前快照保存在 `D:\Projects\killstreakcod-archive\20261016-localization-1.0.1.4322`。未启动 Minecraft 或运行游戏测试。

## 1.0.0.4321 版本号格式调整

- 构建、NeoForge 元数据、MCreator 和当前说明统一使用 `1.0.0.4321`。
- 安装工具允许三段或四段数字版本号，适配新的发行文件名。
- 增加公开发布前的源码协议与第三方资源授权说明；当前文档属于建议，不代表已经获得原游戏资源授权或已授予新的源码许可证。

修改前文件及上一版发行包保存在工程外的 `D:\Projects\killstreakcod-archive\20261015-version-1.0.0.4321`。构建和交付核对记录位于 `build/logs`、`build/reports`；没有启动 Minecraft 或运行游戏测试。

## 4.32.1 配置补全与显示名称修正

- NeoForge 元数据和 MCreator 展示名称改为 **Killstreak**；模组语言名称、按键分类和终端标题同步使用单数形式。
- 创造模式标签页单独显示 **连杀奖励**，中文和英文语言文件使用同一个指定页签名。
- `[rewards].mgbEnabled`、`[rewards].juggernautEnabled` 继续由原生 SERVER 配置注册，默认均为 `false`。
- 检查到实际游戏实例仍使用 4.29.0，故其配置没有新版开关。此次更新补齐实际 `config/killstreakcod-server.toml` 的缺失项，并保留已有获取规则。
- 已有 Java 玩法实现和网络协议不变；修改前配置、源码及上版发行文件保存在工程外的 `D:\Projects\killstreakcod-archive\20261015-config-branding`。

生产包及安装核对记录位于 `build/logs`、`build/reports`。没有启动 Minecraft 或运行游戏测试。

## 4.32.0 模块设计与运行开销整理

- 使用 `mattpocock-skills@mattpocock` 的 `codebase-design` 技能，集中奖励规则与客户端资源生命周期。
- 新增包内 `RewardLedger`，统一旧标签迁移、计数、库存、配装、额外奖励队列及重置。单次操作索引玩家标签，写回变更；旧存档格式继续兼容。
- 库存增加采用饱和运算；无效配装回退到默认槽位；`resetall` 同步清理兼容载具待结算击杀。
- `ImportedCastModel` 用命名渲染阶段替代四层布尔参数转发；可见性索引统一保存并复用，移除没有绘制入口的带手指左手索引构造。
- `NativeSceneUi` 缓存 HUD 动画时间线、图像元数据及纹理位置，移除逐帧关键帧排序、属性分组和材质正则解析；缺失图像缓存到下次重载。
- 七个客户端资源重载监听合并到 `ClientResourceReload`，在渲染线程统一清理模型、动画、UI、皮肤、热成像、特效及相关播放状态；移除旧的两处独立事件包装类。
- 奖励 ID 改用索引查询；无待处理击杀时复用空映射。
- 更新模块设计与编码约定，Java 文件总数仍为 190，沿用用户既有目录及中英文注释。

网络格式与注册协议 **1.0.0** 沿用上一版。修改前快照和 4.31.0 发行包位于 `D:\Projects\killstreakcod-archive\20261015-mattpocock-architecture`。构建及文件核对记录位于 `build/logs`、`build/reports`；未启动 Minecraft，未运行游戏测试或帧率基准。

## 4.31.0 名称、协议与特殊奖励开关

- 模组元数据、MCreator、中文和英文展示名称、创造栏、按键分类统一为 **Killstreaks**。
- 两端网络注册共用 `KillstreakcodMod.NETWORK_VERSION`，值为 **1.0.0**。
- 服务端 TOML 新增 `[rewards].mgbEnabled` 和 `[rewards].juggernautEnabled`，均默认 `false`。
- 开关覆盖自然获取、命令、物品兑换、额外奖励、随机空投和部署；终端显示无畏战甲禁用状态，核弹关闭时不显示可用核弹栏位。
- 配置重载后同步在线玩家；禁用奖励的既有库存保留，核弹尚未引爆的倒计时取消。
- 新增带注释的 TOML 示例。终端状态消息增加无畏战甲可用性字段，客户端和服务器需同时更新。

修改前源码、元数据和语言文件保存在 `D:\Projects\killstreakcod-archive\20261015-feature-switches`。没有启动 Minecraft 或运行游戏测试。

## 4.30.2 universal 子目录归类

- 沿用用户创建的 `universal.compat`，归入 `OptionalIntegrations` 和 `SuperbWarfareCompat`。
- 同步模组入口、击杀结算、伤害处理和卓越前线 Mixin 的导入。
- 为载具击杀排队与定向包围盒命中查询开放必要的两个跨包方法。
- 更新目录说明，采用用户当前的 `render.compat` 名称；Java 文件总数仍为 190，`universal` 直接包含 47 个文件，`universal.compat` 包含 2 个文件。

修改前源码及迁移清单保存在 `D:\Projects\killstreakcod-archive\20261015-universal-packages`。检查记录位于 `build/reports`；没有启动 Minecraft 或运行游戏测试。

## 4.30.1 render 子目录归类

- 沿用用户创建的 `render.compatible`，归入 `OptionalClientIntegrations` 和 `OptionalShaderCompat`。
- 将平板和手机的屏幕合成类 `SceneTabletScreen`、`ScenePhoneScreen` 归入 `render.tablet`，与已有五个设备类集中管理。
- 同步调用方导入，为字幕占位区域、UI 采样及屏幕生命周期提供必要的跨包访问入口。
- 保留用户当前类名、目录、排版和中英文注释；更新开发结构说明，Java 文件总数仍为 190。

修改前源码与迁移清单保存在 `D:\Projects\killstreakcod-archive\20261015-render-packages`。网络协议继续使用 **4.28.1**。检查记录位于 `build/reports`；没有启动 Minecraft 或运行游戏测试。

## 4.30.0 可读性与冗余整理

- 对照用户已修改的音效类，统一 190 个 Java 文件的缩进、语句、方法体和调用链排版。
- 使用编译器符号绑定改善 278 处局部变量和参数命名，展开类型导入并清除未使用导入。
- 拆开语音时长的多层回退表达式，补充服务端调度、平板标定和动画过渡等关键职责说明。
- 四轮删除共 50 个未使用方法，清理相关的旧座舱辅助类、布局记录、字段和废弃模型构造。
- 增加 `.editorconfig`、编码约定与逐方法删除清单，沿用用户现有目录分类。

事件处理、Mixin 注入、接口与父类实现、记录访问器、反射调用和工具类私有构造器已单独识别并保留。批量排版前后 190 个文件的 Java 代码标记一致。

删除记录见 [CODE-CLEANUP.json](CODE-CLEANUP.json)，修改前源码与临时分析保存在 `D:\Projects\killstreakcod-archive\20261015-4.30.0`。构建与文件核对记录在 `build/logs`、`build/reports`；没有启动 Minecraft 或运行游戏测试。

生产 JAR 离线构建成功。与 4.29.2 比较，2,463 个保留方法的指令一致；4 处变化为语音阶段判断、两处时长计算的展开，以及废弃座舱构造的移除。比较忽略行号、局部变量名和编译器生成的 lambda 序号。2,997 个非元数据资源（包括 439 份模型）逐项一致。

## 4.29.2 平板子包访问修复

- 完成用户将五个平板类移入 `net.killstreakcod.render.tablet` 的调整，同步其包名、调用方和静态导入。
- 修复平板子包与父渲染包之间的访问冲突，开放表面顶点记录、投影、画布、显示材质及屏幕状态的必要接口。
- 保持现有玩法和素材内容，沿用网络协议 **4.28.1**。

修改前的目录与源码快照位于 `D:\Projects\killstreakcod-archive\20261015-4.29.2\pre-tablet-access-fix.zip`。

使用已缓存依赖离线构建 JAR 成功；文件与归档核对见 `build/reports`。没有运行游戏或游戏测试。

## 4.29.1 Java 目录归类

- 沿用用户调整的 `net/killstreakcod` 目录，191 个 Java 文件归入 `audio`、`killstreaks`、`render`、`universal` 和既有 Mixin 分类。
- 根包只保留模组入口；包声明、显式导入和必要的跨包访问权限同步更新。
- 迁移四份 Mixin 配置、兼容选择插件中的完整类名，以及 MCreator 工作区入口。
- 资源、注册 ID、存档数据格式和网络协议 **4.28.1** 保持原样。实际素材没有重新导出或替换。

本次修改前的用户目录快照和逐文件移动清单保存在：

`D:\Projects\killstreakcod-archive\20261015-4.29.1`

生产构建与归档／文件核对记录在 `build/logs` 和 `build/reports`。没有启动 Minecraft 或执行游戏测试。

## 4.29.0 工程整理

- 清理根目录历史日志、旧发布产物、旧备份、临时运行目录、参考输入和反编译摘录。
- 删除当前工程中的 449 个废弃资源文件：逐模型导出说明、重复总清单、未使用的分析映射及早已排除的旧炮艇结束动作。
- 保留原有运行 Java 源码，以及 439 份模型、材质、音效、字幕、动作和场景骨骼。
- 移出过期的自动化场景夹具及其启动任务，删除空的 `mcreator.gradle`。
- 通用 `release.py` 与 `update_instance.ps1` 取代逐版本打包、核对和安装脚本。
- 重写当前 README，补充结构、资源、工具说明和忽略规则。MCreator 入口版本与名称同步到当前工程。

显示名称继续为“连杀奖励 / killstreak”，内部 ID 为 `killstreakcod`，网络协议继续使用 **4.28.1**。

移出文件完整保存在 `D:\Projects\killstreakcod-archive\20261015-4.29.0`，便于恢复和继续研究原资源。游戏实例内的存档、配置、皮肤及其他模组不属于此次工程清理范围。

生产构建与归档／文件核对记录在 `build/logs` 和 `build/reports`。没有启动 Minecraft 或执行游戏测试。
