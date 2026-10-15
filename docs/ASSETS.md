# 资源与来源

运行资源位于 `src/main/resources/assets/killstreakcod`：

| 目录／文件 | 用途 |
| --- | --- |
| `mw/models/*.mesh.gz` | 439 份实际使用或由运行时 LOD 家族引用的模型二进制 |
| `mw/animations` | 动作、相机、物品、角色骨骼数据 |
| `mw/scene-models.json`、`mw/aircraft-crash-models.json` | 场景骨骼、绑定、插槽与 LOD 家族，游戏会读取 |
| `mw/scene-tracks`、`mw/cruise-phone-tracks` | 入场及过场相机轨迹 |
| `mw/*audio*.json`、`mw/radio-captions.json`、`sounds.json` | 原音效路由、字幕及 Minecraft 声音定义 |
| `mw/*fx*.json` | 特效图集参数、原生火力效果和碎片绑定 |
| `mw/*ui*.json`、`mw/*hud*.json`、`mw/*text*.json` | 平板、手机、遥控与字幕所用界面数据 |
| `textures`、`sounds` | 模型与 HUD 材质、图集、音效文件 |
| `models/item`、`lang` | 物品定义和中英文文本 |

模型二进制内部自带渲染所需材质、骨骼和顶点数据；同目录的逐模型导出说明 JSON 不参与加载，已移出工程。不能将上述运行时场景骨骼 JSON 与导出说明混为一类。

## 低多边形与像素材质

当前资源按“保留原轮廓、骨骼与动画，降低面数并采用像素贴图”的要求完成本地处理，共覆盖 439 份导入模型，以及这些模型、程序化回退模型和物品定义引用的 340 张贴图。333 份模型的面数降低；其余模型由面数下限、轮廓、透明面或屏幕面保护规则保留几何。合计三角面从 2,700,982 降至 1,794,442，减少约 33.56%。

模型骨骼、坐标与插槽头数据保持逐字节一致；减面后的顶点只选自原顶点记录，保留原法线、UV、颜色、关节索引和蒙皮权重。透明面、玻璃、旋翼模糊面、HUD 和 LCD 屏幕几何单独保护。材质名称、模型 ID 与 LOD 路由保持既有格式。程序化回退模型本身已由方块构成，使用同批像素贴图。

模型贴图最长边为 128 像素，物品贴图最长边为 64 像素，保留宽高比和透明通道，使用 64 色无抖动调色板，并设置 `blur=false`。动态屏幕和 HUD 的绘制代码继续读取原有路由。

转换工具为 `tools/pixel_models.mjs`，只生成暂存结果与完整校验清单。处理记录、原资源备份位置、静态对比和 Tripo 用量估算位于 `build/reports/pixel-models`。本批次使用本地转换，Tripo 积分消耗为 0；尚未进行 Minecraft 实机画面与动作验收。

原游戏资源由用户提供，来源工作区为 `D:\MW2022`，主要导出目录：

- `D:\MW2022\work\tools\saluki\exported_files\mw2_2022`
- `D:\MW2022\work\KILLSTREAK_EXTRACTED\new_extractions`
- `D:\MW2022\work\KILLSTREAK_EXTRACTED\reverse_engineering`

历史导入记录、来源 SHA256、逐模型说明及导入工具保存在：

`D:\Projects\killstreakcod-archive\20261015-4.29.0\workspace`

其中 `docs` 保存来源记录，`tools` 保存转换工具，`src/main/resources` 保存本次移出的导出说明，`build/libs/killstreakcod-4.28.2-source.zip` 保存整理前的完整源码快照。参考视频和兼容模组 JAR 也已移入此归档。当前构建使用转换后的资源，不需要连接原资源目录。
