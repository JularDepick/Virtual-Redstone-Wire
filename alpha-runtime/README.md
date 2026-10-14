<!-- 本文件不得由自动化脚本维护 -->
<!-- alpha-runtime 不直接与项目交互或调用,只负责基于配置文件部署产物,不负责运行测试,不关心游戏实例内部是否配置正确 -->

# alpha-runtime

把构建产物部署到测试运行时(游戏实例)。不参与构建, 不运行测试。

```text
alpha-runtime/
├── README.md            本文件
├── .env.example         .env 模板
├── runtime.config.json  部署配置
├── update_config.py     更新配置可变列表, 可推导 Map 映射
└── copy_jars.py         校验并复制产物到游戏实例
```

# [runtime.config.json](runtime.config.json)

```js
var config=require('./runtime.config.json');
// define '#'' as the root path of the repo(project)
```

## 字段

| 字段 | 值来源 | 说明 |
|:---:|:---:|:---|
| `release_vsersion` | 人工 | 模组版本号 |
| `release_path` | `.env` 优先 | release 目录 |
| `runtimes_path` | `.env` 优先 | 游戏实例目录 |
| `jar_files_id` | 脚本 | `${RELEASE_PATH}/${release_vsersion}` 下的 jar ID |
| `runtimes_list` | 脚本 | `runtimes_path` 下的实例目录名 |
| `runtimes_mods_dir` | 脚本 | 实例下存放模组的目录名, 固定 `mods` |
| `mc_list` | 脚本 | 由 `jar_files_id` 拆出的 MC 版本(带加载器名) |
| `mc_map_runtime` | 人工 | `Map<mc版本号,实例目录名>` |
| `mc_map_id` | 人工 | `Map<mc版本号,jar文件ID[]>` |

- 两个 path 优先从 `.env` 读取, 次选本文件, 都缺则拒绝
- `jar_files_id` 扫描 `${RELEASE_PATH}/${release_vsersion}` 下的 jar 文件名得到
- `mc_list` 把 `jar_files_id` 的 MC 区间拆成单个 MC 版本(带加载器名), 如 `mc-1.20_1.20.1-forge` 拆成 `mc-1.20-forge` 与 `mc-1.20.1-forge`
- 两个 Map 可用 `--withmap` 推导, 推导结果须经二次核验

# [update_config.py](update_config.py)

更新可变列表: `jar_files_id`, `runtimes_list`, `runtimes_mods_dir`, `mc_list`。

```bash
python update_config.py            # 只更新可变列表, 两个 Map 只校验
python update_config.py --withmap  # 额外推导两个 Map
```

## 拒绝条件

两个 path 取不到值, 清单缺失或解析失败, `id` 重复或两侧同名, release 目录或产物缺失,
`runtimes_path` 不存在, 两个 Map 与 `mc_list` / `jar_files_id` 不一致。

## `--withmap` 推导口径

- Levenshtein 编辑距离归一化到 `[0, 1]`
- `mc_map_runtime`: 按 `<MC版本>-<Forge|Fabric>` 拼期望目录名, 取相似度最高的实例
- `mc_map_id`: 取覆盖该版本且加载器一致的候选, 区间越短越优先
- 相似度低于 `0.50` 报为未通过, 需人工确认

# [copy_jars.py](copy_jars.py)

从 `${RELEASE_PATH}/${release_vsersion}` 复制 `mc_map_id[mc][0]` 对应的 jar 到
`${RUNTIMES_PATH}/${mc_map_runtime[mc]}/${runtimes_mods_dir}`。

```bash
python copy_jars.py        # 校验 -> 复制 -> 复制后校验(大小与 md5)
python copy_jars.py check  # 只校验, 不复制
```

## 权限探测

脚本不兜底权限管理, 只探测两个 path, 任一不通过即拒绝:

| path | 要求 |
|:---:|:---:|
| `${RELEASE_PATH}/${release_vsersion}` | 可读 |
| `${RUNTIMES_PATH}` | 可写 |

# 使用顺序

```bash
cp .env.example .env
python update_config.py --withmap
python copy_jars.py check
python copy_jars.py
```

<!--
待办: jar ID 的 MC 区间若写成 x 通配(如 mc-1.20.x-forge 或 mc-1.21_1.21.x-forge),
当前 update_config.py 不支持: split_id() 只接受确定版本号, 遇到 x 会报
"无法从 jar ID 解析 MC 版本与加载器" 并拒绝, 且不写回配置。
现有目标的 id 都是确定版本号(单值或 起始_终止 两个确定值), 所以拆解安全。
将来若要引入 x 形态, 需要先确定展开口径(例如 1.20.x 指 1.20 至 1.20.6, 需要一份
MC 版本序列表), 再改 split_id() 与 derive_mc_list()。现在不做。
-->

