"""copy_jars.py

负责从目录 ${RELEASE_PATH}/${config.release_vsersion} 下, 复制文件
VirtualRedstoneWire-${config.release_vsersion}-${config.mc_map_id[mc][0]}.jar
到目录 ${RUNTIMES_PATH}/${config.mc_map_runtime[mc]}/${config.runtimes_mods_dir} 下。

即: 对 mc_list 中每一个 mc 版本(带加载器名), 把能兼容它的第一个 jar 复制到
对应 Runtime 实例的 mods 目录。某侧缺失(清单, 产物, 实例, 映射)即拒绝执行。

用法:
    python copy_jars.py          走完整流程(前置校验 -> 复制 -> 复制后校验)
    python copy_jars.py check    只做两侧校验, 不复制任何文件
"""

import hashlib
import json
import shutil
import sys
from pathlib import Path

CONFIG = Path(__file__).resolve().parent / 'runtime.config.json'
ENV_FILE = Path(__file__).resolve().parent / '.env'
JAR_PREFIX = 'VirtualRedstoneWire-'
JAR_SUFFIX = '.jar'
# release_path 与 runtimes_path 已迁至 .env, 配置文件只保留空字段名
ENV_KEYS = ('RELEASE_PATH', 'RUNTIMES_PATH')
CHECK_ONLY = 'check'


def log(msg):
    print('[copy_jars] ' + msg)


