# 开发者文档

## 运行与构建

| 命令 | 说明 |
|:---:|:---:|
| `build.bat` | 构建基线产物到 `build/libs/` |
| `gradlew build` | 构建基线产物到 `build/libs/` |
| `gradlew runClient` | 启动 Minecraft 客户端 |
| `gradlew runServer` | 启动 Minecraft 服务端 |
| `gradlew clean` | 清理构建产物 |

## 多版本适配

适配目标清单为 `versions/versions.json`, 生成脚本为 `versions/build_version.py`, 各目标相对基线的差异清单为该目标目录下的 `diff-baseline.json`

| 命令 | 说明 |
|:---:|:---:|
| `python versions/build_version.py list` | 列出全部适配目标 |
| `python versions/build_version.py prework <目标标识>` | 生成该目标的工作副本 |
| `python versions/build_version.py build <目标标识>` | 构建该目标 |
| `python versions/build_version.py copy` | 收集各目标的构建产物到 `release/` |

## 开发者文档清单

| 文档 | 用途 |
|:---:|:---:|
| [多版本兼容开发规范.md](./多版本兼容开发规范.md) | 新增受支持版本区间与平台版本区间时必须走的流程, 差异清单规范, 命名规范与硬约束 |
| [多版本兼容实测清单.md](./多版本兼容实测清单.md) | 每个适配目标上线前的逐项实测检查表与判定标准 |
| [Fabric侧移植差异.md](./Fabric侧移植差异.md) | Fabric 侧与 Forge 基线之间必须存在的差异, 每处的性质与理由, 以及需要实测确认的项 |
| [Fabric侧实测清单.md](./Fabric侧实测清单.md) | Fabric 侧功能等价的逐项验证清单, 重点覆盖两侧差异处 |

通信机制, 版本机制与待办排期类的版本文档仍放在 `docs/` 下
