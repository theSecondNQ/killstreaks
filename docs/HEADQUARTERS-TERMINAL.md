# 总部通讯终端

新增方块和物品 ID：`killstreakcod:headquarters_terminal`。当前版本为 **1.1.0.4341**。

## 获取与使用

在创造模式的“连杀奖励”标签页可直接取出。生存模式在工作台按以下位置合成一个终端：

| 铁锭 | 钻石 | 铁锭 |
| --- | --- | --- |
| 铁锭 | 红石中继器 | 铁锭 |
| 铁块 | 红石比较器 | 铁块 |

取得红石中继器后解锁配方书条目。终端占两格高度，需放在上表面坚实的方块上，并留出上方空间。放置后屏幕朝向玩家，右键上下任意一部分均可打开。

科技树已接入《新系统.docx》中的研究规则：侦察与支援共9项连杀、29项升级，以及四级终端、生存／连杀模式。画布支持拖拽与滚动，右侧显示条件和费用；火力、现代化暂为空分类。流程、费用及存储说明见[研究系统](RESEARCH.md)。按 Esc、背包键或“返回”关闭。

用石镐或更高等级的镐拆除，可取回一个终端；上下部分同时移除。菜单由服务端管理，底座被拆除或玩家离开有效距离后自动关闭。创造拆除不掉落物品。

## 模型

直接读取用户提供的 `assets/terminal.obj`、`assets/terminal.mtl` 及同目录下的九张 `laptop_*.png`。这 11 个原文件逐字节复制进运行资源，不调整几何、缩放、旋转、法线、UV 或材质，也不再映射到 Minecraft 原版材质。旧的临时蓝色网格屏幕已移出运行资源。

OBJ 包含 176 个顶点、22 个部件和 132 个四边形面。专用客户端加载器按导出顺序保留部件，包括三个同名 `cube`，避免按名称存储时丢失底座。模型在资源加载时烘焙为普通方块面，沿用方块渲染管线；游戏中不逐帧解析 OBJ。

底座的 2 个部件与上半部分的 20 个部件分别由上下方块显示，物品显示完整模型。只按 Minecraft 方块坐标约定挂接原点：OBJ 居中的 X/Z 移至方块中心，上半部分使用其所在格的局部 Y；不裁切原面，不改变部件间相对位置。沿用现有方块朝向及物品展示设置。

MTL 的九个 `map_Kd` 名称对应原 PNG，按 Wavefront 的 UV 约定读取；PNG 不翻转、不重编码。原模型、MTL 位于 `models/block/headquarters_terminal/`，贴图位于 `textures/block/headquarters_terminal/`，全部在 `killstreakcod` 命名空间下。

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

现有便携式连杀配置终端继续使用原注册 ID。新增总部终端通过标准容器协议打开，不增加自定义网络消息，也不改动奖励与世界存档格式。
