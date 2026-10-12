#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
compare_sides.py - 核对 Forge 侧与 Fabric 侧产物是否功能等价

判定口径: 同一模组的两套实现, 两侧源码同名同构, 平台差异只允许出现在
docs/developer/Fabric侧移植差异.md 登记的位置. 因此把两侧产物中
com/virtualredstonewire/ 下的 .class 按简单类名归并, 任何一方独有的类
都必须在脚本内的允许差异清单里登记过, 否则判为失败.

只用标准库, 不联网, 不调用 Gradle.

用法:
    python scripts/compare_sides.py
    python scripts/compare_sides.py <forge jar> <fabric jar>

退出码:
    0  全部差异均已在允许清单内
    1  存在未登记的差异
    2  用法错误或产物文件缺失
"""

from __future__ import annotations

import argparse
import fnmatch
import os
import re
import sys
import zipfile

# 模组自身的类所在包前缀
MOD_PACKAGE_PREFIX = "com/virtualredstonewire/"

# 基线产物默认路径, 相对仓库根解析
FORGE_JAR_DIR = "build/libs"
FORGE_JAR_PATTERN = "VirtualRedstoneWire-v*-forge-mc1.20.1.jar"
FABRIC_JAR_DIR = "versions-fabric/build/libs"
FABRIC_JAR_PATTERN = "VirtualRedstoneWire-v*-mc-1.20.1-fabric.jar"

# 允许差异清单: 只在 Forge 侧存在的类
# 键为简单类名, 值为一句理由
ALLOWED_FORGE_ONLY = {
    "ModItems$TabRegistration": "事件总线订阅类, Fabric 侧由 registerItemGroups() 取代",
}

# 允许差异清单: 只在 Fabric 侧存在的类
ALLOWED_FABRIC_ONLY = {
    "ConfigSpec": "自建配置设施, 取代 Forge 侧的 ForgeConfigSpec",
    "ConfigSpec$Entry": "ConfigSpec 的内部条目基类",
    "ConfigSpec$BoolEntry": "ConfigSpec 的内部布尔条目",
    "ConfigSpec$IntEntry": "ConfigSpec 的内部整数条目",
    "ConfigSpec$StringEntry": "ConfigSpec 的内部字符串条目",
    "ConfigSpec$EnumEntry": "ConfigSpec 的内部枚举条目",
    "CablePayload": "保持投递方法泛型形状的接口",
    "ProtocolHandshakePacket": "协议版本握手, 替代 Forge 侧通道在连接期的版本校验",
    "VRedConfigCommand": "客户端命令 /vredconfig, 替代 Forge 侧的 Mods 菜单配置入口",
}

# 输出换行宽度, 仅用于第三组的紧凑列表
WRAP_WIDTH = 96


def force_utf8_output():
    """把标准输出与标准错误切到 UTF-8, 避免 Windows 控制台中文乱码"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


def find_repo_root(start_dir):
    """从起始目录向上找到仓库根, 不写死绝对路径"""
    current = os.path.abspath(start_dir)
    while True:
        has_gradle = os.path.isfile(os.path.join(current, "settings.gradle"))
        has_fabric = os.path.isdir(os.path.join(current, "versions-fabric"))
        if has_gradle and has_fabric:
            return current
        parent = os.path.dirname(current)
        if parent == current:
            return None
        current = parent


def version_key(file_name):
    """从文件名里的 -v<版本号> 取出可比较的元组, 便于在多个产物中选最新的一版"""
    match = re.search(r"-v(\d+(?:\.\d+)*)", file_name)
    if match is None:
        return ()
    return tuple(int(part) for part in match.group(1).split("."))


def resolve_default_jar(repo_root, rel_dir, pattern):
    """按文件名模式在默认目录里找出基线产物, 命中多个时取版本号最高的一个"""
    if repo_root is None:
        return None, "未能定位仓库根, 无法解析默认产物路径"
    directory = os.path.join(repo_root, *rel_dir.split("/"))
    if not os.path.isdir(directory):
        return None, "目录不存在: %s" % directory
    candidates = [name for name in os.listdir(directory) if fnmatch.fnmatchcase(name, pattern)]
    if not candidates:
        return None, "目录 %s 下没有匹配 %s 的产物" % (directory, pattern)
    candidates.sort(key=version_key, reverse=True)
    return os.path.join(directory, candidates[0]), None


