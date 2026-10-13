<!-- 本文件不得由自动化脚本维护 -->
<!-- alpha-runtime 不直接与项目交互或调用,只负责基于配置文件部署产物,不负责运行测试,不关心游戏实例内部是否配置正确 -->

# [runtime.config.json](runtime.config.json)

```js
var config=require('./runtime.config.json');
// define '#'' as the root path of the repo(project)
```

## 基本字段

- `config.release_vsersion` 是项目模组的版本号
- `config.release_path` 是项目储存release的路径(禁止硬编码到配置文件或.env文件外)
- `config.jar_files_id` 是目录 `${config.release_path}/${config.release_vsersion}` 下构建的jar文件ID列表
- `config.jar_files_id` 来源于 `#/versions/versions.json` 和 `#/versions-fabric/versions/versions.json`
- `config.runtimes_path` 是测试运行时的游戏实例路径(禁止硬编码到配置文件或.env文件外)
- `config.runtimes_list` 是游戏实例路径下的游戏实例文件夹名称列表
- `config.runtimes_mods_dir` 是所有游戏实例文件夹下储存模组的文件的文件夹名称(自动补全)
- `config.mc_list` 是本模组支持的所有游戏版本(带模组加载器名称)列表
- `config.mc_map_runtime` 是一个映射 `Map<mc版本号,游戏实例文件夹名称>`
- `config.mc_map_id` 是一个映射 `Map<mc版本号,jar文件ID[]>`

> `release_path` 和 `runtimes_path` 两个字段建议写进 `.env` 文件,脚本能读取到,从而避免将开发机器的目录环境暴露到git历史中

# [copy_jars.py](copy_jars.py)

负责从目录 `${config.release_path}/${config.release_vsersion}` 下,复制文件 `VirtualRedstoneWire-${config.release_vsersion}-${config.mc_map_id[config.mc_list[i]][0]}.jar` 到目录 `${config.runtimes_path}/${config.mc_map_runtime[config.mc_list[i]]}/${config.runtimes_mods_dir}` 下。

简单来说,对于每一个mc版本(带模组加载器名称),要把能兼容它的jar模组文件(第一个)复制到对应的Runtime游戏实例模组文件夹下。


# [update_config.py](update_config.py)

负责更新 [runtime.config.json](runtime.config.json) 的可变列表。

运行该脚本后,必须由Agent或人工手动维护Map映射。
