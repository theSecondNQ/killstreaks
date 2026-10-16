# 1.0.1.4333 资源压缩

## 体积约束和保留范围

生产 JAR 须小于 **100,000,000 字节**，Gradle 的 `jar` 任务及发行清单均检查此限制。这里采用十进制 MB，未通过拆分必需资源包规避限制；模组资源仍全部包含在单个 JAR 中。

本次生产包为 **98,877,191 字节（98.88 MB，94.30 MiB）**，上版为 248,406,818 字节，减少约 60.2%。

- 418 份运行模型保留完整浮点精度、全部顶点记录、三角面顺序及绕序、材质、骨骼、蒙皮和 UV。部分文件仅重新排列顶点存储，并同步索引，不改变绘制结果。
- 719 张受保护贴图的 RGBA 像素与上版一致，包括模型、物品、平板、手机、文字和准星。地图底图、损伤噪声等无文字背景保留尺寸，仅使用高质量调色板。
- 特效图集逐帧缩小，最小帧边长按原尺寸保护，不合并或删减动画帧；同步 `textureWidth`、`textureHeight`，保持实际尺寸与边缘 UV 内缩一致。高质量调色板压缩保留透明边界。
- 1233 个 OGG 文件和声音定义与上版逐字节一致；12 个语言文件保持原样。

本轮移出 183 个无须打包的资源：21 份已经由方块人替代的人物网格，以及无运行时引用或只用于来源记录的历史贴图。运行时仍读取人物骨骼 JSON、动作和挂点，不可删除这些文件。`sourceTexture416`、`sourceTexture417` 等 JSON 字段记录历史来源，当前特效读取 `texture` 字段。

完整旧源码包、JAR、逐文件备份及本轮暂存工具保存在工程外：

`D:\Projects\killstreakcod-archive\20261016-size-1.0.1.4333`

## 模型格式

所有 `.mesh.gz` 仍是独立 GZIP 文件，不依赖共享顶点池或额外模组。解压后的魔数为 `MWM3`，整数字段为大端序：

| 版本 | 顶点／索引记录的存储方式 |
| --- | --- |
| 1 | 原交错记录，直接读取，兼容已有资源包 |
| 2 | 按记录的字节列存储，读取时转回交错记录 |
| 3 | 每个 32 位分量先与前一个记录异或，再按字节列存储 |
| 4 | 每个 32 位分量先对前一个记录作模 2³² 差分，再按字节列存储 |

骨骼、材质及计数字段不参与变换。每个网格的顶点和面索引分别变换，顶点步长为 `36 + influences × 8`，索引步长为 4；每个分量的前值从零开始。运行时由 `ImportedMeshInput` 在首次加载时还原，普通渲染器继续读取版本 1 的数据，不增加逐帧解码。压缩格式检查长度、版本、计数及块边界，压缩模型解码大小上限为 96 MiB。

## 后续维护

`tools/mesh_codec.mjs` 只依赖 Node.js 标准库，读写版本 1 至 4，输出暂存文件，不覆盖输入：

```powershell
node tools/mesh_codec.mjs unpack src/main/resources/assets/killstreakcod/mw/models build/model-unpacked
node tools/mesh_codec.mjs pack src/main/resources/assets/killstreakcod/mw/models build/model-packed
```

`tools/pixel_models.mjs` 已接入相同的解码入口，后续处理原模型时可以继续读取本版格式。更新减面资源后如需再次压缩，应先生成并核对暂存结果，再应用；不要重复执行减面来追求包体限制。

构建及发行核对：

```powershell
.\tools\build.ps1 -Offline
python tools/release.py inventory
python tools/release.py package
```

本轮的字节、几何、透明通道和包体核对位于 `build/reports/compact-resources`。已通过生产构建及资源核对；未启动 Minecraft 进行实机体验。