def extract_mod_classes(jar_path):
    """
    从 jar 中取出模组自身的类

    返回 dict: 简单类名 -> 该名字在 jar 内的全部条目路径(排序后)
    简单类名指去掉包路径与 .class 后缀的名字, 内部类保留 $ 分隔, 如 ModItems$TabRegistration
    """
    grouped = {}
    with zipfile.ZipFile(jar_path) as archive:
        for entry in archive.namelist():
            if not entry.startswith(MOD_PACKAGE_PREFIX):
                continue
            if not entry.endswith(".class"):
                continue
            relative = entry[len(MOD_PACKAGE_PREFIX):-len(".class")]
            simple_name = relative.rsplit("/", 1)[-1]
            grouped.setdefault(simple_name, []).append(entry)
    for paths in grouped.values():
        paths.sort()
    return grouped


def format_paths(paths):
    """把同一简单类名对应的多个条目路径拼成一行"""
    return ", ".join(paths)


def wrap_names(names):
    """把类名列表按宽度紧凑换行, 缩进对齐"""
    lines = []
    current = ""
    indent = "    "
    for name in names:
        piece = name if not current else ", " + name
        if current and len(current) + len(piece) > WRAP_WIDTH:
            lines.append(indent + current)
            current = name
        else:
            current += piece
    if current:
        lines.append(indent + current)
    return lines


def print_diff_group(title, names, allowed_map, path_map, missing_marker):
    """
    打印一组单向差异

    返回该组中未登记差异的个数
    """
    print("")
    print("%s (%d 个)" % (title, len(names)))
    if not names:
        print("    (无)")
        return 0
    unregistered = 0
    for name in names:
        reason = allowed_map.get(name)
        if reason is None:
            unregistered += 1
            print("  - %s" % name)
            print("      标记: 未登记")
        else:
            print("  - %s" % name)
            print("      允许: %s" % reason)
        print("      %s: %s" % (missing_marker, format_paths(path_map[name])))
    return unregistered


