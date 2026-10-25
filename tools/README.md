# 维护工具

需要 Python 3.11+。所有路径根据脚本所在工程解析，工具不依赖旧版本文件。Gradle 构建需使用 JDK 21。

| 命令 | 操作 |
| --- | --- |
| `./tools/build.ps1 -Offline` | 使用已有 Gradle 缓存构建 JAR；优先采用显式路径、`GRADLE_USER_HOME` 或现有 MCreator 缓存 |
| `python tools/release.py inventory` | 只读核对生产 JAR、JSON、语言覆盖与占位符、字幕、声音文件、Mixin 和源码来源，输出 `build/reports/package-inventory.json` |
| `python tools/release.py inventory --baseline OLD.jar` | 额外核对旧版运行代码及保留资源字节不变；用于本次纯结构整理 |
| `python tools/release.py package` | 按明确目录清单生成当前源码包及 SHA256，不收录缓存、日志、旧发行包、参考输入 |
| `./tools/update_instance.ps1 -InstanceRoot '实例绝对路径'` | 在游戏退出后安装已核对的 JAR，备份旧版并记录其他模组、配置和皮肤的校验和 |
| `python tools/release.py verify` | 核对源码包、模组包、已安装文件、旧版备份及保留文件 |
| `python tools/import_aircraft_hud.py '原资源导出目录'` | 核对并导入飞机 HUD 原始 PNG、控件数值、动画与文本，不执行 Lua；详见[飞机 HUD](../docs/AIRCRAFT-HUD.md) |
| `python tools/import_earned_ui.py --source "原资源总交付目录"` | 核对并导入连杀获取提示原图、布局与属性轨道，详见[连杀获取提示](../docs/EARNED-UI.md) |
| `python tools/import_nuke_ui.py --source "原资源总交付目录"` | 核对并导入多人模式核弹 UI 原图、构造状态与动画轨道，详见[核弹 UI](../docs/NUKE-UI.md) |
| `node tools/pixel_models.mjs` | 从原始模型生成低多边形与像素贴图暂存结果，核对骨骼头、顶点属性和材质，并写入 `build/reports/pixel-models/rebuild.json` |

工具不会启动游戏。安装器会在发现 Java 进程、重复模组或哈希不一致时停止。无需针对每个版本生成新脚本。

构建、打包与安装统一使用 `killstreaks-neoforge-1.21.1-<版本号>.jar` 或 `killstreaks-forge-1.20.1-<版本号>.jar`；源码包在同一文件名末尾使用 `-source.zip`。发行工具从 `build.gradle` 的 `base.archivesName` 读取前缀。安装器仍识别旧 `killstreakcod-*` 文件名，备份后替换为统一命名，并拒绝重复模组包。

同版本语言或资源修订可显式使用 `-ReplaceSameVersion`，例如 `./tools/update_instance.ps1 -InstanceRoot '实例绝对路径' -ReplaceSameVersion`。仅允许替换唯一的同名模组 JAR；仍在游戏退出后暂存、核对、备份旧包，并在安装失败时还原。普通安装默认拒绝覆盖同名但内容不同的包。

像素模型转换工具另需 Node.js 20+。在工程目录安装固定版本的构建依赖：

```powershell
npm install --prefix build/pixel-model-tools --no-audit --no-fund meshoptimizer@1.3.0 sharp@0.35.5
node tools/pixel_models.mjs
```

依赖安装在 `build`，不进入模组运行包。复现转换时先在独立副本恢复原始资源；当前工程资源已经减面。脚本只写入 `build/pixel-models/staged`，接入生产资源前应保留原文件备份，按校验清单核对。现有批次的备份位置及接入文件哈希记录在 `build/reports/pixel-models/backup.json` 和 `deploy.json`。

版本号支持三段或四段数字，例如 `4.32.1`、`1.1.0.4342`；安装器仍限制版本号只包含数字和对应的分隔点。

`mesh_codec.mjs` 负责模型版本 1 至 4 的无损打包与解包，输出暂存目录；`pixel_models.mjs` 使用同一解码入口。生产 JAR 必须小于 100,000,000 字节，构建与发行清单均会检查。压缩范围和维护命令见 [资源压缩](../docs/COMPRESSION.md)。

`python tools/import_terminal_model.py` 逐字节复制 `assets/terminal.obj`、配套 MTL 和九张 PNG，生成上下两部分及完整物品模型的加载入口，并核对 SHA-256；不编辑原几何或材质。读取方式及使用方法见 [总部通讯终端](../docs/HEADQUARTERS-TERMINAL.md)。

若要修改原始资源转换过程，历史导入工具仍可从工程外归档取回；不要把只用于导入的来源清单重新放入运行资源目录。

## Maven 连接重置与缓存

`DiffPatch-2.0.0.36-all.jar` 是 NeoForm 的构建工具。若下载报 `Connection reset`，而 MCreator 已缓存该文件，可使用：

```powershell
.\tools\build.ps1 -Offline
```

也可以显式指定 `-GradleUserHome 'C:\Users\NQ_Snvw\.mcreator\gradle'`。`-Offline` 仅在依赖已缓存时使用；升级依赖后需要联网下载时省略此参数。

IntelliJ：在 **Settings → Build, Execution, Deployment → Build Tools → Gradle** 将 **Gradle user home** 指向同一缓存目录，Gradle JVM 使用 JDK 21。外部修改 IDE 项目本地配置后，应重新打开项目使其生效。不要通过清空已有缓存处理这类下载中断。

安装器及核对工具同时识别原 killstreakcod 文件名和带平台的 killstreaks 文件名，并保留现有文件名前缀。GD656 配置编码兼容见 [说明](../docs/GD656-COMPAT.md)。
