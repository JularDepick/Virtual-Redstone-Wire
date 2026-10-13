<!-- 本文件不得由自动化脚本维护 -->
<!-- alpha-runtime 不直接与项目交互或调用,只负责基于配置文件部署产物,不负责运行测试,不关心游戏实例内部是否配置正确 -->

# alpha-runtime

本目录负责把构建产物部署到测试运行时(游戏实例),不参与构建,不运行测试。

## 目录结构

```text
alpha-runtime/
├── README.md                 本文件
├── .env.example              .env 模板
├── runtime.config.json       部署配置
├── update_config.py          更新配置可变列表, 可推导 Map 映射
└── copy_jars.py              校验与复制产物到游戏实例
```

本机使用时复制为 `.env` 并填真实路径, 脚本只读 `.env`; 配置文件 `runtime.config.json` 中 `release_path` 与 `runtimes_path` 一律为空字符串, 字段名保留。


# [runtime.config.json](runtime.config.json)

```js
var config=require('./runtime.config.json');
// define '#'' as the root path of the repo(project)
```

## 字段口径

| 字段 | 值来源 | 说明 |
|:---|:---|:---|
| `release_vsersion` | 人工 | 项目模组的版本号, 须与构建产物文件名中的版本号一致 |
| `release_path` | `.env` 的 `RELEASE_PATH` | 项目储存 release 的路径, 禁止硬编码到 `.env` 之外 |
| `jar_files_id` | 脚本自动 | 目录 `${RELEASE_PATH}/${release_vsersion}` 下构建的 jar 文件 ID 列表 |
| `runtimes_path` | `.env` 的 `RUNTIMES_PATH` | 测试运行时的游戏实例路径, 禁止硬编码到 `.env` 之外 |
| `runtimes_list` | 脚本自动 | `runtimes_path` 下的游戏实例文件夹名称列表 |
| `runtimes_mods_dir` | 脚本自动 | 所有游戏实例下储存模组文件的文件夹名称, 固定 `mods` |
| `mc_list` | 人工 | 本模组支持的所有游戏版本(带加载器名)列表 |
| `mc_map_runtime` | 人工或脚本推导 | `Map<mc版本号,游戏实例文件夹名称>` |
| `mc_map_id` | 人工或脚本推导 | `Map<mc版本号,jar文件ID[]>` |

`jar_files_id` 来源于 `#/versions/versions.json` 与 `#/versions-fabric/versions/versions.json`,
`runtimes_list` 来源于对 `runtimes_path` 的目录枚举, `runtimes_mods_dir` 固定为 `mods`;
这三个由 [update_config.py](update_config.py) 自动写回。`mc_list` 与两个 Map 属人工维护字段,
脚本只做校验, 仅在带 `--withmap` 时才推导。


# [update_config.py](update_config.py)

负责更新 [runtime.config.json](runtime.config.json) 的可变列表: `jar_files_id`, `runtimes_list`,
`runtimes_mods_dir`。`mc_list` 与两个 Map 映射不在无参流程内改动。

```bash
# 只更新可变列表, 两个 Map 只校验不推导
python update_config.py

# 更新可变列表 + 用编辑距离相似度自动推导两个 Map
python update_config.py --withmap
```

## 校验与拒绝

存在以下任一项即拒绝执行并返回退出码 1, 同时打印明细:

- `.env` 缺失, 或 `RELEASE_PATH` / `RUNTIMES_PATH` 任一为空
- 任一份 `versions.json` 缺失, 解析失败, 目标 `id` 重复, 两侧 `id` 同名
- `release_vsersion` 对应的 release 目录不存在, 或某个目标缺少构建产物(Forge 与 Fabric 两侧分别统计)
- `runtimes_path` 不存在
- `mc_list` 中的条目在两个 Map 中缺失, Map 出现不属于 `mc_list` 的键, Map 的值不属于 `jar_files_id`

## `--withmap` 推导口径

- 用 Levenshtein 编辑距离算字符串相似度, 归一化到 `[0, 1]`, 1 为完全相同
- `mc_map_runtime`: 按 `<MC版本>-<Forge|Fabric>` 拼出期望目录名, 与 `runtimes_list` 逐项算相似度取最高
- `mc_map_id`: 用两份 `versions.json` 的目标区间匹配, 取区间覆盖该 MC 版本且加载器名一致的候选,
  区间越短(覆盖越精确)优先, 同区间再按 id 相似度取高
- 相似度低于阈值 `0.50` 的条目标为待人工确认, 明细逐条打印

> 推导结果必须由 Agent 或人工二次核验配置文件后再使用, 脚本不做最终判定。


# [copy_jars.py](copy_jars.py)

负责从目录 `${RELEASE_PATH}/${release_vsersion}` 下, 复制文件
`VirtualRedstoneWire-${release_vsersion}-${mc_map_id[mc][0]}.jar` 到目录
`${RUNTIMES_PATH}/${mc_map_runtime[mc]}/${runtimes_mods_dir}` 下。

简单来说, 对于每一个 mc 版本(带加载器名称), 要把能兼容它的 jar 模组文件(第一个)复制到对应的
Runtime 游戏实例模组文件夹下。

```bash
# 完整流程: 两侧校验 -> 复制 -> 复制后校验(存在性, 大小, md5)
python copy_jars.py

# 只做两侧校验, 不复制任何文件
python copy_jars.py check
```

复制后校验逐项比对目标与源文件的大小与 md5, 不一致即返回退出码 1。

## 权限探测

脚本不兜底权限管理, 只对两个 path 做探测, 任一不通过即拒绝任务并返回退出码 1:

| path | 探测内容 |
|:---|:---|
| `${RELEASE_PATH}/${release_vsersion}` | 需要可读(读 jar) |
| `${RUNTIMES_PATH}` | 需要可写(落 jar) |

# 使用顺序

```bash
# 1. 准备本机环境变量
cp .env.example .env

# 2. 更新配置(需要推导 Map 时加 --withmap, 推导结果需人工二次核验)
python update_config.py

# 3. 校验两侧(不复制)
python copy_jars.py check

# 4. 复制产物并校验
python copy_jars.py
```
