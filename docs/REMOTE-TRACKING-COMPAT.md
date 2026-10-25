# 遥控实体跟踪与自由视角兼容

版本保持 NeoForge 1.21.1 **1.2.0.4361**、Forge 1.20.1 **1.2.0.4365**。

## 崩溃原因

2026-10-25 的 Forge 1.20.1 报告在世界加载实体时出现 `TrackedEntityRemoteViewMixin` 注入失败。对应的 `latest.log` 明确记录：本模组的 `killstreak$cameraPosition` 和百年战争 0.7.1r-fix1 的 `hyw$useFreecamPositionForTracking` 都使用 `@Redirect` 替换 `ChunkMap.TrackedEntity.updatePlayer` 中同一次 `ServerPlayer.position()` 调用。两者优先级均为 1000，其中一个重定向被跳过后，强制注入检查失败，导致服务器中止世界加载。

发行包 refmap 中的方法映射与本机 Forge 游戏运行库一致。崩溃报告顶部的 Embeddium/Oculus 提示不属于本次注入失败的来源。

## 实现

- `TrackedEntityRemoteViewMixin` 改为 `@ModifyVariable`，在原有位置差向量存入局部变量时调整它，不再替换玩家位置调用。
- 本模组遥控有效时，使用“摄像机位置减被跟踪实体位置”；其他时候直接返回已经计算好的向量，因此保留原版和其他模组的自由视角结果。
- 不改变原有的可见性、实体广播、距离上限、配对与解除配对流程；区块订阅仍由各平台现有实现负责。
- 保持强制注入检查，避免注入失效后静默丢失遥控实体跟踪。
- 同一混入更新到两个平台及 NeoForge 源码镜像。无须更新贴图、模型、语言或 Mixin 资源配置。

## 核对范围

此次使用用户崩溃报告、对应日志的重定向冲突记录，以及本机 Minecraft 和百年战争 JAR 的字节码定位原因。生产编译与发行包核对记录位于 `build/logs/remote-tracking-mixin-compat-*.log`。

未新增或运行自动化测试，未打开玩家存档验证世界加载或百年战争自由视角；实际组合仍需在游戏中体验确认。
