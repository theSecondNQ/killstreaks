# 总部通讯终端

新增方块和物品 ID：`killstreakcod:headquarters_terminal`。当前版本为 **1.1.0.4352**。

## 获取与使用

在创造模式的“连杀奖励”标签页可直接取出。生存模式在工作台按以下位置合成一个终端：

| 铁锭 | 钻石 | 铁锭 |
| --- | --- | --- |
| 铁锭 | 红石中继器 | 铁锭 |
| 铁块 | 红石比较器 | 铁块 |

取得红石中继器后解锁配方书条目。终端占两格高度，需放在上表面坚实的方块上，并留出上方空间。放置后屏幕朝向玩家，右键上下任意一部分均可打开。

科技树已接入更新后的《新系统.docx》：四个分类共20项基础研究（含核弹）、59项升级，以及四级终端、生存／连杀模式。画布支持拖拽与滚动，条目按终端等级及所需经验升序排列。右侧显示条件和费用，已研究升级可免费装卸，并在两种模式中生效。流程及存储见[研究系统](RESEARCH.md)，新增连杀及武器操作见[火力与现代化研究](RESEARCH-EXPANSION.md)。按 Esc、背包键或“返回”关闭。

用石镐或更高等级的镐拆除，可取回一个终端；上下部分同时移除。菜单由服务端管理，底座被拆除或玩家离开有效距离后自动关闭。创造拆除不掉落物品。

## 模型

直接读取用户提供的 `assets/terminal.obj`、`assets/terminal.mtl` 及同目录下的九张 `laptop_*.png`。这 11 个原文件逐字节复制进运行资源，不改写源文件中的几何、缩放、旋转、法线、UV 或材质，也不再映射到 Minecraft 原版材质。旧的临时蓝色网格屏幕已移出运行资源。

OBJ 包含 176 个顶点、22 个部件和 132 个四边形面。专用客户端加载器按导出顺序保留部件，包括三个同名 `cube`，避免按名称存储时丢失底座。模型在资源加载时烘焙为普通方块面，沿用方块渲染管线；游戏中不逐帧解析 OBJ。

底座的 2 个部件与上半部分的 20 个部件分别由上下方块显示，物品显示完整模型。只按 Minecraft 方块坐标约定挂接原点：OBJ 居中的 X/Z 移至方块中心，上半部分使用其所在格的局部 Y；不裁切原面，不改变部件间相对位置。沿用现有方块朝向及物品展示设置。

MTL 的九个 `map_Kd` 名称对应原 PNG，按 Wavefront 的 UV 约定读取；PNG 不翻转、不重编码。九张贴图均完全不透明，方块上下半部及物品使用 `minecraft:solid` 渲染，避免错误切入透明裁剪通道。立式底座的前后两面在原导出中法线朝内，仅按原法线校正绕序仍会缺面。加载时以各个封闭长方体部件的中心判断朝外方向，先校正朝内法线，再使面绕序与校正后的法线一致；保留全部22个部件和132个原面。图集边缘采样轻微收至当前图块内，避免多级纹理或光影采到透明边界。原 OBJ／MTL／PNG 字节保持一致。原模型、MTL 位于 `models/block/headquarters_terminal/`，贴图位于 `textures/block/headquarters_terminal/`，全部在 `killstreakcod` 命名空间下。

重新打包原模型：

```powershell
python tools/import_terminal_model.py
```

工具只复制这 11 个原文件并生成三个模型入口 JSON，不改写建模输入。导入清单位于 `build/reports/headquarters-terminal/model-import.json`，记录原文件和运行副本的 SHA-256。完整源码包包含这 11 个建模输入，其他大型参考素材不参与打包。原 `assets/terminal.json` 保留在工作区。

## 代码位置

| 位置 | 职责 |
| --- | --- |
| `killstreaks/HeadquartersTerminalBlock` | 朝向、上下配对、放置、拆除及菜单入口 |
| `universal/HeadquartersTerminalMenu` | 服务端会话与有效距离检查 |
| `render/HeadquartersTerminalScreen` | 客户端菜单注册和基础科技树界面 |
| `render/HeadquartersTerminalObjModel` | 保留重名部件的 OBJ 读取、原 MTL/PNG 解析及方块面烘焙 |
| `universal/KillstreakContent` | 方块、物品与菜单类型注册，沿用本模组创造页 |

现有便携式连杀配置终端继续使用原注册 ID。总部终端通过标准容器协议打开，研究操作及储备状态通过有界消息同步，世界研究格式为 Schema 4；具体规则见[研究系统](RESEARCH.md)。
