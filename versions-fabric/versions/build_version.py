"""
build_version.py - 1.20.x 多版本适配产物生成脚本

子命令:
  list                列出全部适配目标
  prework <目标标识>   生成该目标的工作副本, 不构建
  build <目标标识>     生成该目标的工作副本并调用 Gradle 构建
  jdk-build <JDK主版本> 依次构建清单中 jdk.version 首项等于该主版本的全部目标
  copy                检查各个目标目录下的构建产物模组 jar, 只复制模组版本与 MOD_VERSION 一致的

位置:
  本脚本与清单同处 versions/ 下, 本工程根是其所在目录的上一级, 即 versions-fabric/。
本脚本内的相对路径一律相对本工程根解析, 不涉及外层工程; 这也是本工程可以独立构建的原因。

清单来源:
  versions/versions.json 是多版本列表的唯一权威来源, 由人工维护, 本脚本对它只读: 不写入,
  不重新格式化, 不回写缩进, 也不补字段(清单里没有的东西只能加在脚本侧或改由人工决定).
  本脚本不硬编码任何目标, 目标列表与每个目标的版本参数全部由该数组推导. 条目字段与派生规则:
    id                  目标标识, 同时是 versions/ 下的目录名与产物后缀
    tag                 baseline 或 compat, 仅用于标注
    mc.version          首项为编译对准的 MC 版本, 其余元素用于 fabric.mod.json 的依赖声明
    mc.pack-format      该目标的资源包格式号, 注入时写入 pack.mcmeta
    mod-loader.name     加载器名
    mod-loader.version  首项为编译对准的加载器版本, 其余元素用于 fabric.mod.json 的依赖声明
    gradle.version      首项为该目标使用的 Gradle 版本
    jdk.version         首项为该目标要求的 java 主版本, 构建前与当前 java 比对
  fabric.mod.json 的依赖声明由数组推出: minecraft 取 mc.version 全部元素的并集, 加载器取
  mod-loader.version 全部元素的并集, 并把 x 写法转成 Fabric 谓词(如 47.x -> >=47 <48, 多元素用 || 相连);
  java 取 jdk.version 首项, 写成 >=<主版本>.
  pack.mcmeta 只改基线文件里的 pack_format 值, 取值来自该目标 mc 块的 pack-format 字段,
  其余内容保持基线原样.
  build.gradle 的 java 级别注入两处: toolchain 的 JavaLanguageVersion 与编译任务的
  options.release. 两者必须一致, 因为 options.release 同时决定字节码级别与 Gradle 为
  compileClasspath 选择的 JVM 目标兼容性, 只改 toolchain 会让 1.21.2 起原版依赖链里
  仅支持 java 21 的构件在依赖解析阶段被拒.

  产物名为 VirtualRedstoneWire-v<模组版本>-<id>.jar, 其中模组版本取自 build.gradle, id 即
  目标标识, 例如 VirtualRedstoneWire-v1.1.0-alpha-mc-1.20.1-fabric.jar. 本脚本另在头部以
  MOD_VERSION 宏声明同一版本号, 供 copy 只收集该版本的产物; 改模组版本号时两者必须一起改.

工作副本:
  先把仓库根的 src/ 完整复制到 versions/<目标标识>/work/, 再按该目标目录下的
  diff-baseline.json 改造工作副本, 再注入该目标的版本参数(编译对准的 MC 与加载器版本, java
  级别, Gradle wrapper 版本, fabric.mod.json 依赖, pack.mcmeta, 产物文件名), 然后可选地在该副本内
  调用 Gradle 构建. build 不归集产物: 产物留在该目标自己的工作副本里 work/build/libs/, 由
  copy 统一收集到 release/. 探针的工作副本落在 temp/probes/ 下, 产物留在探针自己的目录, 不
  进入 release/.

  共用内容(基线源码, 构建脚本模板, Gradle wrapper)只保留在工作目录根目录; 各目标独有的内容
  放在 versions/<目标标识>/ 下:
    diff-baseline.json  该目标相对基线的逐文件差异清单(纳入版本控制, 人工维护)
    patch/<层>/         该目标独有的补丁内容(纳入版本控制)
    work/               生成的工作副本(注入后的构建脚本, gradle.properties, 源码副本,
                        build/ 与 .gradle/ 状态), 不纳入版本控制
  工作副本刻意放在 work/ 子目录: 准备副本时会整体清空该目录, 若与 patch/ 同处一层会把目标
  自带的补丁一并删除.

差异清单 diff-baseline.json:
  与 versions/versions.json 一样由人工维护, 本脚本对它只读: 不写入, 不重新格式化, 不回写
  缩进, 也不补字段. 结构:
    {
        "id": "mc-1.20.1-fabric",
        "diff": {
            "del": ["path to work/ file"],
            "add": [{"from": "path to new file", "to": "path to work/ sub dir"}]
        }
    }
  规则:
    1. 只允许操作文件, 不操作目录.
    2. 文件内所有路径都相对该 json 自身所在目录(即 versions/<目标标识>/)解析, 不相对仓库根.
    3. 先执行完全部 del, 再执行 add; add 数组内部按顺序生效, 后写覆盖先写.
    4. del 的每项是指向 work 内一个文件的路径. 差异是逐文件记录"基线被改成了什么", 因此凡是
       会被 add 替换掉的基线文件都必须先在 del 里显式删除, 不允许用 add 直接覆盖基线; 只删除
       而不以新文件替代的基线文件也列在这里.
    5. add 的每项: from 是源文件路径, to 是 work 下的目标子目录, 最终文件名取 from 的文件名.
    6. 每次 add/del 都校验来源与目标: from 不存在, add 的目标目录不存在, del 的目标文件不
       存在, add 的目标文件在执行完 del 之后仍然存在(说明该替换漏写 del), 都直接报错中止,
       并在报错信息里指出是哪一条 diff 项; 同一目标文件被多条 add 写入时列出来并提示最后
       一个生效.
  探针借用真实目标的差异清单: 清单仍按 json 自身所在目录解析, 其中 work/ 下的路径改落到探针
  自己的工作副本, 因此探针的产物与真实目标互不干扰.

用法:
  python versions/build_version.py list
  python versions/build_version.py prework mc-1.20.1-fabric
  python versions/build_version.py build mc-1.20.1-fabric
  python versions/build_version.py build mc-1.20.1-fabric --override-mc 1.20 --override-loader 0.16.14
  python versions/build_version.py jdk-build 17
  python versions/build_version.py copy

注意:
  一次只处理一个目标: 不同目标要求不同主版本的 java, 而本脚本不切换 java, 所以没有跨 java 版本
  的批量构建; jdk-build 只批量构建同一 java 主版本的目标, 且仍是按清单顺序逐个依次构建(一次只
  构建一个), 某个目标失败时继续构建其余目标, 最后汇总. list 与 copy 不涉及编译, 不受此限. 构建
  前取该目标 jdk.version 首项, 与当前 java 主版本比对, 不符则直接拒绝; 本脚本不切换 java, 不写
  入 org.gradle.java.home, 也不查找其他 JDK 路径.
  prework 与 build 开始时内置差异清单校验: 校验各目标 diff-baseline.json 的结构, id 与所在
  目录名是否一致, 以及每条 add 的来源文件是否存在, 校验不通过则中止. jdk-build 不做事前校验,
  某个目标的差异清单问题按该目标构建失败处理, 不中断其余目标.
  构建时 Gradle 会写入工作目录之外的 Gradle 用户缓存, 这是 Loom 的固有行为, 本脚本
  不规避.
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO = SCRIPT_DIR.parent
# versions.json 由人工维护, 本脚本只读: 任何写入/格式化/回写缩进的逻辑都不得加入
MANIFEST = SCRIPT_DIR / 'versions.json'
VERSIONS_ROOT = SCRIPT_DIR
# 探针是临时性工作(不属于真实目标), 其工作副本落在 temp/ 下, 不污染 versions/ 的命名空间
PROBE_ROOT = REPO / 'temp' / 'probes'
# 交付物归集目录, 已在 .gitignore 中忽略
RELEASE_ROOT = REPO / 'release'

# 当前模组版本号: 必须与本工程根 build.gradle 的 version 字段一致, copy 只收集带该版本号的产物
MOD_VERSION = '1.1.0-alpha'

# 从仓库根复制到工作副本的内容: src/ 是功能性的源码目录, 整体复制; 其余是构建脚本模板与
# Gradle wrapper
COPY_FILES = ['build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat']
COPY_DIRS = ['src', 'gradle']

# 工作副本目录名: 差异清单里所有以它开头的路径都指向工作副本自身
WORK_DIR_NAME = 'work'
# 差异清单文件名, 由人工维护, 本脚本只读
DIFF_FILE_NAME = 'diff-baseline.json'

# 确定版本号: 纯数字或点分数字, 允许携带 + 限定后缀(如 Fabric API 的 0.92.12+1.20.1),
# 不含 x 占位与下划线区间
CONCRETE_VERSION = re.compile(r'^\d+(\.\d+)*(\+[\w.\-]+)?$')


def load_manifest():
    """读取唯一权威清单 versions/versions.json

    只做读取与结构校验, 不增删改任何字段; 目标列表完全由该数组的顺序与内容决定.
    """
    with MANIFEST.open(encoding='utf-8') as fh:
        data = json.load(fh)
    if not isinstance(data, list) or not data:
        raise SystemExit('%s 必须是非空的条目数组' % MANIFEST.relative_to(REPO).as_posix())
    seen = set()
    for entry in data:
        for key in ('id', 'tag', 'mc', 'mod-loader', 'gradle', 'jdk'):
            if key not in entry:
                raise SystemExit('%s 的条目缺少字段 %s' % (MANIFEST.relative_to(REPO).as_posix(), key))
        if entry['id'] in seen:
            raise SystemExit('%s 存在重复的 id: %s' % (MANIFEST.relative_to(REPO).as_posix(), entry['id']))
        seen.add(entry['id'])
        for path, what in ((('mc', 'version'), 'mc.version'),
                           (('mod-loader', 'version'), 'mod-loader.version'),
                           (('gradle', 'version'), 'gradle.version'),
                           (('jdk', 'version'), 'jdk.version')):
            value = entry[path[0]].get(path[1])
            if not isinstance(value, list) or not value or not all(isinstance(v, str) for v in value):
                raise SystemExit('%s 的 %s 必须是非空字符串数组' % (entry['id'], what))
    return data


def target_id(entry):
    return entry['id']


def version_list(entry, section):
    return entry[section]['version']


def loader_name(entry):
    name = entry['mod-loader'].get('name')
    if not isinstance(name, str) or not name:
        raise SystemExit('%s 的 mod-loader.name 缺失' % target_id(entry))
    return name


def concrete(value, what, entry):
    """取数组首项并要求它是确定版本号(不含 x 占位与下划线区间)"""
    if not CONCRETE_VERSION.match(value):
        raise SystemExit('%s 的 %s 首项必须是确定版本号(不含 x 与下划线区间), 实际为 %s'
                         % (target_id(entry), what, value))
    return value


def build_mc(entry):
    return concrete(version_list(entry, 'mc')[0], 'mc.version', entry)


def build_loader(entry):
    return concrete(version_list(entry, 'mod-loader')[0], 'mod-loader.version', entry)


def build_gradle(entry):
    return concrete(version_list(entry, 'gradle')[0], 'gradle.version', entry)


def build_jdk(entry):
    """该目标要求的 java 主版本, 门禁比对的就是它"""
    value = concrete(version_list(entry, 'jdk')[0], 'jdk.version', entry)
    return int(value.split('.')[0])


def version_tuple(text, spec):
    if not CONCRETE_VERSION.match(text):
        raise SystemExit('版本写法无法解析: %s (出现在 %s)' % (spec, text))
    return tuple(int(part) for part in text.split('.'))


def bump_last(value):
    return value[:-1] + (value[-1] + 1,)


def parse_spec(spec):
    """把清单里的一个版本写法转成区间 (下界, 上界, 上界是否闭合)

    支持: 单一版本 1.20.1; 定级占位 47.x; 闭区间 1.20_1.20.1; 开区间 1.20_1.21.x
    (表示 [1.20,1.22) ).
    """
    text = spec.strip()
    if not text:
        raise SystemExit('版本写法为空')
    if '_' in text:
        start, _, end = text.partition('_')
        if not start or not end:
            raise SystemExit('区间写法两端都不能匿名: %s' % spec)
        low = version_tuple(start, spec)
        if end.endswith('.x'):
            high_text = end[:-2]
            if not high_text:
                raise SystemExit('区间上界不能整段匿名: %s' % spec)
            high = bump_last(version_tuple(high_text, spec))
            high_inclusive = False
        else:
            high = version_tuple(end, spec)
            high_inclusive = True
        if high < low or (high == low and not high_inclusive):
            raise SystemExit('区间上下界不合法: %s' % spec)
        return (low, high, high_inclusive)
    if text.endswith('.x'):
        low_text = text[:-2]
        if not low_text:
            raise SystemExit('占位写法不能整段匿名: %s' % spec)
        low = version_tuple(low_text, spec)
        return (low, bump_last(low), False)
    value = version_tuple(text, spec)
    return (value, value, True)


def merge_intervals(intervals):
    """把多个区间并为不重叠的少量区间

    相邻或重叠即合并, 被包含的区间自然被吸收, 因此 47.4.23 与 47.x 的并集写成 [47,48).
    """
    merged = []
    for low, high, high_inclusive in sorted(intervals):
        if not merged:
            merged.append([low, high, high_inclusive])
            continue
        current = merged[-1]
        if low <= current[1]:
            if (high, high_inclusive) > (current[1], current[2]):
                current[1], current[2] = high, high_inclusive
        else:
            merged.append([low, high, high_inclusive])
    return [tuple(item) for item in merged]


def render_interval(low, high, high_inclusive):
    low_text = '.'.join(str(part) for part in low)
    if low == high and high_inclusive:
        return '[%s]' % low_text
    high_text = '.'.join(str(part) for part in high)
    return '[%s,%s%s' % (low_text, high_text, ']' if high_inclusive else ')')


def compat_range(specs):
    """把清单数组转成 Maven 区间声明: 全部元素取并集

    死代码登记: 本函数是 Forge 侧脚本的遗留, Fabric 侧声明兼容范围用的是
    fabric_range, 已无调用点; 按守则第 10 章保留不删除.
    """
    return ','.join(render_interval(*item) for item in merge_intervals([parse_spec(spec) for spec in specs]))


def rel(path):
    """提示用的相对路径, 落在仓库之外时退回绝对路径"""
    try:
        return path.resolve().relative_to(REPO).as_posix()
    except ValueError:
        return str(path)


def diff_path(owner_id):
    """该目标目录下的差异清单"""
    return VERSIONS_ROOT / owner_id / DIFF_FILE_NAME


def load_diff(owner_id):
    """读取 diff-baseline.json, 返回 (清单所在目录, del 数组, add 数组)

    清单由人工维护, 本函数只读取与校验结构, 不修改文件内容. 清单不存在时视为该目标相对基线
    没有任何差异.
    """
    path = diff_path(owner_id)
    base = path.parent
    if not path.is_file():
        return base, [], []
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
    except ValueError as exc:
        raise SystemExit('%s 不是合法 JSON: %s' % (rel(path), exc))
    if not isinstance(data, dict):
        raise SystemExit('%s 的顶层必须是对象' % rel(path))
    declared = data.get('id')
    if declared is not None and declared != owner_id:
        raise SystemExit('%s 的 id 为 %s, 与所在目录名 %s 不一致' % (rel(path), declared, owner_id))
    diff = data.get('diff')
    if diff is None:
        return base, [], []
    if not isinstance(diff, dict):
        raise SystemExit('%s 的 diff 必须是对象' % rel(path))
    del_items = diff.get('del', [])
    add_items = diff.get('add', [])
    if not isinstance(del_items, list) or not all(isinstance(v, str) for v in del_items):
        raise SystemExit('%s 的 diff.del 必须是字符串数组' % rel(path))
    if not isinstance(add_items, list):
        raise SystemExit('%s 的 diff.add 必须是数组' % rel(path))
    for index, item in enumerate(add_items):
        if (not isinstance(item, dict) or not isinstance(item.get('from'), str)
                or not isinstance(item.get('to'), str)):
            raise SystemExit('%s 的 diff.add 第 %d 项必须是含字符串 from 与 to 的对象'
                             % (rel(path), index + 1))
    return base, del_items, add_items


def resolve_in_work(base, work, value, what):
    """把清单里的一个路径按 json 自身所在目录解析, 并把 work/ 前缀改指到实际工作副本

    清单内的路径一律相对 json 自身所在目录, 不相对仓库根. 其中的 work/ 只在真实目标目录下才
    等于实际工作副本: 探针借用真实目标的清单, 其工作副本落在 temp/probes/, 因此这里把解析结
    果落在 <json 目录>/work/ 之下的部分改挂到实际工作副本上.
    """
    raw = Path(value)
    if raw.is_absolute():
        raise SystemExit('%s 的路径必须是相对路径: %s' % (what, value))
    dest = (base / raw).resolve()
    logical_work = (base / WORK_DIR_NAME).resolve()
    if dest == logical_work or logical_work in dest.parents:
        dest = work / dest.relative_to(logical_work)
    return dest


def require_inside_work(path, work, what, value):
    """差异清单只能改动工作副本内的文件"""
    if path != work and work not in path.parents:
        raise SystemExit('%s 必须落在工作副本内: %s (解析为 %s)' % (what, value, rel(path)))


def resolve_del(base, work, index, value):
    """解析一条 del 项, 返回要删除的文件路径"""
    what = 'diff 项 del[%d]' % index
    dest = resolve_in_work(base, work, value, what + ' 的目标')
    require_inside_work(dest, work, what + ' 的目标文件', value)
    if not dest.exists():
        raise SystemExit('%s 的目标文件不存在: %s (按 %s 解析)' % (what, value, rel(dest)))
    if not dest.is_file():
        raise SystemExit('%s 的目标不是文件(本机制只允许操作文件): %s' % (what, value))
    return dest


def resolve_add(base, work, index, item):
    """解析一条 add 项, 返回 (来源文件, 目标目录, 落盘路径)

    落盘路径在 del 全部执行完之后才允许存在: 它若还在, 说明该替换没有写 del, 属于清单与基线
    不同步, 直接报错中止而不是静默覆盖.
    """
    what = 'diff 项 add[%d]' % index
    src = resolve_in_work(base, work, item['from'], what + ' 的 from')
    if not src.exists():
        raise SystemExit('%s 的来源文件不存在: %s (按 %s 解析)' % (what, item['from'], rel(src)))
    if not src.is_file():
        raise SystemExit('%s 的来源不是文件(本机制只允许操作文件): %s' % (what, item['from']))
    target = resolve_in_work(base, work, item['to'], what + ' 的 to')
    if not target.exists():
        raise SystemExit('%s 的目标目录不存在: %s (按 %s 解析)' % (what, item['to'], rel(target)))
    if not target.is_dir():
        raise SystemExit('%s 的目标不是目录(本机制只允许操作文件): %s' % (what, item['to']))
    require_inside_work(target, work, what + ' 的目标目录', item['to'])
    dest = target / src.name
    if dest.exists():
        raise SystemExit('%s 的目标文件在 del 之后仍然存在: %s, 该替换漏写了 del'
                         % (what, rel(dest)))
    return src, target, dest


def planned_dest(base, item):
    """按逻辑工作副本算出 add 项的落盘路径, 不校验存在性(供清单预检统计使用)"""
    base = base.resolve()
    src = (base / Path(item['from'])).resolve()
    target = resolve_in_work(base, base / WORK_DIR_NAME, item['to'], 'diff 项的 to')
    return target / src.name


def duplicate_adds(dests):
    """同一目标文件被多条 add 写入的情况, 按顺序记录, 最后一条生效"""
    written = {}
    for index, dest in dests:
        written.setdefault(dest, []).append(index)
    return {dest: indexes for dest, indexes in written.items() if len(indexes) > 1}


def apply_diff(base, work, del_items, add_items, log):
    """按差异清单改造工作副本: 先执行完全部 del, 再按数组顺序执行 add

    del 全部落地之后才校验 add 的落点, 因此"用 add 直接覆盖基线文件"会被判为漏写 del 并中止.
    """
    for index, value in enumerate(del_items):
        dest = resolve_del(base, work, index, value)
        dest.unlink()
        log.append('del[%d] 删除 %s' % (index, rel(dest)))
    resolved = []
    for index, item in enumerate(add_items):
        src, _target, dest = resolve_add(base, work, index, item)
        resolved.append((index, src, dest))
    for index, src, dest in resolved:
        shutil.copy2(src, dest)
        log.append('add[%d] %s -> %s' % (index, rel(src), rel(dest)))
    for dest, indexes in sorted(duplicate_adds([(index, dest) for index, _src, dest in resolved]).items()):
        print('  提示: 目标文件 %s 被 %s 多条写入, 最后一项 add[%d] 生效'
              % (rel(dest), ', '.join('add[%d]' % i for i in indexes), indexes[-1]))
    return len(del_items), len(add_items)


def diff_owner(entry):
    """差异清单的归属目标标识

    探针会改写 id 并借用一个真实目标的差异清单, 因此清单归属由 diff_owner 单独记录, 缺省才回
    退到 id.
    """
    return entry.get('diff_owner') or target_id(entry)


def find_target(manifest, wanted):
    for entry in manifest:
        if target_id(entry).lower() == wanted.lower():
            return entry
    raise SystemExit('未知目标: %s (可用: %s)'
                     % (wanted, ', '.join(target_id(entry) for entry in manifest)))


def copy_baseline(work, log):
    """把仓库根的基线完整复制到工作副本

    src/ 是功能性的源码目录, 整体复制; 其余是构建脚本模板与 Gradle wrapper.
    """
    for name in COPY_FILES:
        src = REPO / name
        if not src.is_file():
            raise SystemExit('基线缺少文件: %s' % name)
        shutil.copy2(src, work / name)
    for name in COPY_DIRS:
        src = REPO / name
        if not src.is_dir():
            raise SystemExit('基线缺少目录: %s' % name)
        shutil.copytree(src, work / name)
    log.append('复制基线到 %s (src/ 整体 + 构建脚本与 Gradle wrapper)' % rel(work))


def sub_once(text, pattern, replacement, what):
    new, count = re.subn(pattern, replacement, text)
    if count != 1:
        raise SystemExit('注入失败: %s 在 build.gradle 中匹配到 %d 次(期望 1 次), 基线可能已变动' % (what, count))
    return new


def inject_build_gradle(work, entry, log):
    path = work / 'build.gradle'
    text = path.read_text(encoding='utf-8')
    mc_version = build_mc(entry)
    loader_version = build_loader(entry)
    api_version = build_api(entry)
    text = sub_once(text, r"def mcVersion = '[^']*'", "def mcVersion = '%s'" % mc_version,
                    'mcVersion(编译对准的 MC 版本)')
    text = sub_once(text, r"def mcRange = '[^']*'", "def mcRange = '%s'" % fabric_range(entry, 'mc'),
                    'mcRange(minecraft 兼容谓词)')
    text = sub_once(text, r"def loaderVersion = '[^']*'", "def loaderVersion = '%s'" % loader_version,
                    'loaderVersion(编译对准的加载器版本)')
    text = sub_once(text, r"def loaderRange = '[^']*'",
                    "def loaderRange = '%s'" % fabric_range(entry, 'mod-loader'),
                    'loaderRange(加载器兼容谓词)')
    text = sub_once(text, r"def fabricApiVersion = '[^']*'", "def fabricApiVersion = '%s'" % api_version,
                    'fabricApiVersion(Fabric API 版本)')
    text = sub_once(text, r"JavaLanguageVersion\.of\(\d+\)", "JavaLanguageVersion.of(%d)" % build_jdk(entry),
                    'java toolchain(该目标的 java 主版本)')
    # options.release 决定编译产物的字节码级别, 也决定 Gradle 为 compileClasspath 选择的
    # JVM 目标兼容性; 它必须与该目标的 java 主版本一致, 否则 1.21.2 起原版依赖链里的
    # jtracy 等仅支持 21 的构件会在依赖解析阶段被拒(报"looking for a library compatible
    # with JVM runtime version 17"), 编译根本走不到源码阶段
    text = sub_once(text, r"options\.release = \d+", "options.release = %d" % build_jdk(entry),
                    'options.release(该目标的 java 主版本)')
    text = sub_once(text, r"archiveFileName = \"[^\"]*\"",
                    'archiveFileName = "${archivesBaseName}-v${project.version}-%s.jar"' % target_id(entry),
                    'archiveFileName(产物名后缀为目标标识)')
    path.write_text(text, encoding='utf-8')
    log.append('注入 build.gradle: 编译对准 %s / %s-%s / Fabric API %s / Java %d (toolchain 与 release) '
               '/ 产物名后缀 %s'
               % (mc_version, loader_name(entry), loader_version, api_version, build_jdk(entry),
                  target_id(entry)))


def build_api(entry):
    """该目标编译对准的 Fabric API 版本, 取 mod-loader.api 的首项"""
    api = (entry.get('mod-loader') or {}).get('api')
    if not api:
        raise SystemExit('%s 的 mod-loader 块缺少 api 字段, 无法确定 Fabric API 版本' % target_id(entry))
    return concrete(api[0], 'mod-loader.api', entry)


def fabric_predicate(spec):
    """把清单里的一个版本写法转成 Fabric 的版本谓词

    精确版本直接用; 固定一部分用 .x, 转成 [本级, 下一级) 的上下界; 闭区间用下划线相连,
    转成两端都闭的上下界; 开区间写作"起点_终点线.x", 转成起点闭终点线开.
    """
    if '_' not in spec:
        if spec.endswith('.x'):
            parts = spec.split('.')
            return '>=%s <%s' % ('.'.join(parts[:-1]), increment_last('.'.join(parts[:-1])))
        return spec
    low, high = spec.split('_', 1)
    if high.endswith('.x'):
        line = high[:-2]
        return '>=%s <%s' % (low, increment_last(line))
    return '>=%s <=%s' % (low, high)


def increment_last(version):
    """把点分版本号的最后一级加一, 用于由本级推出下一级的上界"""
    parts = version.split('.')
    parts[-1] = str(int(parts[-1]) + 1)
    return '.'.join(parts)


def fabric_range(entry, key):
    """把清单数组转成 Fabric 的依赖谓词: 全部元素取并集, 用 || 相连"""
    return ' || '.join(fabric_predicate(item) for item in version_list(entry, key))


def write_fabric_mod_json(work, entry, log):
    """把 fabric.mod.json 的依赖声明写成该目标数组推出的 Fabric 谓词

    minecraft 取 mc.version 全部元素的并集, 加载器取 mod-loader.version 全部元素的并集,
    因此"用某个版本编译, 声明覆盖一段范围"与基线同一文件兼容.
    java 取该目标 jdk.version 首项: 基线写死的 >=17 对 1.21.x 与 1.20.5/1.20.6 这类
    要求 java 21 的目标不正确, 会让加载器在 java 17 上先按声明放行再抛类版本错误,
    因此这里随该目标要求的 java 主版本注入下界.
    """
    path = work / 'src' / 'main' / 'resources' / 'fabric.mod.json'
    if not path.exists():
        raise SystemExit('基线缺少 fabric.mod.json: %s' % rel(path))
    data = json.loads(path.read_text(encoding='utf-8'))
    depends = data.setdefault('depends', {})
    minecraft_range = fabric_range(entry, 'mc')
    loader_range = fabric_range(entry, 'mod-loader')
    java_range = '>=%d' % build_jdk(entry)
    depends['minecraft'] = minecraft_range
    depends['fabricloader'] = loader_range
    depends['java'] = java_range
    path.write_text(json.dumps(data, ensure_ascii=False, indent=4) + '\n', encoding='utf-8')
    log.append('写入 fabric.mod.json 依赖: minecraft %s / fabricloader %s / java %s'
               % (minecraft_range, loader_range, java_range))


def pack_format(entry):
    """该目标的资源包格式号, 取自清单中 mc 块的 pack-format 字段"""
    value = (entry.get('mc') or {}).get('pack-format')
    if value is None:
        raise SystemExit('%s 的 mc 块缺少 pack-format 字段, 无法确定资源包格式号' % target_id(entry))
    return value


def write_pack_mcmeta(work, entry, log):
    """只改基线 pack.mcmeta 里的 pack_format 值, 其余内容保持基线原样"""
    path = work / 'src' / 'main' / 'resources' / 'pack.mcmeta'
    if not path.exists():
        raise SystemExit('基线缺少 pack.mcmeta: %s' % rel(path))
    value = pack_format(entry)
    text = path.read_text(encoding='utf-8')
    text, count = re.subn(r'"pack_format"\s*:\s*\d+', '"pack_format": %d' % value, text)
    if count != 1:
        raise SystemExit('注入失败: pack.mcmeta 的 pack_format 匹配到 %d 次(期望 1 次)' % count)
    path.write_text(text, encoding='utf-8')
    log.append('注入 pack.mcmeta: pack_format=%d' % value)


def inject_wrapper(work, entry, log):
    """把 Gradle wrapper 指向该目标清单里声明的 Gradle 版本"""
    gradle_version = build_gradle(entry)
    path = work / 'gradle' / 'wrapper' / 'gradle-wrapper.properties'
    text = path.read_text(encoding='utf-8')
    text, count = re.subn(r'gradle-[\w.]+-bin\.zip', 'gradle-%s-bin.zip' % gradle_version, text)
    if count != 1:
        raise SystemExit('注入失败: gradle-wrapper.properties 的发行包名匹配到 %d 次(期望 1 次)' % count)
    path.write_text(text, encoding='utf-8')
    log.append('注入 Gradle wrapper: %s' % gradle_version)


def current_java_major():
    """读取 PATH 上默认 java 的主版本号, 用于构建前的门禁比对"""
    try:
        proc = subprocess.run(['java', '-version'], capture_output=True, text=True, shell=False)
    except OSError:
        return None
    text = (proc.stderr or '') + (proc.stdout or '')
    match = re.search(r'version "(\d+)', text)
    return int(match.group(1)) if match else None


def require_java_version(entry, log):
    """检查当前 java 版本是否满足该目标要求, 不满足则拒绝构建

    构建必须使用该目标 jdk.version 首项要求的 JDK. 脚本不做版本切换也不做兜底: 既不写
    org.gradle.java.home, 也不查找其他 JDK 路径, 只检查当前 java 并在不符合时拒绝, 由使用者
    自行切换版本.
    """
    required = build_jdk(entry)
    current = current_java_major()
    if current is None:
        raise SystemExit('无法读取当前 java 版本, 请确认 java 已在 PATH 中')
    if current != required:
        raise SystemExit('当前 java 主版本为 %d, 目标 %s 要求 java %d; 请先把当前 java 切到 %d 再构建'
                         % (current, target_id(entry), required, required))
    log.append('当前 java 主版本 %d 满足目标要求(java %d)' % (current, required))


def prepare(entry, log, work_root=None):
    # 工作副本下沉到 <目标标识>/work/ 下, 与目标自带的 <目标标识>/patch/ 并列:
    # 准备副本时会整体清空工作副本目录, 若与其并列的 patch/ 同处一层就会被一并删除
    work = (work_root or VERSIONS_ROOT) / target_id(entry) / WORK_DIR_NAME
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    copy_baseline(work, log)
    owner = diff_owner(entry)
    base, del_items, add_items = load_diff(owner)
    if diff_path(owner).is_file():
        log.append('差异清单 %s: del %d 条, add %d 条'
                   % (rel(diff_path(owner)), len(del_items), len(add_items)))
    else:
        log.append('差异清单 %s 不存在, 视为无差异' % rel(diff_path(owner)))
    del_count, add_count = apply_diff(base, work, del_items, add_items, log)
    log.append('差异清单校验通过: del %d 条已删除, add %d 条已写入' % (del_count, add_count))
    inject_build_gradle(work, entry, log)
    write_fabric_mod_json(work, entry, log)
    write_pack_mcmeta(work, entry, log)
    inject_wrapper(work, entry, log)
    return work


def run_build(work, gradle_cmd, log):
    cmd = [gradle_cmd, 'build']
    log.append('调用构建: %s (工作目录 %s)' % (' '.join(cmd), work.relative_to(REPO).as_posix()))
    proc = subprocess.run(cmd, cwd=str(work), shell=False)
    return proc.returncode


def find_artifacts(work):
    """工作副本内的产物模组 jar, 排除 sources/javadoc/dev 等附属 jar"""
    libs = work / 'build' / 'libs'
    if not libs.is_dir():
        return []
    return sorted(p for p in libs.glob('*.jar')
                  if not p.name.endswith(('-sources.jar', '-javadoc.jar', '-dev.jar')))


def check_diffs(manifest, verbose=False):
    """校验各目标的差异清单, 并列出各目标的 add/del 条数

    校验项: diff-baseline.json 的结构, id 与所在目录名是否一致, 每条 add 的来源文件是否存在,
    以及同一目标内被多条 add 写入的同一文件(后者覆盖前者, 属有意叠加时应当看得见). del 与
    add 的落点都在工作副本内, 只有准备工作副本时才能校验, 故不在此处校验.
    """
    problems = []
    for entry in manifest:
        owner = diff_owner(entry)
        try:
            base, del_items, add_items = load_diff(owner)
        except SystemExit as exc:
            problems.append(str(exc))
            continue
        if verbose:
            print('  %-22s del %d 条, add %d 条' % (owner, len(del_items), len(add_items)))
        for index, item in enumerate(add_items):
            src = (base / Path(item['from'])).resolve()
            if not src.exists():
                problems.append('%s: diff 项 add[%d] 的来源文件不存在: %s' % (owner, index, item['from']))
            elif not src.is_file():
                problems.append('%s: diff 项 add[%d] 的来源不是文件: %s' % (owner, index, item['from']))
        duplicates = duplicate_adds([(index, planned_dest(base, item)) for index, item in enumerate(add_items)])
        for dest, indexes in sorted(duplicates.items()):
            print('  %s: 同一目标文件被多条 add 写入: %s <- %s'
                  % (owner, dest.name, ', '.join('add[%d]' % i for i in indexes)))
    for item in problems:
        print('问题: %s' % item)
    if not problems:
        print('差异清单校验通过: %d 个目标' % len(manifest))
    return 1 if problems else 0


def require_diffs_ok(manifest):
    """prework 与 build 的前置校验: 差异清单有问题就直接报错退出"""
    if check_diffs(manifest) != 0:
        raise SystemExit('差异清单校验未通过, 已中止')


def command_list(manifest):
    failed = 0
    for entry in manifest:
        try:
            _base, del_items, add_items = load_diff(diff_owner(entry))
            counts = 'diff del %d / add %d' % (len(del_items), len(add_items))
        except SystemExit as exc:
            counts = 'diff 读取失败: %s' % exc
            failed += 1
        print('%-22s [%-8s] 编译对准 %s / %s-%s / Java %d / Gradle %s'
              % (target_id(entry), entry['tag'], build_mc(entry), loader_name(entry),
                 build_loader(entry), build_jdk(entry), build_gradle(entry)))
        print('%-22s  声明 minecraft %s / 加载器 %s / %s / 产物后缀 %s'
              % ('', fabric_range(entry, 'mc'),
                 fabric_range(entry, 'mod-loader'), counts, target_id(entry)))
    return 1 if failed else 0


def command_prework(args, manifest):
    entry = find_target(manifest, args.target)
    require_diffs_ok(manifest)
    print('=== %s [%s] ===' % (target_id(entry), entry['tag']))
    log = []
    work = prepare(entry, log)
    for line in log:
        print('  ' + line)
    print('  工作副本已生成: %s' % rel(work))
    return 0


def probe_entry(entry, args):
    """按 --override-mc / --override-loader / --override-api 构造探针条目

    探针沿用被借用目标的差异清单与 java 要求, 只把编译对准的 MC 版本, 加载器版本与
    Fabric API 版本换掉; 其工作副本落在 temp/probes/ 下, 产物留在探针自己的目录,
    不进入 release/.

    与 Forge 侧的差别: Forge 把平台与 API 打包在同一构件里, 只覆盖 MC 与加载器即可;
    Fabric 的 API 是按 MC 版本分发的独立构件, 必须单独覆盖, 否则探针会拿另一条
    MC 版本线的 API 去编译, 结论就不可信.
    """
    mc_version = args.override_mc or build_mc(entry)
    loader_version = args.override_loader or build_loader(entry)
    api_version = args.override_api or build_api(entry)
    for value, what in ((mc_version, '--override-mc'), (loader_version, '--override-loader'),
                        (api_version, '--override-api')):
        if not CONCRETE_VERSION.match(value):
            raise SystemExit('%s 只接受确定版本号(不含 x 与下划线区间), 实际为 %s' % (what, value))
    probe = json.loads(json.dumps(entry))
    probe['mc']['version'] = [mc_version]
    probe['mod-loader']['version'] = [loader_version]
    probe['mod-loader']['api'] = [api_version]
    probe['id'] = 'mc-%s-%s-%s' % (mc_version, loader_name(entry), loader_version)
    probe['tag'] = 'probe'
    probe['diff_owner'] = target_id(entry)
    return probe


def build_one(entry, gradle_cmd, work_root=None):
    """构建单个目标, 打印全过程日志, 返回 Gradle 的退出码

    build 与 jdk-build 共用这一条构建路径(java 门禁 -> 准备工作副本 -> 调用 Gradle), 避免两处
    的构建行为日后分叉. java 门禁不通过或准备工作副本失败时抛出 SystemExit, 由调用方决定是终止
    整个命令还是记为该目标的失败.
    """
    # java 门禁先于准备工作副本: 版本不符时直接拒绝, 不触碰工作副本
    log = []
    require_java_version(entry, log)
    print('  ' + log[-1])

    log = []
    work = prepare(entry, log, work_root)
    for line in log:
        print('  ' + line)

    log = []
    code = run_build(work, gradle_cmd, log)
    print('  ' + log[-1])
    if code != 0:
        print('  构建失败(退出码 %d).' % code)
        return code
    for jar in find_artifacts(work):
        print('  构建产物: %s' % jar.relative_to(REPO).as_posix())
    if work_root is None:
        print('  产物留在工作副本, 用 copy 子命令收集到 release/')
    return 0


def command_build(args, manifest):
    entry = find_target(manifest, args.target)
    require_diffs_ok(manifest)

    is_probe = bool(args.override_mc or args.override_loader or args.override_api)
    if is_probe:
        borrowed = target_id(entry)
        entry = probe_entry(entry, args)
        print('探针模式: 借用 %s 的差异清单与 java 要求, 编译对准 %s / %s-%s / API %s (工作副本落在 temp/probes/, 不属于真实目标)'
              % (borrowed, build_mc(entry), loader_name(entry), build_loader(entry), build_api(entry)))
    print('=== %s [%s] ===' % (target_id(entry), entry['tag']))
    return build_one(entry, args.gradle_cmd, PROBE_ROOT if is_probe else None)


def targets_for_jdk(manifest, major):
    """按清单顺序挑出 jdk.version 首项等于该主版本号的全部目标"""
    return [entry for entry in manifest if build_jdk(entry) == major]


def command_jdk_build(args, manifest):
    """依次构建清单中 java 主版本匹配的全部目标

    一次只构建一个目标: 不同目标要求不同主版本的 java, 而本脚本不切换 java, 所以这里按清单顺序
    逐个串行构建. java 门禁在批量开始前只检查一次, 避免逐个目标重复报同一件事, 且门禁先于准备
    工作副本, 不符时不触碰任何工作副本. 单个目标失败不中断其余目标, 最后打印汇总表, 存在失败
    目标时返回非 0. 不归集产物, 收集仍由 copy 负责.
    """
    major = args.jdk
    selected = targets_for_jdk(manifest, major)
    if not selected:
        print('清单中没有 jdk.version 首项为 java %d 的目标, 无需构建' % major)
        return 0
    print('java %d 的目标共 %d 个, 按清单顺序依次构建: %s'
          % (major, len(selected), ', '.join(target_id(entry) for entry in selected)))

    # java 门禁与 build 同一口径(只检查不切换)
    log = []
    require_java_version(selected[0], log)
    print('  ' + log[-1])

    results = []
    for entry in selected:
        print('=== %s [%s] ===' % (target_id(entry), entry['tag']))
        try:
            code = build_one(entry, args.gradle_cmd)
        except SystemExit as exc:
            code, reason = 1, str(exc)
        except Exception as exc:
            # 单个目标的意外错误不应中断其余目标
            code, reason = 1, '%s: %s' % (type(exc).__name__, exc)
        else:
            reason = '构建失败(退出码 %d)' % code if code != 0 else ''
        results.append((target_id(entry), code == 0, reason))

    failed = [item for item in results if not item[1]]
    print('=== java %d 构建汇总 ===' % major)
    for identifier, ok, reason in results:
        print('  %-22s %s' % (identifier, '成功' if ok else '失败: %s' % reason))
    print('  共 %d 个目标: 成功 %d, 失败 %d' % (len(results), len(results) - len(failed), len(failed)))
    return 1 if failed else 0


def command_copy(manifest):
    """检查各个目标目录下的构建产物模组 jar, 只把模组版本与 MOD_VERSION 一致的那些复制到 release/

    只扫描 versions/ 下的真实目标; 探针的工作副本落在 temp/probes/, 其产物不是交付物.
    产物名里必须出现 "-v<MOD_VERSION>-": 早先版本构建留下的残留产物一律跳过并列出, 避免把
    旧版本的 jar 混进发布产物.
    """
    version_token = '-v%s-' % MOD_VERSION
    copied = []
    skipped = []
    stale = []
    RELEASE_ROOT.mkdir(parents=True, exist_ok=True)
    for entry in manifest:
        identifier = target_id(entry)
        jars = find_artifacts(VERSIONS_ROOT / identifier / WORK_DIR_NAME)
        if not jars:
            skipped.append(identifier)
            print('  %s: 未找到构建产物, 跳过' % identifier)
            continue
        for jar in jars:
            if version_token not in jar.name:
                stale.append(jar.name)
                print('  %s: 产物名不含 %s, 跳过: %s' % (identifier, version_token, jar.name))
                continue
            dest = RELEASE_ROOT / jar.name
            shutil.copy2(jar, dest)
            copied.append(dest)
            print('  %s -> %s' % (jar.relative_to(REPO).as_posix(), dest.relative_to(REPO).as_posix()))
    if not copied:
        raise SystemExit('未找到模组版本为 %s 的构建产物, 请先按该版本构建至少一个目标'
                         % MOD_VERSION)
    print('已复制 %d 个产物到 %s%s'
          % (len(copied), RELEASE_ROOT.relative_to(REPO).as_posix() + '/',
             ', 无产物的目标: %s' % ', '.join(skipped) if skipped else ''))
    if stale:
        print('  跳过 %d 个模组版本不符的残留产物: %s' % (len(stale), ', '.join(sorted(set(stale)))))
    return 0


def main():
    # 重定向到文件时 stdout 默认是块缓冲, 会让"准备工作副本/开始构建"等进度长时间不落盘;
    # 改为行缓冲, 便于构建期间用日志观察进度
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except (AttributeError, ValueError):
        pass
    parser = argparse.ArgumentParser(description='1.20.x 多版本适配产物生成')
    # 子命令缺失时自行报错: 保留 argparse 对未知选项的原始报错(旧开关形式会在此被拒)
    subparsers = parser.add_subparsers(dest='command', metavar='<子命令>')
    subparsers.add_parser('list', help='列出全部适配目标')
    prework = subparsers.add_parser('prework', help='生成指定目标的工作副本, 不构建')
    prework.add_argument('target', help='目标标识, 例如 mc-1.20.1-fabric')
    build = subparsers.add_parser('build', help='生成指定目标的工作副本并构建')
    build.add_argument('target', help='目标标识, 例如 mc-1.20.1-fabric')
    build.add_argument('--gradle-cmd', default='gradlew.bat', help='Gradle 命令, 默认 gradlew.bat')
    build.add_argument('--override-mc', help='探针: 临时把编译对准的 MC 版本改为该值')
    build.add_argument('--override-loader', help='探针: 临时把编译对准的加载器版本改为该值')
    build.add_argument('--override-api', help='探针: 临时把编译对准的 Fabric API 版本改为该值')
    jdk_build = subparsers.add_parser('jdk-build', help='依次构建清单中 java 主版本匹配的全部目标')
    jdk_build.add_argument('jdk', type=int, help='JDK 主版本号, 例如 17')
    jdk_build.add_argument('--gradle-cmd', default='gradlew.bat', help='Gradle 命令, 默认 gradlew.bat')
    subparsers.add_parser('copy', help='收集各目标目录下模组版本与 MOD_VERSION 一致的产物模组 jar 到 release/')
    args = parser.parse_args()
    if not args.command:
        parser.error('需要指定子命令: list / prework / build / jdk-build / copy')

    manifest = load_manifest()

    if args.command == 'list':
        return command_list(manifest)
    if args.command == 'prework':
        return command_prework(args, manifest)
    if args.command == 'build':
        return command_build(args, manifest)
    if args.command == 'jdk-build':
        return command_jdk_build(args, manifest)
    if args.command == 'copy':
        return command_copy(manifest)
    raise SystemExit('未知子命令: %s' % args.command)


if __name__ == '__main__':
    sys.exit(main())
