# CobbleRide

CobbleRide 是一个独立开发、非官方的 Cobblemon Fabric 附属模组，为队伍中的可骑乘生物提供快捷召唤、模型选择轮盘，以及陆地、水面和空中的动态换乘功能。

> 当前版本：**1.0**  
> Minecraft：**1.21.1**  
> 模组加载器：**Fabric**

## 功能

- 短按 `V` 召唤并骑乘上次坐骑；没有记录时自动选择队伍中位置靠前的可骑乘成员。
- 骑乘时短按 `V`，下马并收回当前坐骑。
- 长按 `V` 约 0.25 秒打开选择轮盘，移动鼠标选择，松开按键确认。
- 轮盘只显示队伍中拥有有效骑乘配置且未濒死的成员，并展示名称、等级和实时模型。
- 支持在陆地、水面和空中召唤与换乘。
- 新坐骑会先靠近玩家，再完成换乘并收回旧坐骑。
- 飞行坐骑会预测高速移动玩家的位置并加速追赶。
- 空中换成地面坐骑时，地面坐骑会尝试接住下落的玩家并消除坠落伤害。
- 水面召唤飞行坐骑时，坐骑会以空中状态出现在水面上方。
- 使用 Cobblemon 提供的原生骑乘行为，保留不同坐骑的速度、动画、耐力和操控特点。

## 操作

| 操作 | 效果 |
| --- | --- |
| 短按 `V` | 快速召唤上次坐骑；无记录时选择队伍中靠前的可骑乘成员 |
| 骑乘时短按 `V` | 下马并收回当前坐骑 |
| 长按 `V` | 打开骑乘选择轮盘 |
| 移动鼠标 | 选择轮盘中的成员 |
| 松开 `V` | 确认召唤或换乘 |

快捷键可在 Minecraft 的按键设置中重新绑定。

## 依赖

- Minecraft 1.21.1
- Java 21+
- Fabric Loader 0.19.3+
- Fabric API
- Fabric Language Kotlin
- Cobblemon 1.7.1+（推荐 1.7.3）

客户端和服务端均需安装 CobbleRide 及以上运行依赖。

## 安装

1. 安装适用于 Minecraft 1.21.1 的 Fabric Loader。
2. 安装 Fabric API、Fabric Language Kotlin 和 Cobblemon。
3. 将 `cobbleride-1.0.jar` 放入客户端与服务端的 `mods` 文件夹。
4. 删除旧版 CobbleRide，确保只保留一个版本。

## 构建

使用 Java 21，在项目目录运行：

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/`。

依赖通过 Gradle 从其官方 Maven 仓库取得，不应将 Minecraft、Cobblemon 或其他第三方 JAR 提交到本仓库。

## 独立项目声明

CobbleRide 是社区制作的非官方兼容扩展。项目与 Mojang Studios、Microsoft、The Pokémon Company、Nintendo、Game Freak、Creatures Inc. 及 Cobblemon 开发团队不存在隶属、授权、赞助、认可或合作关系。

Minecraft、Pokémon、Cobblemon 及相关名称、角色、商标和素材归各自权利人所有。本仓库不包含 Cobblemon 源码、Minecraft 文件、宝可梦模型、贴图或其他第三方游戏素材；运行时显示的模型和数据由用户另行安装的 Cobblemon 提供。

## AI 辅助开发说明

本项目的初始程序实现及文档由 OpenAI Codex 根据项目维护者提出的需求生成并整理。OpenAI 不是本项目的发布者、维护者或赞助者，也未对本项目作出认可、质量保证或法律审查。项目维护者应在发布前自行审查代码、素材、许可证和适用的平台规则。

## 第三方组件

本项目通过公开 API 与 Minecraft、Fabric 和 Cobblemon 兼容，但不向这些第三方项目授予任何权利，也不因 MIT 许可证改变它们各自的许可条款。详情见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可证

本仓库中由 CobbleRide 贡献者提供的原创代码和项目专用资源采用 [MIT License](LICENSE)。该许可证不覆盖第三方依赖、名称、商标、模型、贴图、角色或其他素材。

本软件按“现状”提供，不附带任何明示或暗示的保证。使用、修改或分发前请自行核实权利和当地规则；如需针对具体发布方式获得法律保证，请咨询具备相应资质的专业人士。
