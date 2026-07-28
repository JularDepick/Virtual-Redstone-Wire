<div align="center">

# Virtual Redstone Wire (虚拟红石缆线)

[![Version](https://img.shields.io/badge/Version-1.0.0-red)](https://github.com/JularDepick/Virtual-Redstone-Wire/releases/tag/v1.0.0)
[![Copyright](https://img.shields.io/badge/Copyright-JularDepick-0066AA)](./COPYRIGHT)
[![License](https://img.shields.io/badge/License-MIT-yellow)](./LICENSE)

[简体中文]
| [[English]](./README_en-US.md)

</div>

为红石信号远程传递实现的Minecraft模组，不依赖实体红石方块，支持一对一、一对多、多对一连接。

---

# 模组使用教程

本模组高度依赖Forge，但无前置模组依赖，也不绑定任何其他模组或整合包。

## 线缆

合成: 红石 + 铁锭

用法: 不消耗, 可重复使用
1. 手持线缆, 右键点击一个方块, 将其设为输入端 (聊天栏提示已选中)
2. 再右键点击另一个方块, 将其设为输出端, 链路即建立
3. 在输出端会自动生成一个不可见的虚拟红石源方块, 其朝向输出面输出红石信号
4. 如需取消选中, 再次右键同一方块即可
5. 同一输入端可连接多个输出端 (一对多), 同一输出端也可接收多个输入端 (多对一, 取最大信号强度)

## 线缆剪

合成: 铁锭 + 木棍 x2

用法: 不消耗, 可重复使用
- 右键点击任意与线缆关联的方块, 断开其所有输入/输出链路
- 对输入端或输出端使用均可

## 线缆放大镜

合成: 红石 + 玻璃板 + 木棍

用法: 不消耗, 可重复使用
- 手持时: 所有链路的输入端和输出端以高亮边框显示, 链路以红色线条可视化 (蓝色边框=输入端, 黄色边框=输出端, 红色线=链路路径)
- 右键方块时: 打开独立信息面板, 显示该方块关联的所有输入/输出链路列表 (点击面板外或X按钮关闭)

## 红石行为

- 虚拟线缆传输的是红石信号强度 (0-15)
- 输入端方块检测其所在位置的红石信号 (使用标准红石 API)
- 信号沿链路传递到输出端, 由虚拟红石源方块向指定面输出 (虚拟源放在接收面外侧, 向方块内部发射)
- 输入端的红石变化会实时反映到输出端
- 支持一对多广播、多对一合并 (取最大值)
- 聊天栏操作反馈默认关闭, 可在配置文件中启用

# 技术栈

| 组件 | 版本 / 说明 |
|------|-------------|
| Minecraft | 1.20.1 |
| Forge | 47.4.22 |
| JDK | 17 |
| Gradle | 8.14.3 |
| 模组 ID | virtual_redstone_wire |

# 源码目录结构

```
src/main/java/com/virtualredstonewire/
├── VirtualRedstoneWire.java        # 模组主入口
├── ClientSetup.java                # 客户端初始化
├── ServerEventHandler.java         # 世界加载/保存/红石事件
├── registry/
│   ├── ModItems.java               # 物品注册
│   └── ModBlocks.java              # 方块注册
├── item/
│   ├── VirtualCableItem.java       # 线缆物品
│   ├── CableCutterItem.java        # 线缆剪物品
│   └── CableMagnifierItem.java     # 线缆放大镜物品
├── block/
│   └── VirtualRedstoneSourceBlock.java  # 虚拟红石源方块
├── data/
│   ├── CableLink.java              # 链路数据模型
│   ├── CableNetwork.java           # 网络图 (邻接表)
│   ├── CableNetworkManager.java    # 内存管理器
│   └── CableNetworkSavedData.java  # 持久化
├── redstone/
│   └── RedstoneCalculator.java     # 红石信号计算
├── network/
│   ├── CableNetworkChannel.java    # 网络通道
│   ├── CableSyncPacket.java        # 全量同步
│   ├── CableUpdatePacket.java      # 增量更新
│   └── CableQueryPacket.java       # 查询响应
├── config/
│   └── ModConfig.java              # 配置
└── client/
    ├── ClientCableCache.java       # 客户端缓存
    ├── render/CableRenderer.java   # 3D 渲染
    └── gui/CableInfoScreen.java    # 信息界面
```

```
src/main/resources/
├── META-INF/mods.toml
├── pack.mcmeta
├── virtual_redstone_wire.png            # 模组 Logo
├── assets/virtual_redstone_wire/
│   ├── lang/{en_us,zh_cn}.json
│   ├── models/item/*.json
│   ├── models/block/virtual_source.json
│   ├── blockstates/virtual_source.json
│   └── textures/item/{virtual_cable,cable_cutter,cable_magnifier}.png
└── data/virtual_redstone_wire/recipes/
    ├── virtual_cable.json
    ├── cable_cutter.json
    └── cable_magnifier.json
```

# 构建与运行

确保环境满足以下要求：

| 组件 | 要求 |
|------|------|
| JDK | 17 (推荐 Eclipse Temurin / Adoptium) |
| Gradle | 8.14.3 |
| Minecraft | 1.20.1 (Forge 47.4.22) |

验证环境：

```bash
java -version
# 输出应为 openjdk version "17.x.x"

gradle --version
# 输出应包含 Gradle 8.14.3
```

构建：

```bash
gradle build
# 输出在 build/libs/VirtualRedstoneWire-1.0.0.jar
```

# 许可证

本项目基于 [MIT 许可证](./LICENSE) 开源。

# 版权信息

Copyright (c) 2026 JularDepick

详见 [COPYRIGHT文件](./COPYRIGHT)
