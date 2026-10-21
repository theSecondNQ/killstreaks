# Forge 1.20.1 适配

本目录为 Minecraft **1.20.1** / **Forge 47.4.10+** / **Java 17** 工程，沿用模组版本 **1.1.0.4356**。运行时不需要额外前置模组。客户端和服务器均安装同一份 JAR：

`build/libs/killstreakcod-forge-1.20.1-1.1.0.4356.jar`

现有连杀、研究树、总部通讯终端、遥控载具、平板标定、热成像、音效和本地操作员皮肤均保留。`D:\Projects\killstreakcod` 为原工程；移植前源码备份为 `D:\Projects\killstreaks-backups\20261019-before-forge1201.zip`。

## 构建与检查

```powershell
.\tools\build.ps1 -Tasks build
.\tools\build.ps1 -Tasks runGameTestServer
.\tools\build.ps1 -Tasks @('-PclientSmoke', 'runClient')
python tools/release.py inventory
python tools/release.py package
python tools/release.py verify
```

Gradle Wrapper 使用 8.8，ForgeGradle 使用 6.0.54。脚本默认使用用户目录中的 `.gradle`，会读取 Windows 已启用的 HTTP 代理；依赖齐全时可加 `-Offline`。GameTest 在 `build/gametest` 内运行独立的测试服务器，测试源码和结构位于 `src/gameTest`，不打进发行 JAR。

客户端调试使用 `runClient`，专用服务器调试使用 `runServer`，工作目录为 `run-forge`。可选的 `-PclientSmoke` 检查在 `build/client-smoke` 内启动隔离客户端，隐藏窗口，在标题页核对模型和着色器后自动退出，不读取已有存档。

已通过 Java 17 编译、Forge 服务端 5 项 GameTest（消息编解码与边界、终端放置／拆除及配方、实体和物品 NBT、研究数据落盘），以及隔离客户端启动检查。客户端确认终端模型烘焙、4 个自定义着色器编译及 418 个导入模型解码正常。具体结果和发行包 SHA-256 记录在 `build/reports/forge-validation.json`；发行清单、源码包核对结果分别见 `package-inventory.json`、`delivery-verification.json`。

1.1.0.4353 增加 Embeddium 0.3.31、Oculus 1.8.0 与 Photon v1.4 的隔离渲染检查，覆盖光影开关、爆炸／曳光及巡航导弹入场。1.1.0.4354 修复视口事件改角后特效的错误朝向。1.1.0.4355 修复透明混合被着色器覆盖及重叠层次错误造成的黑边闪烁，增加透明边缘、移动精灵、六种曳光和最终主画面检查。修复及复现方法见 [渲染兼容](RENDER-COMPAT.md)。真实多人联机、完整连杀流程和其他第三方模组组合尚未完成回归测试。

## 主要接口变化

- 注册、配置和事件改为 Forge API；客户端及服务端 tick 按 Forge 的 START/END 阶段调用。
- 消息保留固定标识与有界解码，统一通过 Forge `SimpleChannel` 注册，限定发送方向并检查协议版本。Forge 协议为 `forge-1.20.1/1.0.0`。
- 物品奖励信息存入 1.20.1 的 NBT，护甲材质使用旧版接口；研究数据使用 1.20.1 `SavedData`，继续按玩家 UUID 和研究节点 ID 保存。
- 顶点缓冲、颜色、矩阵、模型加载器和着色器调用使用 1.20.1 的渲染接口。操作员皮肤页面包含对应版本的宽／细手臂预览。
- 着色器事件订阅内部类使用各自唯一的短名称，避免 Forge 1.20.1 事件包装器将同名处理器错误复用。
- 遥控视角单独订阅远程区块，每名玩家每 tick 最多发送 16 个区块，退出后分批恢复本体区块。保留正常玩家观察位置和模拟票据；Sodium、Embeddium 或 Rubidium 存在时跳过原版地形渲染注入。
- 资源包格式为 15；配方、战利品表、进度及方块／物品／实体标签目录改为 1.20.1 的名称。
- 原版 Mixin 通过打包的 `killstreakcod.refmap.json` 映射为正式环境的方法名，发行 JAR 执行 `reobfJar`。

Forge 1.20.1 的 SERVER 配置位于存档的 `serverconfig/killstreakcod-server.toml`；专用服务器为 `<level-name>/serverconfig/killstreakcod-server.toml`。新存档的默认值可放在实例 `defaultconfigs` 中。客户端配置仍位于 `config/killstreakcod-client.toml`。

可选枪械及 HUD 模组通过运行时探测接入。它们的 1.20.1 版本可能采用不同 API，需用实际安装的组合继续验证。

接口参考：[Forge 开发环境](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/)、[网络消息](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/)、[GameTest](https://docs.minecraftforge.net/en/1.20.1/misc/gametest/)。

## 本次 HUD 同步

此分支已合入飞机原资源 HUD，使用 Java 17 编译入口。上一次源码同步排除了运行资源；本次 1.1.0.4352 构建已从上一版 Forge JAR 恢复基础资源，并合入新版 41 个资源文件，当前共包含 3,215 个运行资源文件。恢复来源见 `build/reports/resource-restoration.json`。

本次已重新通过构建、5 项服务端 GameTest 和隔离客户端启动检查；检查确认终端模型、4 个着色器和 418 个导入模型正常加载。飞机 HUD 在实际任务中的显示、完整连杀流程及多人联机仍需游戏内验证。详见 [飞机 HUD](AIRCRAFT-HUD.md)。
