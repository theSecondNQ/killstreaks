# 维护工具

需要 Python 3.11+。所有路径根据脚本所在工程解析，工具不依赖旧版本文件。Gradle 构建需使用 JDK 17。

| 命令 | 操作 |
| --- | --- |
| `./tools/build.ps1 -Offline` | 使用已有 Gradle 缓存构建 JAR；优先采用显式路径、`GRADLE_USER_HOME` 或用户目录的 `.gradle` |
| `python tools/release.py inventory` | 只读核对生产 JAR、JSON、语言覆盖与占位符、字幕、声音文件、Mixin 和源码来源，输出 `build/reports/package-inventory.json` |
| `python tools/release.py inventory --baseline OLD.jar` | 额外核对旧版运行代码及保留资源字节不变；仅用于同平台纯资源整理，不适用于版本移植 |
| `python tools/release.py package` | 按明确目录清单生成当前源码包及 SHA256，不收录缓存、日志、旧发行包、参考输入 |
| `./tools/update_instance.ps1 -InstanceRoot '实例绝对路径'` | 在游戏退出后安装已核对的 JAR，备份旧版并记录其他模组、配置和皮肤的校验和 |
| `python tools/release.py verify` | 核对源码包、模组包；存在当前发行包的安装记录时另核对已安装文件、旧版备份及保留文件 |
| `node tools/pixel_models.mjs` | 从原始模型生成低多边形与像素贴图暂存结果，核对骨骼头、顶点属性和材质，并写入 `build/reports/pixel-models/rebuild.json` |

发行与安装工具不会启动游戏。安装器会在发现 Java 进程、重复模组或哈希不一致时停止。无需针对每个版本生成新脚本。

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

## Forge 构建缓存与代理

本工程使用用户目录的 `.gradle` 缓存；可用 `-GradleUserHome` 显式指定其他位置。`build.ps1` 会读取 Windows 已启用的 HTTP 代理并传给 Java，避免启动器联网正常而 Gradle 连接失败。

依赖齐全时使用 `.\tools\build.ps1 -Offline -Tasks build`。离线模式跳过 ForgeGradle 的联网探测，Maven 下载仍使用正常的 TLS 校验。IDE 的 Gradle JVM 使用 JDK 17，Gradle user home 与脚本保持一致。

运行专用测试服务器使用 `.\tools\build.ps1 -Tasks runGameTestServer`。首次运行需要下载开发运行环境；它在 `build/gametest` 中生成独立世界，不读取已有游戏存档。完整说明见 [Forge 适配](../docs/FORGE-1.20.1.md)。

## 飞机 HUD 资源导入

`python tools/import_aircraft_hud.py "原资源导出目录"` 核对并导入原始 PNG、控件数值、动画和文本；不执行重建 Lua，日常构建不需要导出目录。详见 [飞机 HUD](../docs/AIRCRAFT-HUD.md)。当前源码同步跳过 `resources`，该工具仅在明确更新资源时手动运行。

安装器及核对工具同时识别原 killstreakcod 文件名和带平台的 killstreaks 文件名，并保留现有文件名前缀。GD656 配置编码兼容见 [说明](../docs/GD656-COMPAT.md)。
