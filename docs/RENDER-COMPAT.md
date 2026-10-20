# Forge 1.20.1 渲染兼容修复

当前版本为 1.1.0.4355，修复着色器切换和透明层次错误造成的黑边闪烁，并保留前版的朝向、特效矩阵和巡航导弹震屏修复。运行时仍不需要额外前置；Embeddium、Oculus 都是可选模组。

## 修复原因

- Forge 1.20.1 在 `AFTER_LEVEL` 事件中传入的是投影姿态栈。将它当作世界视图矩阵，会使本应在摄像机前方的特效落到裁剪范围之外。`WorldRenderMatrices` 从同一帧的世界阶段保存真实视图，供爆炸精灵、曳光线、热成像遮罩及核爆绘制使用，并排除光影的阴影阶段。
- 1.20.1 的 `Camera.rotation()` 与原工程所用版本的视图约定不同。巡航导弹分离和推进器点火触发震屏时，旧的四元数相乘方式会把摄像机局部模型大幅转离视野。现在由相同约定下的震动前、后视图计算相对变换，震屏保留为小角度运动。
- Forge 1.20.1 的 `ComputeCameraAngles` 经由 `Camera.setAnglesInternal` 更新角度字段，但不会同步 `Camera.rotation()` 四元数。继续用该四元数构造特效平面，会让平面朝向与真实视图不同，甚至侧转到不可见。4354 从同一帧的真实视图派生、缓存特效朝向，供爆炸／烟雾精灵、曳光回退平面及炮口闪光使用，同时包括镜头 roll。
- `ShaderInstance.apply()` 会应用 JSON 中的混合模式；省略 `blend` 时为不透明。旧的精灵和曳光着色器缺少这个声明，因而绘制前设置的透明混合会在程序切换时被关闭。同一张 50% 透明黑色贴图在灰色背景上，可随前一个程序不同而输出灰色或纯黑。4355 为普通精灵、发光精灵和曳光分别声明匹配的混合模式，不同精灵模式使用独立着色程序。
- 旧实现先排序，再把所有相同贴图合为一个批次，破坏了烟雾与火光之间的透明顺序。现在只合并排序后相邻且材质相同的精灵，并按本帧插值位置在真实视图中的深度排序，避免使用出生位置和横向距离判断前后层次。
- 状态恢复同时保存实际 OpenGL 混合状态与 `BlendMode.lastApplied` 缓存；两者不同步时，后续原版着色器可能覆盖暗角使用的特殊混合系数。该缓存通过客户端 Mixin 访问器读取和恢复，避免光影关闭后出现整屏黑色暗角。最终主画面检查保留原版暗角叠层。

巡航导弹继续在原实体阶段使用正常材质、光照及世界深度。排查时用于替换材质、改变阶段、关闭深度检测的实验代码已移除。

## 隔离回归

测试使用 Embeddium **0.3.31+mc1.20.1**、Oculus **1.8.0**、Photon **v1.4**，分别检查启用、停用光影的状态。兼容依赖仅在显式添加 `-PrenderCompat` 时加入开发运行环境，不打进发行包，也不读取玩家存档。

先把本机实例中的测试依赖复制到 `build/render-compat`：

```powershell
python tools/prepare_render_compat.py 'D:\Minecraft\.minecraft\versions\1.20.1-Forge_47.4.28'
.\tools\build.ps1 -Offline -Tasks @('-PrenderCompat', 'runClient')
.\tools\build.ps1 -Offline -Tasks @('-PrenderCompat', '-PcruiseSmoke', 'runClient')
.\tools\build.ps1 -Offline -Tasks @('-PrenderCompat', '-PbillboardSmoke', 'runClient')
.\tools\build.ps1 -Offline -Tasks @('-PrenderCompat', '-PalphaSmoke', 'runClient')
```

运行同一准备命令并添加 `--no-shaders`，再重复检查，可测试光影关闭状态。测试游戏的存档、配置和截图位于工程的 `build` 目录，依赖使用 Gradle 缓存；测试客户端隐藏窗口，检查完成后自动退出。

特效检查由真实测试服务器发送爆炸、曳光消息，确认客户端接收，并检查实际渲染路径的像素输出、主画面的变化、自定义着色器和震屏角度。`render-compat.json` 与最终画面 `render-compat.png` 位于 `build/render-compat/game`。

导弹检查通过正常的服务端部署入口呼叫巡航导弹，在新的平坦世界、固定日间光照下，从最终画面采样入场第 0、10、20、30、40、50、56 tick。第 30、40 tick 的弹体像素检查能捕获震屏引起的消失；结果及截图为 `cruise-smoke.json`、`cruise-smoke/cruise-*.png`。测试源码位于 `src/gameTest`，不包含在正式 JAR 中。

4354 新增方向回归：使用实际特效绘制器和方向标记贴图，连续检查 112 组普通／视口覆盖角度，包括俯仰、±60° 镜头倾斜以及 6／24／96／192 格距离。既检查最终主画面的标记方向，也比较空深度与真实地形深度下的绘制结果：可见特效必须保留，位于地下的平面必须被遮挡。结果及逐帧截图为 `billboard-smoke.json`、`billboard-main-*.png`。此前只检查存在像素，不能捕获平面朝向错误；新的检查能在旧实现的 90° 方向复现消失，在 180° 方向复现贴图翻转。

4355 新增透明回归：每种光影状态共 106 项，包括 4 项透明／发光混合检查、6 种真实曳光的程序切换比较、48 帧重叠层次比较，以及 48 帧最终主画面采样。重叠场景使用半透明边缘标记和实际巡航爆炸贴图，覆盖静止、带速度移动及偏离画面中心的精灵；每帧与按真实前后顺序逐层绘制的结果比较。混合检查会主动切换前一个程序的混合状态，验证同一输入始终输出正确半透明结果。`alpha-smoke.json`、`alpha-actual-*.png`、`alpha-expected-*.png`、`alpha-main-*.png` 保存报告和代表帧。

排查中，旧混合声明在非不透明程序之后将预期灰度 32／192 分别画成 0／255；补齐声明后四项检查全部正确。保留原合批方式仍有 24/32 项重叠比较失败；仅修复合批后，静止场景全部通过，剩余 8 项移动场景失败，进一步定位到出生位置排序。六种曳光也都能在旧声明下复现程序切换前后的像素差异。诊断截图和分步报告保留在 `build/render-compat/evidence/alpha-*`，不打进发行包。

最终主画面检查还捕获了仅恢复 OpenGL 状态时的暗角回归：光影关闭后 48/48 帧采样变黑；同步恢复混合缓存后，原版暗角与半透明精灵都保持正确。

发行包还需通过常规构建、服务端 GameTest、无兼容模组的客户端启动及资源核对。结果与发行 JAR 的 SHA-256 关联在 `build/reports/forge-validation.json`。

这些检查覆盖上述渲染组合、爆炸／曳光和巡航导弹入场；完整连杀流程、其他光影包及真实多人联机仍需游戏内验证。

兼容实现参考：[Embeddium 开发者说明](https://github.com/FiniteReality/embeddium/wiki/For-Developers)、[OpenGL 透明排序说明](https://wikis.khronos.org/opengl/Transparency_Sorting)。着色器混合行为同时核对了本机 Forge 1.20.1 映射运行库中的 `ShaderInstance` 和 `BlendMode`。
