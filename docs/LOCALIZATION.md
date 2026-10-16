# 语言支持 / Localization

## 当前语言

Minecraft → 选项 → 语言，切换后由游戏重新加载资源。无需在模组配置中重复选择。

| 语言 | Minecraft 语言代码 | 界面 | 无线电台词字幕 |
| --- | --- | --- | --- |
| 简体中文 | `zh_cn` | 已翻译 | 原中文字幕 |
| 繁體中文（台灣） | `zh_tw` | 完整繁中校订 | 完整繁中校订 |
| 繁體中文（香港） | `zh_hk` | 已翻译 | 完整繁中校订 |
| 文言（華夏） | `lzh` | 文言译写 | 文言译写 |
| English (US) | `en_us` | 已翻译 | 原英文字幕 |
| 日本語 | `ja_jp` | 已翻译 | 英文回退 |
| 한국어 | `ko_kr` | 已翻译 | 英文回退 |
| Deutsch | `de_de` | 已翻译 | 英文回退 |
| Français | `fr_fr` | 已翻译 | 英文回退 |
| Español (España) | `es_es` | 已翻译 | 英文回退 |
| Русский | `ru_ru` | 已翻译 | 英文回退 |
| Português (Brasil) | `pt_br` | 已翻译 | 英文回退 |

12 个语言选项各含 423 个界面键，覆盖终端、奖励与兑换物品、按键设置、白名单、操作员皮肤、聊天提示、击杀提示、平板及手机文字、遥控 HUD、声音字幕和 NeoForge 配置界面。装备及人物名称为本模组翻译，新增语言不宣称逐项对应原游戏官方译名。

无线电仍播放原英文音频。694 条语音按实际播放资源对应原台词，播报人物继续区分阵营与载具。英语、简体中文、台湾繁中、香港繁中和文言各含完整的 1,151 个键：423 个界面键、694 个台词键、34 个人物键。其余语言的无线电字幕回退英文。

文言为本模组依现有中文字幕译写，使用“連斬之賞”“請賞”“已備”“同袍名籍”等措辞，装备专名、型号和指令 ID 保持可识别。全部 316 种不同中文台词逐条译写后对应回 694 条语音，不用通用事件文字替代实际台词，也不更改播报时序。

原版台湾繁中、香港繁中是独立语言选项。Minecraft 不会将 `zh_hk` 自动转到 `zh_tw`；本模组为二者分别提供完整文件，避免香港繁中回退到英文。繁中同时校订“並”“稍後”“遊戲”“關係”“重複”“剩餘”等用字及“伺服器”“檔案”“資料夾”“滑鼠”“儲存”等界面用语。

原始贴图中烘焙的英文、仪表符号、单位、武器口径及型号继续作为美术资源显示。语音音频没有重新配音。模组列表名称在各语言均为 **Killstreak**；简体中文创造标签保持 **连杀奖励**。

## 资源位置与回退

- 语言文件：`src/main/resources/assets/killstreakcod/lang/<语言代码>.json`，UTF-8，无 BOM。
- 原始字幕路由：`assets/killstreakcod/mw/radio-captions.json`。
- 英文字幕来自用户提供的 `killstreak_subtitle_source/localizations.json`，按 `subtitleLocalizeId` 对应，构建与运行均不依赖导出目录。
- `radio.killstreakcod.line.<subtitleLocalizeId>` 表示原台词；`radio.killstreakcod.speaker.<hash>` 表示当前播报人物。
- 人物键使用 `displaySpeaker` 的 Java `String.hashCode()` 无符号十六进制值：原人物 ID 被多种岗位复用，不能用它覆盖不同操作员。
- Minecraft 先合并英文，再加载所选语言和资源包。英语、简中、台港繁中与文言都有完整字幕；其他语言缺少的无线电键沿用英文。若资源包也缺失该键，使用路由文件内的原中文字幕和人物名称。
- 更改语言时，统一的客户端资源重载入口清空平板、手机、HUD 和字幕缓存；字体继续使用游戏自带 Unicode 字体。

资源包可以直接覆盖同名语言键，为其他语言补充实际台词字幕。不要在翻译中写入语音路由、奖励 ID 或 Java 类名。

## 维护约定

1. 为新增可见文字添加 `en_us.json` 和 `zh_cn.json` 键，并补齐本表中的界面语言。
2. 保留 `%s`、`%1$s` 等参数及 `%%` 百分号转义；需要调整参数次序时使用带序号参数。
3. `screen.killstreakcod.phone.confirm` 保留两行显示，其余短屏幕文案保持简洁。
4. 服务端发送 `Component.translatable`，不要提前调用 `getString()` 把奖励名固定为服务器语言。
5. 按键提示使用客户端实际绑定名称；原资源内烘焙的按键图案不由语言文件改变。
6. `python tools/release.py inventory` 核对完整界面键、重复键、占位符、字幕路由、五种完整字幕、MCreator 语言登记及繁中混用残留，结果记录在 `package-inventory.json` 的 `localization` 字段。

## 源码同步

`D:\Projects\killstreakcod` 为含运行资源的完整开发工程。按本次要求，`D:\Projects\killstreaks` 仅同步源码、版本、文档和维护工具，跳过所有名为 `resources` 的目录。

同步不会复制模组 JAR、完整源码 ZIP、构建缓存、游戏运行目录或素材转换目录，因为它们可能间接带入运行资源。目标仓库的 `.git`、`LICENSE` 和其他既有文件保留。新语言 JSON 只存在于完整工程及其发行包中；源码仓库需要另外提供匹配资源才能构建完整模组。

## English summary

The UI follows Minecraft's selected language and has 423 translated entries in twelve locale options. English, Simplified Chinese, Traditional Chinese (Taiwan and Hong Kong), and Literary Chinese each cover all 1,151 UI and radio keys. Literary Chinese captions are this mod's translation of the existing Chinese lines. Other locales use English radio captions. Audio remains English. Text baked into artwork is not translated. The source-only `killstreaks` synchronization excludes every `resources` directory and archives containing resources.
