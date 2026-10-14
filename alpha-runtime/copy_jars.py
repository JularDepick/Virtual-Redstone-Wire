"""copy_jars.py — 把产物复制到测试运行时的游戏实例

用法:
    python copy_jars.py        校验 -> 复制 -> 复制后校验
    python copy_jars.py check  只校验, 不复制

每个 mc_list 条目取其 mc_map_id 的第一个 jar, 复制到
${RUNTIMES_PATH}/${mc_map_runtime[mc]}/${runtimes_mods_dir} 下。
"""

import hashlib
import json
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CONFIG = HERE / 'runtime.config.json'
ENV_FILE = HERE / '.env'
JAR_PREFIX = 'VirtualRedstoneWire-'
PATH_KEYS = {'RELEASE_PATH': 'release_path', 'RUNTIMES_PATH': 'runtimes_path'}


def log(msg):
    print('[copy_jars] ' + msg)


def read_env():
    """读 .env; 返回 {键: 值}, 文件缺失返回 {}"""
    if not ENV_FILE.is_file():
        return {}
    values = {}
    for line in ENV_FILE.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            key, _, value = line.partition('=')
            values[key.strip()] = value.strip()
    return values


def resolve_paths(env, config):
    """两个 path 按 .env 优先, 配置文件次之; 返回 (值, 来源, 缺失键)"""
    paths, sources, missing = {}, {}, []
    for env_key, cfg_key in PATH_KEYS.items():
        value = env.get(env_key) or config.get(cfg_key) or ''
        if value:
            paths[env_key] = value
            sources[env_key] = '.env' if env.get(env_key) else 'runtime.config.json'
        else:
            missing.append(env_key)
    return paths, sources, missing


def jar_name(version, target_id):
    return '%s%s-%s%s' % (JAR_PREFIX, version, target_id, '.jar')


def writable(path):
    """探测 path 是否可写; 返回 (是否可写, 原因)"""
    if not path.is_dir():
        return False, '目录不存在'
    probe = path / '.perm_probe'
    try:
        probe.touch()
        probe.unlink()
        return True, ''
    except OSError as exc:
        return False, '%s: %s' % (type(exc).__name__, exc)


def readable(path):
    """探测 path 是否可读; 返回 (是否可读, 原因)"""
    if not path.is_dir():
        return False, '目录不存在'
    try:
        next(iter(path.iterdir()), None)
        return True, ''
    except OSError as exc:
        return False, '%s: %s' % (type(exc).__name__, exc)


def build_plan(config, release_dir, runtimes_path):
    """返回 (计划, 问题); 计划为 [(mc, 源 jar, 目标 mods 目录)]"""
    version = config['release_vsersion']
    mods_dir = config['runtimes_mods_dir']
    plan, problems = [], []
    for mc in config['mc_list']:
        runtime = config['mc_map_runtime'].get(mc)
        ids = config['mc_map_id'].get(mc) or []
        if not runtime:
            problems.append('mc_map_runtime 缺少 %s' % mc)
            continue
        if not ids:
            problems.append('mc_map_id 缺少 %s' % mc)
            continue
        runtime_dir = runtimes_path / runtime
        if not runtime_dir.is_dir():
            problems.append('实例目录不存在: %s' % runtime_dir)
            continue
        src = release_dir / jar_name(version, ids[0])
        if not src.is_file():
            problems.append('jar 缺失: %s' % src.name)
            continue
        plan.append((mc, src, runtime_dir / mods_dir))
    return plan, problems


def md5(path):
    digest = hashlib.md5()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    args = sys.argv[1:]
    if args not in ([], ['check']):
        print('用法: python copy_jars.py [check]')
        return 1
    check_only = bool(args)

    if not CONFIG.is_file():
        print('配置文件缺失: %s' % CONFIG)
        return 1
    config = json.loads(CONFIG.read_text(encoding='utf-8'))

    env = read_env()
    log('读取 ' + ENV_FILE.as_posix() + ('(不存在)' if not env else ''))
    paths, sources, missing_keys = resolve_paths(env, config)
    if missing_keys:
        print('两个 path 都取不到值: %s' % ', '.join(missing_keys))
        return 1
    for key in PATH_KEYS:
        log('  %s 取自 %s: %s' % (key, sources[key], paths[key]))

    version = config['release_vsersion']
    release_dir = Path(paths['RELEASE_PATH']) / version
    runtimes_path = Path(paths['RUNTIMES_PATH'])
    log('模式: %s' % ('只校验' if check_only else '校验 + 复制 + 复制后校验'))

    problems = []
    ok, reason = readable(release_dir)
    log('权限探测 %s: %s' % (release_dir.as_posix(), '可读' if ok else '拒绝 ' + reason))
    if not ok:
        problems.append('release 目录不可读: ' + reason)
    ok, reason = writable(runtimes_path)
    log('权限探测 %s: %s' % (runtimes_path.as_posix(), '可写' if ok else '拒绝 ' + reason))
    if not ok:
        problems.append('runtimes_path 不可写: ' + reason)

    plan, plan_problems = build_plan(config, release_dir, runtimes_path)
    problems.extend(plan_problems)
    log('复制计划 %d 条, 问题 %d 条' % (len(plan), len(plan_problems)))

    if problems:
        print()
        print('=' * 56)
        print('总结')
        print('=' * 56)
        print('mc_list 条目 : %d' % len(config['mc_list']))
        print('可用计划     : %d' % len(plan))
        print('拒绝项       : %d' % len(problems))
        for item in problems:
            print('  - ' + item)
        print()
        print('校验未通过, 未复制任何文件')
        return 1

    if check_only:
        print()
        print('=' * 56)
        print('总结')
        print('=' * 56)
        print('mc_list 条目 : %d' % len(config['mc_list']))
        print('可用计划     : %d' % len(plan))
        print()
        print('两侧校验通过')
        return 0

    print()
    print('=' * 56)
    print('开始复制')
    print('=' * 56)
    for mc, src, mods_dir in plan:
        mods_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, mods_dir / src.name)
        log('%-22s %s' % (mc, (mods_dir / src.name).as_posix()))

    bad = []
    for mc, src, mods_dir in plan:
        dst = mods_dir / src.name
        if not dst.is_file() or dst.stat().st_size != src.stat().st_size \
                or md5(src) != md5(dst):
            bad.append('%s -> %s' % (mc, dst.as_posix()))

    print()
    print('=' * 56)
    print('总结')
    print('=' * 56)
    print('复制条目     : %d' % len(plan))
    print('复制后校验   : %d 条通过, %d 条失败' % (len(plan) - len(bad), len(bad)))
    for item in bad:
        print('  - ' + item)
    if bad:
        return 1
    print()
    print('全部复制完成并通过校验')
    return 0


if __name__ == '__main__':
    sys.exit(main())
