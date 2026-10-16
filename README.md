# Killstreak

Minecraft Java Edition **1.21.1** · NeoForge **21.1.232+** · **Java 21** · 当前版本 **1.0.1.4322**

提供 19 种可配置连杀奖励与隐藏核弹，包括按键呼叫、遥控载具、平板标定、侦察雷达、空投、低多边形模型／像素材质、原骨骼动画／音效及字幕。客户端与服务器均须安装同一版本模组。

模型保留原轮廓与骨骼绑定，439 份模型及其物品、回退模型贴图已统一处理；资源统计、转换规则与备份说明见 [资源说明](docs/ASSETS.md)。

NeoForge 模组列表显示 **Killstreak**；中文环境下，创造模式物品栏标签页显示 **连杀奖励**。

界面支持 12 个语言选项：简体中文、台湾繁中、香港繁中、文言（华夏）、英语、日语、韩语、德语、法语、西班牙语、俄语和巴西葡萄牙语，跟随 Minecraft 的语言设置。英语、简体中文、两种繁中和文言均完整覆盖界面及无线电字幕，其余语言的播报字幕回退到英文；语音音频仍为英文。覆盖范围和翻译维护方式见 [语言支持](docs/LOCALIZATION.md)。

## 使用

- 将 `build/libs/killstreakcod-1.0.1.4322.jar` 放进实例的 `mods` 文件夹，并移出旧版本。
- 按 **K** 或使用连杀终端配置奖励、阵营、队友白名单和操作员皮肤。
- 规则配置位于游戏或服务器根目录的 `config/killstreakcod-server.toml`；原生 SERVER 配置支持已有世界中的同名覆盖文件。
- 客户端表现设置位于 `config/killstreakcod-client.toml`。
- 自定义玩家皮肤 PNG 放入游戏根目录的 `killstreakcod-skins` 文件夹，在终端中选择。
- 管理命令入口为 `/killstreak`，可在游戏内使用命令补全查看参数。

核弹和无畏战甲默认关闭。在服务端配置的 `[rewards]` 中将 `mgbEnabled` 或 `juggernautEnabled` 改为 `true` 即可开启。开关同时控制自然获取、命令发放、物品兑换和启动；禁用奖励的既有库存保存在存档中，重新开启后恢复可用。完整配置示例见 [killstreakcod-server.toml](docs/killstreakcod-server.toml)。

两端网络注册统一使用 `1.0.0`，由 `KillstreakcodMod.NETWORK_VERSION` 提供。客户端和专用服务器应同时更新到本版。

版本号采用四段数字；公开发布前的源码协议与第三方资源授权说明见 [发布与许可](docs/PUBLISHING.md)。

1.0.0.4321 按 `mattpocock-skills` 的模块设计原则整理奖励账本、模型渲染与资源生命周期，并缓存 HUD 动画时间线和图像元数据。设计及存档兼容说明见 [模块设计](docs/ARCHITECTURE.md)。

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

在工程目录使用 JDK 21：

```powershell
.\gradlew.bat jar
python tools/release.py inventory
python tools/release.py package
```

若仓库下载出现 `Connection reset`，且本机已有 MCreator 依赖缓存，可执行 `.\tools\build.ps1 -Offline` 复用缓存。IDE 的 Gradle user home 应选择同一目录；详见 [工具说明](tools/README.md#maven-连接重置与缓存)。

可选安装及交付核对：

```powershell
.\tools\update_instance.ps1 -InstanceRoot 'D:\Minecraft\.minecraft\versions\1.21.1-NeoForge_21.1.252'
python tools/release.py verify
```

安装工具会检查游戏进程、核对文件并备份旧版。源码包只包含当前开发必需文件。构建和交付工具不会启动游戏。

详细内容见 [开发结构](docs/DEVELOPMENT.md)、[编码约定](docs/CODING-STYLE.md)、[资源说明](docs/ASSETS.md)、[本次整理](docs/RELEASE.md) 与 [工具用法](tools/README.md)。

历史发布、备份、参考视频、第三方 JAR 以及旧导入工具已归档到工程外的 `D:\Projects\killstreakcod-archive\20261015-4.29.0`。完整移动清单见归档中的 `cleanup-plan.json`，原始内容可恢复。
