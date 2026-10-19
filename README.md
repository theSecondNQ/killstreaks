# Killstreak

Minecraft Java Edition **1.20.1** · Forge **47.4.10+** · **Java 17** · 当前版本 **1.1.0.4352**

提供 19 种可配置连杀奖励与隐藏核弹，包括按键呼叫、遥控载具、平板标定、侦察雷达、空投、低多边形模型／像素材质、原骨骼动画／音效及字幕。客户端与服务器均须安装同一版本模组。

模型保留原轮廓与骨骼绑定，418 份运行模型及其物品、回退模型贴图已统一处理；资源统计、转换规则与备份说明见 [资源说明](docs/ASSETS.md)。

本版通过模型无损编码、清理未使用资源及特效贴图压缩，将单个发行 JAR 控制在 **100 MB 以下**。模型细节、HUD 文字及原音频保留，维护方式见 [压缩说明](docs/COMPRESSION.md)。

Forge 模组列表显示 **Killstreak**；中文环境下，创造模式物品栏标签页显示 **连杀奖励**。

界面支持 12 个语言选项：简体中文、台湾繁中、香港繁中、文言（华夏）、英语、日语、韩语、德语、法语、西班牙语、俄语和巴西葡萄牙语，跟随 Minecraft 的语言设置。英语、简体中文、两种繁中和文言均完整覆盖界面及无线电字幕，其余语言的播报字幕回退到英文；语音音频仍为英文。覆盖范围和翻译维护方式见 [语言支持](docs/LOCALIZATION.md)。

Forge 适配、构建及测试说明见 [Forge 1.20.1](docs/FORGE-1.20.1.md)。

## 使用

- 将 `build/libs/killstreakcod-forge-1.20.1-1.1.0.4352.jar` 放进实例的 `mods` 文件夹，并移出旧版本。
- 按 **K** 或使用连杀终端配置奖励、阵营、队友白名单和操作员皮肤。
- 可放置的“总部通讯终端”已接入侦察、支援、火力、现代化四棵科技树，含20项基础研究（包括核弹）、59项升级和四级终端。生存模式先研究并在总部支付材料预购储备，击杀／得分达标时扣一次储备并获得可呼叫奖励，呼叫时只扣奖励库存。升级可免费装卸，在两种模式中生效；科技树按终端等级及所需经验排列。玩法说明见[研究系统](docs/RESEARCH.md)，新增费用及武器操作见[火力与现代化研究](docs/RESEARCH-EXPANSION.md)，配方及模型说明见[总部通讯终端](docs/HEADQUARTERS-TERMINAL.md)。
- 规则配置位于存档的 `serverconfig/killstreakcod-server.toml`；新存档默认配置可放入实例的 `defaultconfigs` 文件夹。
- 客户端表现设置位于 `config/killstreakcod-client.toml`。
- 自定义玩家皮肤 PNG 放入游戏根目录的 `killstreakcod-skins` 文件夹，在终端中选择。
- 管理命令入口为 `/killstreak`，可在游戏内使用命令补全查看参数。

核弹和无畏战甲默认关闭。在服务端配置的 `[rewards]` 中将 `mgbEnabled` 或 `juggernautEnabled` 改为 `true` 即可开启。开关同时控制自然获取、命令发放、物品兑换和启动；禁用奖励的既有库存保存在存档中，重新开启后恢复可用。完整配置示例见 [killstreakcod-server.toml](docs/killstreakcod-server.toml)。

两端 Forge 网络协议为 `forge-1.20.1/1.0.0`，版本由 `KillstreakcodMod.NETWORK_VERSION` 提供。客户端和专用服务器应同时更新到本版。

版本号采用四段数字；公开发布前的源码协议与第三方资源授权说明见 [发布与许可](docs/PUBLISHING.md)。

4.32.0 按 `mattpocock-skills` 的模块设计原则整理奖励账本、模型渲染与资源生命周期，并缓存 HUD 动画时间线和图像元数据。设计及存档兼容说明见 [模块设计](docs/ARCHITECTURE.md)。

## 目录

| 路径 | 内容 |
| --- | --- |
| `src/main/java` | 实际运行的玩法、网络、界面、渲染与兼容代码 |
| `src/main/resources` | 运行所需模型、材质、动画、音效、语言、配置及数据 |
| `gradle`、`gradlew*` | Gradle Wrapper |
| `build.gradle`、`settings.gradle`、`gradle.properties` | 构建配置 |
| `killstreakcod.mcreator` | MCreator 工作区入口 |
| `.editorconfig` | 统一编码、缩进和换行设置 |
| `tools` | 通用资源核对、打包与安装工具 |
| `docs` | 当前版本、结构与资源说明 |
| `build/libs` | 当前构建的模组包、源码包与校验和，自动生成 |
| `build/reports`、`build/logs` | 核对结果与构建日志，自动生成 |

## 构建和交付

服务端可在 `config/killstreakcod-server.toml` 的 `[research]` 中设置 `disableUpgrades = true`，禁用连杀升级研究、装配和效果，默认值为 `false`。具体规则见[研究说明](docs/RESEARCH-EXPANSION.md#禁用升级项目)。

在工程目录使用 JDK 17：

```powershell
.\gradlew.bat jar
python tools/release.py inventory
python tools/release.py package
```

若仓库下载出现 `Connection reset`，且本机已有 MCreator 依赖缓存，可执行 `.\tools\build.ps1 -Offline` 复用缓存。IDE 的 Gradle user home 应选择同一目录；详见 [工具说明](tools/README.md#maven-连接重置与缓存)。

可选安装及交付核对：

```powershell
.\tools\update_instance.ps1 -InstanceRoot 'D:\Minecraft\.minecraft\versions\1.20.1-Forge_47.4.10'
python tools/release.py verify
```

安装工具会检查游戏进程、核对文件并备份旧版。源码包只包含当前开发必需文件。构建和交付工具不会启动游戏。

详细内容见 [开发结构](docs/DEVELOPMENT.md)、[编码约定](docs/CODING-STYLE.md)、[资源说明](docs/ASSETS.md)、[本次整理](docs/RELEASE.md) 与 [工具用法](tools/README.md)。

历史发布、备份、参考视频、第三方 JAR 以及旧导入工具已归档到工程外的 `D:\Projects\killstreakcod-archive\20261015-4.29.0`。完整移动清单见归档中的 `cleanup-plan.json`，原始内容可恢复。

本次同步新增空中炮艇与攻击直升机原资源 HUD，使用实际弹药、武器和按键状态，座舱采用原三屏画布。说明见 [飞机 HUD](docs/AIRCRAFT-HUD.md)。当前分支为 `1.20.1`；本次仅同步源码，`resources` 由用户另行提供。