def load_json(path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def load_env(path):
    """读取 .env; 缺失或键缺失都视为失败"""
    if not path.is_file():
        raise FileNotFoundError('.env 缺失: %s(可参考 .env.example)' % path)
    values = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if '=' not in line:
            continue
        key, _, value = line.partition('=')
        values[key.strip()] = value.strip()
    missing = [key for key in ENV_KEYS if not values.get(key)]
    if missing:
        raise ValueError('.env 缺少有效配置: %s' % ', '.join(missing))
    return values


def jar_name(version, target_id):
    return '%s%s-%s%s' % (JAR_PREFIX, version, target_id, JAR_SUFFIX)


def probe_path_writable(path, need_write):
    """对单个 path 做权限探测: 需要可读(读 jar), 或需要可写(落 jar)

    返回 (是否可写, 原因); 探测通过返回 ('', '')。不兜底权限, 只判断。
    """
    if not path.is_dir():
        return False, '目录不存在: %s' % path.as_posix()
    probe = path / '.perm_probe'
    try:
        if need_write:
            probe.touch()
            probe.unlink()
        else:
            probe.exists() or list(path.iterdir())
        return True, ''
    except OSError as exc:
        return False, '%s: %s' % (type(exc).__name__, exc)


def build_plan(release_dir, runtimes_path, mods_dir, version, mc_list, map_runtime, map_id):
    """生成复制计划; 返回 (条目列表, 缺失项列表)

    条目为 (mc, jar 源, jar 目标)。权限探测在 check_sides 统一做, 这里只做存在性。
    """
    plan = []
    missing = []
    for mc in mc_list:
        if mc not in map_runtime:
            missing.append('mc_map_runtime 缺少 %s' % mc)
            continue
        runtime_dir = runtimes_path / map_runtime[mc]
        if not runtime_dir.is_dir():
            missing.append('实例目录不存在: %s' % runtime_dir)
            continue

        if mc not in map_id:
            missing.append('mc_map_id 缺少 %s' % mc)
            continue
        ids = map_id[mc] if isinstance(map_id[mc], list) else [map_id[mc]]
        if not ids:
            missing.append('mc_map_id[%s] 为空' % mc)
            continue
        src = release_dir / jar_name(version, ids[0])
        if not src.is_file():
            missing.append('jar 缺失: %s' % src.name)
            continue
        plan.append((mc, src, runtime_dir / mods_dir / src.name))
    return plan, missing


def check_sides(release_dir, runtimes_path, mods_dir, version, mc_list, map_runtime, map_id):
    """两侧校验: 路径, 清单条目, 映射, 产物, 实例, 目标 mods 目录"""
    log('两侧校验开始')
    if not release_dir.is_dir():
        log('  release 目录不存在: %s' % release_dir)
        return False
    log('  release 目录: %s' % release_dir.as_posix())
    if not runtimes_path.is_dir():
        log('  运行时路径不存在: %s' % runtimes_path)
        return False
    log('  运行时路径: %s' % runtimes_path.as_posix())
    if not mc_list:
        log('  mc_list 为空')
        return False

    plan, missing = build_plan(release_dir, runtimes_path, mods_dir, version,
                               mc_list, map_runtime, map_id)
    for item in missing:
        log('  缺失: ' + item)

    # 两个 path 的权限探测: RELEASE_PATH 需可读, RUNTIMES_PATH 需可写; 任一不通过即拒绝
    perm_ok, perm_reason = probe_path_writable(release_dir, need_write=False)
    log('权限探测 RELEASE_PATH(读): %s' % ('通过' if perm_ok else '拒绝 ' + perm_reason))
    if not perm_ok:
        missing.append('RELEASE_PATH 不可读: ' + perm_reason)
    perm_ok, perm_reason = probe_path_writable(runtimes_path, need_write=True)
    log('权限探测 RUNTIMES_PATH(写): %s' % ('通过' if perm_ok else '拒绝 ' + perm_reason))
    if not perm_ok:
        missing.append('RUNTIMES_PATH 不可写: ' + perm_reason)

    print()
    print('=' * 60)
    print('总结')
    print('=' * 60)
    print('mc_list 条目    : %d 个' % len(mc_list))
    print('映射可用条目    : %d 个' % len(plan))
    print('拒绝项          : %d 个' % len(missing))
    for item in missing:
        print('  - ' + item)
    print()
    print('两侧校验通过' if not missing else '校验未通过, 不复制任何文件')
    return not missing


def verify_copied(plan):
    """复制后校验: 目标存在, 大小一致, md5 一致"""
    log('复制后校验开始')
    problems = []
    for mc, src, dst in plan:
        if not dst.is_file():
            problems.append('%s 目标缺失: %s' % (mc, dst.name))
            continue
        if dst.stat().st_size != src.stat().st_size:
            problems.append('%s 大小不一致: %s' % (mc, dst.name))
            continue
        if file_md5(src) != file_md5(dst):
            problems.append('%s 内容不一致(md5): %s' % (mc, dst.name))
            continue
        log('  %s -> %s 一致(%d 字节)' % (mc, dst.name, dst.stat().st_size))

    print()
    print('=' * 60)
    print('总结')
    print('=' * 60)
    print('校验条目: %d 个' % len(plan))
    print('校验问题: %d 个' % len(problems))
    for item in problems:
        print('  - ' + item)
    print()
    print('复制后校验通过' if not problems else '复制后校验未通过')
    return not problems


def file_md5(path):
    digest = hashlib.md5()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    args = sys.argv[1:]
    if len(args) > 1 or (args and args[0] != CHECK_ONLY):
        print('用法: python copy_jars.py [check]')
        return 1
    check_only = bool(args)

    if not CONFIG.is_file():
        print('配置文件缺失: %s' % CONFIG)
        return 1

    log('读取配置文件 ' + CONFIG.as_posix())
    config = load_json(CONFIG)

    log('读取环境变量文件 ' + ENV_FILE.as_posix())
    try:
        env = load_env(ENV_FILE)
    except (OSError, ValueError) as exc:
        print('环境变量读取失败: %s' % exc)
        return 1
    for key in ENV_KEYS:
        log('  %s = %s' % (key, env[key]))

    version = config['release_vsersion']
    release_dir = Path(env['RELEASE_PATH']) / version
    runtimes_path = Path(env['RUNTIMES_PATH'])
    mods_dir = config['runtimes_mods_dir']
    mc_list = config['mc_list']
    map_runtime = config['mc_map_runtime']
    map_id = config['mc_map_id']

    log('模式: %s' % ('仅校验, 不复制' if check_only else '校验 + 复制 + 复制后校验'))
    if not check_sides(release_dir, runtimes_path, mods_dir, version,
                       mc_list, map_runtime, map_id):
        return 1
    if check_only:
        return 0

    plan, missing = build_plan(release_dir, runtimes_path, mods_dir, version,
                               mc_list, map_runtime, map_id)
    copied = []
    print()
    print('=' * 60)
    print('开始复制')
    print('=' * 60)
    for mc, src, dst in plan:
        dst.parent.mkdir(parents=True, exist_ok=True)
        log('%s <- %s' % (mc, src.name))
        log('  目标 %s' % dst.parent.as_posix())
        shutil.copy2(src, dst)
        copied.append((mc, src, dst))

    print()
    print('=' * 60)
    print('总结')
    print('=' * 60)
    print('复制条目: %d 个' % len(copied))
    for mc, name, target in copied:
        print('  %-24s %-52s -> %s' % (mc, name.name, target.parent.as_posix()))
    if not copied:
        print('无可复制条目')
        return 1
    if not verify_copied(copied):
        return 1
    print()
    print('全部复制完成并通过校验')
    return 0


if __name__ == '__main__':
    sys.exit(main())
