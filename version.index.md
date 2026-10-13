# 版本号索引

当前项目版本号: `1.1.0-alpha`

## 一 项目版本号 1.1.0-alpha 出现位置

### 1.1 构建脚本与项目文档

共 8 个文件, 8 行

| 文件 | 行号 | 该行是什么 |
|:---:|:---:|:---:|
| README.md | 5 | 文档版本徽章 `Version-1.1.0--alpha` 与 release 链接中的 tag `v1.1.0-alpha` |
| README_en-US.md | 5 | 文档版本徽章 `Version-1.1.0--alpha` 与 release 链接中的 tag `v1.1.0-alpha` |
| build.gradle | 7 | 构建脚本版本字段 `version = '1.1.0-alpha'`, Forge 侧根工程的版本号来源 |
| versions-fabric/build.gradle | 5 | Fabric 侧构建脚本版本字段 `version = '1.1.0-alpha'` |
| versions-fabric/versions/build_version.py | 38 | 模块文档字符串中的产物名示例 `VirtualRedstoneWire-v1.1.0-alpha-mc-1.20.1-fabric.jar` |
| versions-fabric/versions/build_version.py | 123 | 头部模组版本宏 `MOD_VERSION = '1.1.0-alpha'`, `copy` 据此只收集该版本的产物 |
| versions/build_version.py | 32 | 模块文档字符串中的产物名示例 `VirtualRedstoneWire-v1.1.0-alpha-mc-1.20.1-forge.jar` |
| versions/build_version.py | 117 | 头部模组版本宏 `MOD_VERSION = '1.1.0-alpha'`, `copy` 据此只收集该版本的产物 |

### 1.2 以占位符或表达式引用项目版本号的位置

这些行不含字面量 `1.1.0-alpha`, 但改项目版本号时必须一起核对

| 文件 | 行号 | 该行是什么 |
|:---:|:---:|:---:|
| build.gradle | 25, 27 | 资源处理任务声明版本输入并把 `${version}` 注入 mods.toml |
| build.gradle | 103 | 产物文件名模板, 取 `project.version` |
| build.gradle | 110 | 清单文件中的实现版本, 取 jar 归档版本 |
| src/main/resources/META-INF/mods.toml | 8 | 模组元信息版本字段, 占位符 `${version}` |
| versions-fabric/build.gradle | 34, 36 | 资源处理任务声明版本输入并把 `${version}` 注入 fabric.mod.json |
| versions-fabric/build.gradle | 55 | 产物文件名模板, 取 `project.version` |
| versions-fabric/src/main/resources/fabric.mod.json | 4 | 模组元信息版本字段, 占位符 `${version}` |
| build.bat | 16 | 构建完成后按 `VirtualRedstoneWire-v*.jar` 列出产物, 间接依赖产物命名模板 |
| versions/build_version.py | 31 | 文档字符串中的产物命名规则 `VirtualRedstoneWire-v<模组版本>-<id>.jar` |
| versions/build_version.py | 484, 485 | 生成工作副本时把产物文件名模板重写为含 `v${project.version}` 的形式 |
| versions-fabric/versions/build_version.py | 37 | 文档字符串中的产物命名规则 `VirtualRedstoneWire-v<模组版本>-<id>.jar` |
| versions-fabric/versions/build_version.py | 506, 507 | 生成工作副本时把产物文件名模板重写为含 `v${project.version}` 的形式 |

### 1.3 合计

| 分组 | 文件数 | 命中行数 |
|:---:|:---:|:---:|
| 1.1 构建脚本与项目文档 | 8 | 8 |
| 合计 | 8 | 8 |

## 二 维护口径

- 项目版本号变更后应同步更新本索引, 使文件路径与行号保持与代码一致
- 本索引只登记项目版本号(即模组版本号)出现的位置; Minecraft 版本, 模组加载器版本, Gradle 版本, Java 版本与资源包格式号等平台版本信息一律不登记
- 源码注释里不再出现项目版本号(守则第 10 章禁令), 变更史一律由 git 记录承载, 因此本索引不再有"变更史标注"区; 注释里引用版本文档的路径(如 `docs/v1.0.0-*.md`)属历史文件名, 不随项目版本号变更而改动, 也不登记
- 需要随项目版本号变更同步修改的是 1.1 节与 1.2 节
- 1.1 节里两侧 `build.gradle` 的 `version` 字段与两侧 `build_version.py` 头部的 `MOD_VERSION` 宏必须同时改: `copy` 子命令按 `MOD_VERSION` 筛选产物, 两者不一致时会收集不到任何产物
- 新增或移动涉及项目版本号的行之后, 应重新按文件类型逐类检索并更新本索引的行号
