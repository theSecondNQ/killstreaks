# 核弹倒计时界面

依据 `nuke_ui_index/NUKE_COUNTDOWN_UI_RESOURCES.md`、MP 构造组件树、NukeInbound 原始属性轨道、原始配色及材质替换证据接入。NeoForge 1.21.1 为 **1.2.0.4361**，Forge 1.20.1 为 **1.2.0.4365**。

## 显示与资源

- 使用 MP `NukeWarningContainer → NukeWarning` 的 NukeKillstreak 位置状态：顶部锚点、居中 1920×104 子组件。保留 1920×227 容器和父级 1920×96 的来源区分；当前绘制采用该状态实际使用的 104 高度。
- 导入 25 个 MP 控件的构造状态及独立属性通道，保留原顺序、可见性、Alpha、RGB、材质切换、混合方式和位置。主序列为 10000ms，9800ms 后按原回调的 250ms Alpha 退场。
- 主警告使用原 `hud_splash_caution_icon`，在 t=0 覆盖构造默认的骷髅图；其余原图为斜块、斜块背板、1200×64 大背板及右到左渐变，共五张完整分辨率 PNG。导入器核对源 SHA-256 和尺寸。
- 标题为原文“核弹来袭”；右上角显示呼叫者和“呼叫了核弹打击！”，两侧显示警告。简体中文与英文取恢复的原值，其余已有语言补齐新文字。
- 计时数字继续使用实时文字，不伪造数字 PNG。沿用模组的一位小数显示；原 UICountdown 的 C 端精确数字排版尚未恢复。

## 服务端时间线

`MgbCountdownClock` 根据服务端快照的 gameTime 与 remaining 计算结束刻，并映射到本地世界时间。装饰动画由当前剩余时间定位，不按渲染帧减秒；晚加入直接进入对应阶段，不重播完整入场。重复或乱序快照不重启时间线，新的服务端结束时间可以重新同步。维度切换、登出、取消和新一轮核弹重置时钟。

当前服务端核弹保持原有 200 tick / 10 秒倒计时与伤害流程。归零只隐藏客户端提示；核爆与伤害由现有服务端流程触发。资源重载仅清理 UI 缓存，不重置核弹时钟。

## 代码与导入

- `MgbWarning`：服务端消息、核弹警告音效、生命周期和 HUD 事件。
- `MgbCountdownClock`：结束刻同步、插值及晚加入定位。
- `NukeUiAnimation`：编译布局、数值/离散通道及状态采样。
- `NukeUiRenderer`：原图、实时文字及屏幕适配。
- `mw/nuke-ui.json`：25 个控件与运行数据，约 93 KB。
- `textures/gui/mwii/nuke/`：五张原图。

重新导入需要已安装 Pillow 的 Python：

```powershell
python tools/import_nuke_ui.py --source 'D:\MW2022\work\KILLSTREAK_EXTRACTED'
```

工具只解析字面算术、已核对的 swatch 与枚举，不执行 Lua。CP、地面战争、DEFCON 及对照图片不进入此 MGB 主提示。独立资源增量 ZIP 的 `assets` 目录可解压到 `src/main/resources`；完整发行 JAR 已包含所需资源。develop 镜像继续排除 resources。

## 验证范围

`tools/tests/NukeUiAnimationCheck.java` 用生产采样器核对原生位置终点、100/150/700ms 可见性、材质替换、并行 RGB/位置通道、9800ms 淡出和计时同步，并遍历所有控件检查数值/材质有效性，共 20545 项通过。

Forge 隔离客户端实际绘制 0/99/100/149/150/300/700/1500/5000/8500/9800/9925/10000ms 的十三帧，核对提示与数字出现时机、归零隐藏，以及绘制前后的普通文字像素一致。另检查上一版获取提示八帧和连续三次资源重载，保留自定义键位、选中资源包及中文字形。结果和截图在 `build/client-smoke/nuke-ui-smoke`。测试只使用工程独立目录。

两个平台均完成生产构建与资源核对；NeoForge 未启动客户端。本次没有运行实际世界中的完整核爆或多人联机流程，也没有把第三方模组/光影组合视为已验证。

## 原资源边界

原字体、完整 glitch/pixel-grid/遮罩 shader 和 UICountdown 原生 C 绘制尚未恢复。采用 Minecraft 默认字体和普通 GUI 文字绘制，图片按恢复的原配色及透明/加色方式绘制；没有全局字体替换或新的渲染 Mixin。保留 Alpha 退场，未补造完整 glitch 效果。当前实现针对标准 MP timerType=0，不将 CP 和其它模式拼接为同一界面。