def main(argv=None):
    parser = argparse.ArgumentParser(
        prog="compare_sides.py",
        description="核对 Forge 侧与 Fabric 侧产物是否功能等价, 未登记的差异判为失败",
        add_help=True,
    )
    parser.add_argument("forge_jar", nargs="?", help="Forge 侧产物 jar 路径, 省略时取仓库根下的基线产物")
    parser.add_argument("fabric_jar", nargs="?", help="Fabric 侧产物 jar 路径, 省略时取 versions-fabric 下的基线产物")
    args = parser.parse_args(argv)

    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = find_repo_root(script_dir)
    if repo_root is None:
        repo_root = os.path.dirname(script_dir)

    forge_path = args.forge_jar
    fabric_path = args.fabric_jar
    if forge_path is None:
        forge_path, error = resolve_default_jar(repo_root, FORGE_JAR_DIR, FORGE_JAR_PATTERN)
        if error is not None:
            print("错误: 未指定 Forge 侧产物, 且默认产物不可用")
            print("      原因: %s" % error)
            print("      仓库根: %s" % repo_root)
            print("      可改为显式传入: python scripts/compare_sides.py <forge jar> <fabric jar>")
            return 2
    if fabric_path is None:
        fabric_path, error = resolve_default_jar(repo_root, FABRIC_JAR_DIR, FABRIC_JAR_PATTERN)
        if error is not None:
            print("错误: 未指定 Fabric 侧产物, 且默认产物不可用")
            print("      原因: %s" % error)
            print("      仓库根: %s" % repo_root)
            print("      可改为显式传入: python scripts/compare_sides.py <forge jar> <fabric jar>")
            return 2

    forge_path = os.path.abspath(forge_path)
    fabric_path = os.path.abspath(fabric_path)

    print("两侧产物功能等价核对")
    print("仓库根: %s" % repo_root)
    print("Forge  侧: %s" % forge_path)
    print("Fabric 侧: %s" % fabric_path)

    missing = False
    for label, path in (("Forge", forge_path), ("Fabric", fabric_path)):
        if not os.path.isfile(path):
            print("")
            print("错误: %s 侧产物不存在: %s" % (label, path))
            missing = True
    if missing:
        print("")
        print("判定: 失败 (产物缺失, 未做任何比较)")
        print("提示: 请先构建对应一侧, 本脚本不会自行调用 Gradle 构建")
        return 2

    try:
        forge_classes = extract_mod_classes(forge_path)
    except zipfile.BadZipFile:
        print("")
        print("错误: Forge 侧产物不是可读的 jar 文件: %s" % forge_path)
        return 2
    except OSError as exc:
        print("")
        print("错误: 读取 Forge 侧产物失败: %s" % exc)
        return 2

    try:
        fabric_classes = extract_mod_classes(fabric_path)
    except zipfile.BadZipFile:
        print("")
        print("错误: Fabric 侧产物不是可读的 jar 文件: %s" % fabric_path)
        return 2
    except OSError as exc:
        print("")
        print("错误: 读取 Fabric 侧产物失败: %s" % exc)
        return 2

    if not forge_classes:
        print("")
        print("错误: Forge 侧产物中没有任何 %s 下的类, 疑似取错了文件" % MOD_PACKAGE_PREFIX)
        return 2
    if not fabric_classes:
        print("")
        print("错误: Fabric 侧产物中没有任何 %s 下的类, 疑似取错了文件" % MOD_PACKAGE_PREFIX)
        return 2

    print("Forge  侧模组类: %d 个" % len(forge_classes))
    print("Fabric 侧模组类: %d 个" % len(fabric_classes))

    forge_only = sorted(set(forge_classes) - set(fabric_classes))
    fabric_only = sorted(set(fabric_classes) - set(forge_classes))
    both = sorted(set(forge_classes) & set(fabric_classes))

    unregistered = 0
    unregistered += print_diff_group("[一] 只在 Forge 侧存在", forge_only,
                                     ALLOWED_FORGE_ONLY, forge_classes, "Forge 路径")
    unregistered += print_diff_group("[二] 只在 Fabric 侧存在", fabric_only,
                                     ALLOWED_FABRIC_ONLY, fabric_classes, "Fabric 路径")

    print("")
    print("[三] 两侧都有 (%d 个)" % len(both))
    if both:
        for line in wrap_names(both):
            print(line)
    else:
        print("    (无)")

    # 同名但落点不同属于结构性异常, 不计入失败, 仅作提示
    path_conflicts = []
    for name in both:
        if forge_classes[name] != fabric_classes[name]:
            path_conflicts.append(name)
    if path_conflicts:
        print("")
        print("提示: 以下简单类名两侧都有但条目路径不同, 请人工确认")
        for name in path_conflicts:
            print("  - %s" % name)
            print("      Forge 路径: %s" % format_paths(forge_classes[name]))
            print("      Fabric 路径: %s" % format_paths(fabric_classes[name]))

    print("")
    print("差异统计: 只在 Forge 侧 %d 个, 只在 Fabric 侧 %d 个, 其中未登记 %d 个"
          % (len(forge_only), len(fabric_only), unregistered))
    print("允许清单: Forge 独有 %d 条, Fabric 独有 %d 条, 见脚本内 ALLOWED_FORGE_ONLY 与 ALLOWED_FABRIC_ONLY"
          % (len(ALLOWED_FORGE_ONLY), len(ALLOWED_FABRIC_ONLY)))

    print("")
    if unregistered == 0:
        print("判定: 通过 (全部差异均已在允许清单内)")
        return 0
    print("判定: 失败 (存在 %d 个未登记的差异)" % unregistered)
    print("提示: 若该差异确属平台必需, 请登记进脚本内的允许清单并同步 docs/developer/Fabric侧移植差异.md")
    return 1


if __name__ == "__main__":
    force_utf8_output()
    sys.exit(main())
