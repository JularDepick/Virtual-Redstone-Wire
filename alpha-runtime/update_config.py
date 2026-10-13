"""update_config.py

负责更新 alpha-runtime/runtime.config.json 的可变列表:
  jar_files_id, runtimes_list, runtimes_mods_dir, mc_list
mc_map_runtime 与 mc_map_id 两个 Map 映射必须由 Agent 或人工手动维护, 本脚本只检查不填写。

硬校验(某侧缺失即拒绝):
  - 任一份 versions.json 缺失或解析失败
  - 目标的 jar 在 release 目录中缺失(forge/fabric 两侧都要查)
  - 运行时实例目录缺失(mods 目录一并检查)
"""

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
CONFIG = Path(__file__).resolve().parent / 'runtime.config.json'
ENV_FILE = Path(__file__).resolve().parent / '.env'
FORGE_MANIFEST = REPO / 'versions' / 'versions.json'
FABRIC_MANIFEST = REPO / 'versions-fabric' / 'versions' / 'versions.json'
JAR_PREFIX = 'VirtualRedstoneWire-'
JAR_SUFFIX = '.jar'
# release_path 与 runtimes_path 已迁至 .env, 配置文件只保留空字段名
ENV_KEYS = ('RELEASE_PATH', 'RUNTIMES_PATH')


def log(msg):
    print('[update_config] ' + msg)


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


