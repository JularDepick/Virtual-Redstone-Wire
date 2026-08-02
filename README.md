<div align="center">

# Virtual Redstone Wire (虚拟红石线缆)

[![Version](https://img.shields.io/badge/Version-0.3.0-red)](https://github.com/JularDepick/Virtual-Redstone-Wire/releases/tag/v0.3.0)
[![Copyright](https://img.shields.io/badge/Copyright-JularDepick-0066AA)](./COPYRIGHT)
[![License](https://img.shields.io/badge/License-MIT-yellow)](./LICENSE)

[简体中文]
| [[English]](./README_en-US.md)

</div>

为红石信号远程传递实现的Minecraft模组，不依赖实体红石方块，支持一对一、一对多、多对一连接。

---

# 模组使用教程

本模组高度依赖Forge，但无前置模组依赖，也不绑定任何其他模组或整合包。

当前模组仅支持 Minecraft 1.20.1版本,如需做其他版本兼容适配,请 [提交Issue](https://github.com/JularDepick/Virtual-Redstone-Wire/issues/new) 或 [进行贡献](#贡献指南) 。

| 物品 | 合成 | 操作 | 特性 |
|:---:|:---:|:---:|:---:|
| 线缆 | 红石 + 铁锭 | 右键输入端方块，再右键输出端方块，创建单向红石链路 | 信号强度沿链路传播；再次右键同一方块可取消选中；输入端可连接多个输出端，输出端可接收多个输入端 (取最大信号) |
| 线缆剪 | 铁锭 + 木棍 x2 | 右键输入端方块，切断其全部出链 | 仅作用于该方块作为输入端(起点)的链路 |
| 线缆放大镜 | 红石 + 玻璃板 + 木棍 | 手持时高亮显示全部链路（蓝框是输入端，黄框是输出端，红线是链路路径）；潜行右键方块，查看其链路信息；右键方块，快捷栏上方显示其红石信号强度 |  |

## 红石行为

- 虚拟线缆传输的是红石信号强度 (0-15)
- 输入端方块检测其所在位置的红石信号 (使用标准红石 API)
- 信号沿链路传递到输出端, 通过内存网络图查询返回信号强度（无方块占用）
- 输入端的红石变化会实时反映到输出端
- 支持一对多广播、多对一合并 (取最大值)
- 链路有方向: 信号从输出方块的指定面射出 (方向精确), 红石灯放在输出端方块本身上即可点亮
- 支持先建链后放源: 放置/移除信号源、拨动拉杆等操作会实时刷新链路信号
- 聊天栏操作反馈默认关闭, 可在配置文件中启用

# 技术栈

| 组件 | 版本 |
|:---:|:---:|
| Minecraft | 1.20.1 |
| Forge | 47.4.22 |
| JDK | 17 |
| Gradle | 8.14.3 |
| ForgeGradle | 6.x |
| 映射 | official |
| 模组 ID | virtual_redstone_wire |

# 源码目录结构

```
src/main/java/com/virtualredstonewire/
├── VirtualRedstoneWire.java          # 模组主入口
├── ClientSetup.java                  # 客户端初始化
├── ServerEventHandler.java           # 服务端事件(世界加载/保存/卸载/方块更新)
├── RedstoneDiagnostics.java          # 红石信号自动化诊断(调试用)
├── client/
│   ├── ClientCableCache.java         # 客户端链路缓存(版本化,只读)
│   ├── CableClientQueue.java         # 操作任务队列(单在途)
│   ├── CableClientEvents.java        # 连接/维度切换全量同步
│   ├── CableActionBarHud.java        # 快捷栏上方信号飘浮提示
│   ├── gui/CableInfoScreen.java      # 线缆信息 GUI 面板
│   ├── gui/CableInfoScreenOpener.java
│   └── render/CableRenderer.java     # 3D 渲染(方管梁+面亮点+连接线)
├── commands/
│   └── VRedTestCommand.java          # /vredtest 诊断命令
├── config/
│   ├── ServerConfig.java             # 服务端配置(距离/变更表/日志)
│   └── ClientConfig.java             # 客户端配置(聊天反馈)
├── data/
│   ├── CableLink.java                # 链路数据模型(纯渲染载体)
│   ├── CableNetwork.java             # 电缆网络图(结点索引+存储式信号查询)
│   ├── CableNode.java                # 电缆拓扑结点(toWho/fromWho 邻接表)
│   ├── CableNetworkManager.java      # 按维度管理 CableNetwork/计数器/变更表
│   └── CableNetworkSavedData.java    # NBT 持久化
├── item/
│   ├── VirtualCableItem.java         # 虚拟线缆(创建/删除链路,保留选态)
│   ├── CableCutterItem.java          # 线缆剪(只删起点链路)
│   └── CableMagnifierItem.java       # 线缆放大镜(下蹲查看面板/右键查看信号)
├── mixin/
│   └── MixinLevel.java               # 仅覆写 getSignal (DBW 存储式语义)
├── network/
│   ├── CableNetworkChannel.java      # 网络通道注册
│   ├── CableProtocol.java            # 协议常量/缩写键名/错误码
│   ├── CableOpPacket.java            # 操作请求(add/del/pull, JSON)
│   ├── CableMsgPacket.java           # 消息(d/f/r, JSON)
│   ├── CableOpPacketHandler.java     # 服务端请求处理与校验
│   ├── CableServerQueue.java         # 服务端 tick 队列与冲突处理
│   ├── CableChangeLog.java           # 变更日志(可选)
│   ├── CableInfoRequestPacket.java   # 放大镜信号查询请求
│   └── CableInfoResponsePacket.java  # 放大镜信号查询响应
└── registry/
    ├── ModItems.java                 # 物品注册
    └── ModBlocks.java                # 方块注册
```

```
src/main/java/com/virtualredstonewire/util/
└── TooltipLines.java                 # 物品提示多行工具
```

```
src/main/resources/
├── META-INF/mods.toml
├── mixins.virtual_redstone_wire.json    # Mixin 配置
├── pack.mcmeta
├── virtual_redstone_wire.png            # 模组 Logo
├── assets/virtual_redstone_wire/
│   ├── lang/{en_us,zh_cn}.json
│   ├── models/item/*.json
│   └── textures/item/{virtual_cable,cable_cutter,cable_magnifier}.png
└── data/virtual_redstone_wire/recipes/
    ├── virtual_cable.json
    ├── cable_cutter.json
    └── cable_magnifier.json
```

# 构建与运行

确保环境满足以下要求：

| 组件 | 要求 |
|:---:|:---:|
| JDK | 17 (推荐 Eclipse Temurin / Adoptium) |
| Gradle | 8.14.3 |
| Minecraft | 1.20.1 (Forge 47.4.22) |

验证环境：

```bash
java -version
# 输出应为 openjdk version "17.x.x"

gradlew --version
# 输出应包含 Gradle 8.14.3
```

构建：

```bash
gradlew build
# 输出在 build/libs/VirtualRedstoneWire-0.3.0.jar
```

# 变更日志

详见 [CHANGELOG.md](./CHANGELOG.md)

# 贡献指南

详见 [CONTRIBUTING.md](./CONTRIBUTING.md)

# 许可证

本项目基于 [MIT 许可证](./LICENSE) 开源。

# 版权信息

Copyright (c) 2026 JularDepick

详见 [COPYRIGHT文件](./COPYRIGHT)

# 开发参考

本模组开发部分参考了 Drive By Wire 模组的交互效果和实现逻辑。

# 需要协作

- 缺少多 Minecraft 版本兼容
- 缺少优秀的物品贴图素材方案

# 相关链接

- CurseForge模组链接：https://www.curseforge.com/minecraft/mc-mods/virtual-redstone-wire
- Modrinth模组链接：https://modrinth.com/mod/virtual-redstone-wire

> 娘希匹的Dickseep,做个模组愣是花了劳资20大洋,做了一坨屎出来,给劳资气了积薄都打闪电,最后狠狠鞭笞才做出可用版本