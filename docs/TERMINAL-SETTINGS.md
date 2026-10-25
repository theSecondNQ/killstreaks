# 手持终端设置与服务器规则

手持终端右上角的“设置”统一进入四个页面：常规、服务器、客户端、按键。原先直接显示的阵营、皮肤和白名单入口已移入此处。

## 保存范围和权限

| 页面 | 生效对象 | 保存方式 |
| --- | --- | --- |
| 常规：阵营语音 | 当前玩家 | 选择后保存，由服务器确认；不受战斗中禁止更换连杀配置的限制 |
| 常规：人物皮肤 | 本机的三个操作员岗位 | 进入原皮肤选择页，读取游戏目录的 `killstreakcod-skins` PNG |
| 常规：队友白名单 | 当前世界、当前玩家的队友关系 | 进入原白名单页，由服务器保存 JSON |
| 服务器 | 当前实例的世界；联机使用服务器规则 | 房主或二级及以上权限管理员修改，点击保存；普通玩家只读 |
| 客户端 | 本机画面、特效和音量 | 保存至 `config/killstreakcod-client.toml` |
| 按键 | 本机 | 改键后立即保存至 Minecraft 键位设置，原版控制页面也能修改 |

更改页签或进入皮肤／白名单页面时保留当前设置草稿。返回连杀配置页会放弃尚未保存的设置；连杀配置本身的未保存选择保留。
服务器保存只发送发生变化的键，服务器校验权限、键名、有限数值、整数及取值范围。等待确认期间锁定编辑，超时保留输入并允许刷新重试。

服务器规则仍注册为原生 `SERVER` 类型。NeoForge 默认从全局 `config` 读取，沿用加载器对已有世界覆盖文件的处理。
Forge 的 `GlobalServerConfig` 使用公开的 `ModConfig`／`ConfigFileTypeHandler` 扩展接口，将本模组的读取、文件监视和卸载定位到全局 `config`，保留原生登录同步，不新增 Mixin。

## 新服务器选项

文件：`config/killstreakcod-server.toml`。已有的计数、重复获取、核弹、无畏装甲及禁用升级选项保留。

```toml
[earning]
hostileKillsOnly = true

[research]
terminalEnabled = true

[airspace]
airspaceChecks = true
gunshipViewDistance = 32
```

- `hostileKillsOnly`：默认只计敌对生物、Boss 和敌方玩家。关闭后其他非友军生物也计入；友军、盔甲架和本模组支援单位不计入。默认规则下空载模组载具的击毁不额外计数，乘员死亡按原击杀路径结算。
- `terminalEnabled`：关闭后创造和生存玩家均使用连杀模式，不要求研究或材料储备，且全部升级效果停用。**获取奖励仍要求达到击杀／得分门槛**。研究、装配、预购储备与原模式选择保留，重新启用后继续使用。总部方块此时打开手持连杀配置界面。
- `airspaceChecks`：关闭后跳过呼叫前的航线空间与自动抬升检查，以及飞行中的空间阻挡检测。地狱禁飞保持生效。
- `gunshipViewDistance`：炮艇地面区块观察半径，默认 32，允许 16～32。客户端同名设置默认 32，实际可见范围不超过服务端发送半径。较大范围会增加区块加载、内存和带宽消耗；退出遥控继续使用现有恢复与区块票据清理路径。

## 伤害配置

`[damage]` 中以下默认值保持当前武器平衡。单位为生命值，1 点为半颗心；范围 0～1,000,000，0 关闭该通道的伤害。无需配置没有攻击能力的侦察或补给奖励。

| 配置键 | 武器／用途 | 默认 |
| --- | --- | ---: |
| bombDrone | 炸弹无人机 | 32 |
| clusterMine | 集束地雷 | 35 |
| mortar | 迫击炮打击 | 64 |
| precisionAirstrike | 精确空袭 | 24 |
| cruiseMissile | 巡航导弹 | 180 |
| sae | 战略轰炸子弹药 | 240 |
| sentryGun | 哨戒机枪 | 3 |
| wheelsonCannon / wheelsonSplash | 遥控战车直击／溅射 | 6 / 8 |
| overwatchHelo | 支援直升机 | 3 |
| vtolCannon / vtolRocket | 垂直起降战机机炮／火箭弹 | 3.5 / 55 |
| vtolHerculesCannon / vtolHerculesSplash | 大力神套件直击／溅射 | 8 / 12 |
| stealthBomber | 隐形轰炸机 | 100 |
| chopperCannon / chopperSplash / chopperRocket | 攻击直升机机炮／溅射／火箭弹 | 14 / 20 / 60 |
| gunship25mm / gunship25mmSplash | 空中炮艇 25mm 直击／溅射 | 8 / 12 |
| gunship40mm / gunship105mm | 空中炮艇 40mm／105mm | 45 / 100 |
| guidedRocket | 研究增加的制导火箭弹 | 55 |
| m134 | 无畏装甲 M134 | 8 |
| mgb | 核弹 | 100000 |

`StreakDamage` 在统一直击和爆炸入口按“配置值 ÷ 原基础值”换算。因此原有升级、自动射击变体、装甲减伤、爆炸距离衰减仍然生效；实际命中伤害可能不等于面板基础值。尾焰、弹道、音效和命中特效不因伤害数值改变类别。

## 武器按键

新增五项独立绑定：遥控主武器开火、遥控副武器发射、M134 预热／开火、炸弹无人机引爆、巡航导弹加速。
默认分别为鼠标左键、右键、右键、左键、左键；各自只在对应控制情境读取。
原有武器 1～5、武器切换、热成像、缩放、干扰弹、自动驾驶、退出、连杀呼叫等按键也在设置列表中提供。

- 遥控副武器保留“一次按下发射一发”，鼠标短按和键盘短按均缓存到下一控制刻。
- HUD 根据当前绑定绘制提示，移动／升降仍读取原版已配置的移动、跳跃、潜行键。
- 终端录入单键，Esc 解除绑定；组合修饰键可在原版键位页设置。重新录入单键会清除旧修饰键，不会重置其他模组的按键。
- M134 使用独立的服务端开火时钟，客户端每四刻发送按住心跳；死亡、换物品、维度切换、退出和超时都会结束开火。射速、蓄转和伤害由服务器决定，不依赖伪造原版使用物品键。

新增网络消息后，内部网络协议升级到 `1.1.0`，用于拒绝旧协议客户端混连；发行版本号保持 NeoForge `1.2.0.4361`、Forge `1.2.0.4365`。

## 源码同步和语言资源

两个源码镜像继续排除 `resources`。本次 12 种语言 JSON 通过单独资源增量 ZIP 提供。
Forge 构建可直接使用解压后的语言目录，不改写源码镜像内的资源：

```powershell
.\tools\build.ps1 -Offline -Tasks @('-PkillstreakLanguageDir=D:/path/to/assets/killstreakcod/lang', 'jar')
python tools/release.py inventory --language-overlay D:/path/to/assets/killstreakcod/lang
```

相同 Gradle 参数也适用于 `-PuiPreview runClient`。实际界面预览另外导出常规、服务器、客户端及按键四页，使用示例快照，不加载玩家世界。

连杀栏三种显示模式、自动收起、F10 布局编辑器和本机 JSON 的使用方式见 [UI 布局](UI-LAYOUT.md)。