def load_json(path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def jar_name(version, target_id):
    return '%s%s-%s%s' % (JAR_PREFIX, version, target_id, JAR_SUFFIX)


def collect_ids(manifest, label):
    """从一份清单提取目标 id, 校验 id 唯一性"""
    if not manifest.is_file():
        raise FileNotFoundError('%s 侧清单缺失: %s' % (label, manifest))
    data = load_json(manifest)
    ids = []
    for entry in data:
        target_id = entry.get('id', '')
        if not target_id:
            raise ValueError('%s 侧清单存在无 id 的条目: %r' % (label, entry))
        if target_id in ids:
            raise ValueError('%s 侧清单存在重复 id: %s' % (label, target_id))
        ids.append(target_id)
    return ids


def check_jars(release_path, config, ids, label):
    """校验 release 目录下每个 id 的产物存在; 返回缺失列表"""
    version = config['release_vsersion']
    release_dir = Path(release_path) / version
    if not release_dir.is_dir():
        raise FileNotFoundError('%s 侧产物目录缺失: %s' % (label, release_dir))
    missing = []
    for target_id in ids:
        jar = release_dir / jar_name(version, target_id)
        if not jar.is_file():
            missing.append(jar.name)
    return missing


def check_runtime(runtimes_path):
    """校验运行时路径与实例目录; 返回 (实例名列表, 缺失的实例列表)"""
    root = Path(runtimes_path)
    if not root.is_dir():
        raise FileNotFoundError('运行时路径缺失: %s' % root)
    existing = sorted(p.name for p in root.iterdir() if p.is_dir())
    return existing, []


def check_maps(config, runtimes_existing):
    """校验两个手动维护的 Map: key 必须落在 mc_list, 值必须存在"""
    problems = []
    mc_list = config.get('mc_list', [])
    map_runtime = config.get('mc_map_runtime', {})
    map_id = config.get('mc_map_id', {})

    for mc in map_runtime:
        if mc not in mc_list:
            problems.append('mc_map_runtime 的 key 不属于 mc_list: %s' % mc)
        elif map_runtime[mc] not in runtimes_existing:
            problems.append('mc_map_runtime[%s] 指向的实例不存在: %s' % (mc, map_runtime[mc]))

    ids = set(config.get('jar_files_id', []))
    for mc in map_id:
        if mc not in mc_list:
            problems.append('mc_map_id 的 key 不属于 mc_list: %s' % mc)
            continue
        values = map_id[mc] if isinstance(map_id[mc], list) else [map_id[mc]]
        if not values:
            problems.append('mc_map_id[%s] 为空列表' % mc)
        for target_id in values:
            if target_id not in ids:
                problems.append('mc_map_id[%s] 的值不属于 jar_files_id: %s' % (mc, target_id))
    for mc in mc_list:
        if mc not in map_runtime:
            problems.append('mc_map_runtime 缺少 mc_list 中的键: %s' % mc)
        if mc not in map_id:
            problems.append('mc_map_id 缺少 mc_list 中的键: %s' % mc)
    return problems


def main():
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
    release_path = env['RELEASE_PATH']
    runtimes_path = env['RUNTIMES_PATH']

    failures = []

    try:
        log('读取 Forge 侧清单 ' + FORGE_MANIFEST.as_posix())
        forge_ids = collect_ids(FORGE_MANIFEST, 'Forge')
        log('Forge 侧目标 %d 个: %s' % (len(forge_ids), ', '.join(forge_ids)))

        log('读取 Fabric 侧清单 ' + FABRIC_MANIFEST.as_posix())
        fabric_ids = collect_ids(FABRIC_MANIFEST, 'Fabric')
        log('Fabric 侧目标 %d 个: %s' % (len(fabric_ids), ', '.join(fabric_ids)))
    except (OSError, ValueError) as exc:
        print('清单读取失败: %s' % exc)
        return 1

    all_ids = forge_ids + fabric_ids
    overlap = set(forge_ids) & set(fabric_ids)
    if overlap:
        failures.append('两侧清单存在同名 id: %s' % ', '.join(sorted(overlap)))

    try:
        runtimes_existing, missing_runtime = check_runtime(runtimes_path)
    except FileNotFoundError as exc:
        print('运行时检查失败: %s' % exc)
        return 1

    log('运行时路径下共 %d 个实例' % len(runtimes_existing))
    if missing_runtime:
        failures.append('runtimes_list 中的实例不存在: %s' % ', '.join(missing_runtime))

    missing_jars = []
    for label, ids in (('Forge', forge_ids), ('Fabric', fabric_ids)):
        try:
            missing = check_jars(release_path, config, ids, label)
        except FileNotFoundError as exc:
            print('产物检查失败: %s' % exc)
            return 1
        log('%s 侧产物齐全度: %d/%d' % (label, len(ids) - len(missing), len(ids)))
        for name in missing:
            log('  %s 侧缺少产物: %s' % (label, name))
        missing_jars.extend(missing)

    if missing_jars:
        failures.append('缺失构建产物 %d 个, 请先构建再更新配置' % len(missing_jars))

    map_problems = check_maps(config, runtimes_existing)
    log('Map 映射人工检查: %d 个问题' % len(map_problems))
    for problem in map_problems:
        log('  ' + problem)

    config['jar_files_id'] = all_ids
    config['runtimes_list'] = runtimes_existing
    config['runtimes_mods_dir'] = 'mods'
    # release_path 与 runtimes_path 归空, 字段名保留, 实际值只来自 .env
    config['release_path'] = ''
    config['runtimes_path'] = ''

    print()
    print('=' * 60)
    print('总结')
    print('=' * 60)
    print('jar_files_id      : %d 个(Forge %d / Fabric %d)'
          % (len(all_ids), len(forge_ids), len(fabric_ids)))
    print('runtimes_list     : %d 个实例' % len(runtimes_existing))
    print('runtimes_mods_dir : %s' % config['runtimes_mods_dir'])
    print('mc_list           : %d 个(手动维护, 未改动)' % len(config.get('mc_list', [])))
    print('mc_map_runtime    : %d 项(手动维护, 未改动)' % len(config.get('mc_map_runtime', {})))
    print('mc_map_id         : %d 项(手动维护, 未改动)' % len(config.get('mc_map_id', {})))
    print('缺失构建产物      : %d 个' % len(missing_jars))
    print('Map 映射问题      : %d 个' % len(map_problems))
    print('其它失败          : %d 项' % len([f for f in failures if '缺失构建产物' not in f]))

    with open(CONFIG, 'w', encoding='utf-8') as f:
        json.dump(config, f, ensure_ascii=False, indent=4)
        f.write('\n')
    log('已写回 ' + CONFIG.as_posix())

    if failures or map_problems:
        print()
        print('存在待处理问题(未通过):')
        for item in failures:
            print('  - ' + item)
        return 1

    print()
    print('全部校验通过')
    return 0


if __name__ == '__main__':
    sys.exit(main())
