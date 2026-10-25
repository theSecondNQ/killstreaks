# 连杀获取提示

本次依据 `earned_ui_index/KILLSTREAK_EARNED_UI_RESOURCES.md`、原生属性轨道、构造布局及回调证据更新。NeoForge 1.21.1 为 **1.2.0.4354**，Forge 1.20.1 为 **1.2.0.4358**。

## 资源与表现

- 19 种可装备奖励及 MGB 按原生 MP 对应表使用各自图标；导入 25 张原尺寸 PNG，核对来源 SHA-256，保留透明边界。更新后的图标映射同样供终端和右侧连杀栏使用。
- 中央提示选用 `KillstreakEarnedSplash`：1920×1080 参考坐标、顶部偏移 200、居中 500×128 内容；菱形、奖励图标、上下背板、分隔线、双箭头、标题及提示分别绘制。上背板按原参数横向三段拉伸。
- 逐属性采样恢复的 duration、value、easing，不将并行通道的时间相加。入场于 0ms 启动图标/菱形，200ms 启动内容，350ms 启动提示闪烁；Scale=0 按原尺寸显示。
- 管理器显示时间 2000ms、隐藏阶段 600ms、下条间隔 300ms。HideSplash 内部按 100/200/300/400ms 回调触发图标、分隔线、背板与隐藏。横幅退场不再因播报长度拉长，下一条获取提示等待上一段就绪语音结束以免截断；就绪语音、音效和字幕仍与提示一起启动。
- 标题使用当前语言并转大写，操作提示读取当前实际键位；简体中文为“按下[%s]使用”，英文为“Press [%s] to use”，其余现有语言同步保留格式参数。
- 右侧已获得奖励增加原 `hud_earned_streak_backer`。连杀栏的完整排序动画不是中央获取提示，本次保留当前的数量、研究库存与栏位功能。

## 代码入口

- `StreakNoticeRenderer`：提示队列、时长、按键文字和声音字幕同步。
- `EarnedSplashAnimation`：资源属性通道、布局锚点、回调时间轴，独立于 Minecraft 绘制器。
- `EarnedSplashRenderer`：原图层组合、文字及屏幕适配。
- `OriginalRewardUi`：图标资源、完整透明边界和横向三段拉伸；统一资源重载时使提示缓存失效。
- `mw/earned-splash.json`：导入的数值轨道、布局、管理器默认值、20 项奖励和来源限制。
- `mw/notice-assets.json`：贴图元数据和图标映射。

## 重新导入

使用安装了 Pillow 的 Python：

```powershell
python tools/import_earned_ui.py --source 'D:\MW2022\work\KILLSTREAK_EXTRACTED'
```

导入器只读取原图、JSON 及已核对的字面数值，不执行反编译 Lua。原始 Lua、DDS、源字节码和分析报告不进入发行 JAR。Forge 使用相同的资源增量；develop 源码镜像继续排除 `resources`。

## 核对

`tools/tests/EarnedSplashAnimationCheck.java` 使用生产采样器和导入 JSON，对构造坐标、50/200/350ms 入场、闪烁、并行 RGB 通道、退场和重复/倒序采样进行 530 项检查，Java 17 运行通过。

Forge 隔离客户端另外实际绘制 100/225/450/1000/2050/2150/2350/2400ms 的八帧，核对提示可见性、Hide 边界以及前后普通文字像素一致。连续三次重载保留自定义键位、选中资源包和中文；418 个导入模型解码、终端模型与五个着色器检查通过。截图和报告位于 Forge `build/client-smoke/earned-ui-smoke`，客户端使用工程独立目录，不读写玩家存档或设置。NeoForge 完成生产构建和资源核对，未启动客户端；第三方模组/光影组合及多人队列仍需实际体验确认。

## 已知资源边界

原字体及完整 LUI shader 尚未恢复，采用 Minecraft 默认字体及加粗样式，并为本地化长文本缩放适配；没有注册全局字体替换。原文中的 `_PT` 单位、tracking、加色文字和精确字形效果无法据现有资源完全复现。

原回调调用 `OutroChevronFlicker`，实际注册的是 `OutroChevronBlink`；本次保留该不一致，不补造别名。恢复的音效别名保留为来源证据，本批次没有新音频，继续使用现有获取音效。不能将此实现称为完整原生 UI 系统。
