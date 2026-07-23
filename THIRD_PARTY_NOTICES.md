# Third-Party Notices

CobbleRide 是独立开发的兼容扩展。它需要用户另行安装下列软件，并在编译时通过 Gradle 引用其公开 API：

| 项目 | 用途 | 上游地址 |
| --- | --- | --- |
| Minecraft | 游戏运行环境 | <https://www.minecraft.net/> |
| Fabric Loader | 模组加载器 | <https://github.com/FabricMC/fabric-loader> |
| Fabric API | Fabric 开发与运行 API | <https://github.com/FabricMC/fabric> |
| Fabric Language Kotlin | Kotlin 语言运行支持 | <https://github.com/FabricMC/fabric-language-kotlin> |
| Cobblemon | 骑乘、队伍、实体及模型的运行时实现 | <https://gitlab.com/cable-mc/cobblemon> |

这些组件不属于 CobbleRide，也不受 CobbleRide 的 MIT 许可证重新许可。它们的代码、二进制文件、名称、商标和素材分别受各自许可证、最终用户协议及使用条款约束。发布者和使用者应以各上游项目发布时提供的最新条款为准。

本仓库不应提交或分发：

- Minecraft 客户端、服务端、映射文件或其他专有游戏文件；
- Cobblemon 或其他模组的 JAR、复制源码、模型、贴图、音频或数据包素材；
- 含有玩家信息、服务器地址、访问令牌或整合包内容清单的日志及崩溃报告；
- 未取得分发许可的第三方图片、字体、音乐、角色素材或商标图形。

CobbleRide 的选择界面在运行时调用用户已安装的 Cobblemon 来显示生物模型。本仓库和 CobbleRide 发布 JAR 不包含这些模型或贴图。

如果未来复制、修改或直接打包任何第三方内容，必须在发布前单独确认其许可证兼容性，并补充准确的版权与来源声明。
