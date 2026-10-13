"""update_config.py

负责更新 alpha-runtime/runtime.config.json 的可变列表:
  jar_files_id, runtimes_list, runtimes_mods_dir, mc_list

用法:
  python update_config.py           走原有流程, 两个 Map 只校验不推导
  python update_config.py --withmap 额外自动推导两个 Map(编辑距离相似度匹配)

无论是否自动推导, 推导结果都必须由 Agent 或人工二次核验配置文件后再使用。

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
# 两个 path 的取值顺序: .env 优先, 配置文件次之, 两者都缺则拒绝
PATH_KEYS = {'RELEASE_PATH': 'release_path', 'RUNTIMES_PATH': 'runtimes_path'}
WITHMAP_FLAG = '--withmap'
# 编辑距离归一化后的相似度阈值, 低于该值的匹配判为失败并列入待人工确认
SIMILARITY_THRESHOLD = 0.50


def log(msg):
    print('[update_config] ' + msg)


def load_env(path):
    """读取 .env; 文件缺失不视为失败, 键缺失只记录警告"""
    if not path.is_file():
        return {}, 'missing'
    values = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if '=' not in line:
            continue
        key, _, value = line.partition('=')
        values[key.strip()] = value.strip()
    return values, 'ok'


def resolve_paths(env, config):
    """按 .env 优先, 配置文件次之的顺序解析两个 path

    返回 (paths 字典, 来源字典, 缺失键列表)
    """
    paths, sources, missing = {}, {}, []
    for env_key, cfg_key in PATH_KEYS.items():
        env_value = env.get(env_key, '')
        cfg_value = config.get(cfg_key, '') or ''
        if env_value:
            paths[env_key] = env_value
            sources[env_key] = '.env'
        elif cfg_value:
            paths[env_key] = cfg_value
            sources[env_key] = 'runtime.config.json'
        else:
            missing.append(env_key)
    return paths, sources, missing


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


def load_targets(manifest, label):
    """把一份清单展开成推导用目标列表: {id, mc:[版本区间], loader}"""
    if not manifest.is_file():
        raise FileNotFoundError('%s 侧清单缺失: %s' % (label, manifest))
    data = load_json(manifest)
    targets = []
    for entry in data:
        target_id = entry.get('id', '')
        versions = entry.get('mc', {}).get('version', [])
        loader = entry.get('mod-loader', {}).get('name', '')
        if not target_id or not versions or not loader:
            raise ValueError('%s 侧清单存在字段不完整的目标: %r' % (label, entry))
        targets.append({'id': target_id, 'mc': list(versions), 'loader': loader})
    return targets


def levenshtein(a, b):
    """编辑距离: 把 a 变成 b 所需的最少单字符编辑次数"""
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(previous[j] + 1,
                               current[j - 1] + 1,
                               previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def similarity(a, b):
    """归一化相似度: 1 为完全相同, 0 为完全不同"""
    longest = max(len(a), len(b))
    if longest == 0:
        return 1.0
    return 1.0 - levenshtein(a, b) / longest


def split_mc_key(mc):
    """把 mc-1.20.1-forge 拆成 (mc 版本号, 加载器名)"""
    target = mc[3:] if mc.startswith('mc-') else mc
    for loader in ('forge', 'fabric'):
        if target.endswith('-' + loader):
            return target[:-(len(loader) + 1)], loader
    return target, ''


# 加载器名到运行时目录后缀的写法映射(目录用首字母大写)
RUNTIME_SUFFIX = {'forge': 'Forge', 'fabric': 'Fabric'}


def guess_runtime_dir(mc, runtimes_existing):
    """按目录命名规则 <MC版本>-<Forge|Fabric> 猜实例目录, 取相似度最高者"""
    version, loader = split_mc_key(mc)
    suffix = RUNTIME_SUFFIX.get(loader, '')
    expected = '%s-%s' % (version, suffix) if suffix else version
    ranked = sorted(((similarity(expected, name), name) for name in runtimes_existing),
                    key=lambda item: (-item[0], item[1]))
    if not ranked:
        return None, 0.0, expected
    return ranked[0][1], ranked[0][0], expected


def guess_jar_id(mc, targets, existing_ids):
    """按清单推断能兼容 mc 的 jar id

    规则: 对每个目标算出其 mc 版本区间与加载器名, mc 落在区间内且加载器名一致即候选;
    区间长度越短(覆盖越精确)优先, 其次取 id 相似度高者。
    """
    version, loader = split_mc_key(mc)

    def in_range(versions):
        return version in versions

    candidates = [t for t in targets
                  if t.get('loader') == loader and in_range(t.get('mc', []))]
    if not candidates:
        return None, 0.0, expected_key(mc, loader)

    def sort_key(t):
        span = len(t.get('mc', []))
        return (span, -similarity(mc, t['id']))

    candidates.sort(key=sort_key)
    best = candidates[0]
    expected = expected_key(mc, loader)
    score = similarity(mc, best['id']) if loader == best.get('loader') else 0.0
    return best['id'], score, expected


def expected_key(mc, loader):
    return '%s-%s' % (mc, loader)


def infer_maps(config, runtimes_existing, targets):
    """自动推导两个 Map; 返回 (map_runtime, map_id, report 列表)

    report 每项为 (mc, 字段, 值, 相似度, 是否低于阈值)
    """
    mc_list = config.get('mc_list', [])
    existing_ids = set(config.get('jar_files_id', []))
    map_runtime = {}
    map_id = {}
    report = []
    for mc in mc_list:
        name, score, _expected = guess_runtime_dir(mc, runtimes_existing)
        map_runtime[mc] = name if name else ''
        if not name:
            report.append((mc, 'mc_map_runtime', '(无候选)', 0.0, True))
        else:
            report.append((mc, 'mc_map_runtime', name, score, score < SIMILARITY_THRESHOLD))

        target_id, score_id, _exp = guess_jar_id(mc, targets, existing_ids)
        map_id[mc] = [target_id] if target_id else []
        if not target_id:
            report.append((mc, 'mc_map_id', '(无候选)', 0.0, True))
        else:
            report.append((mc, 'mc_map_id', target_id, score_id, score_id < SIMILARITY_THRESHOLD))
    return map_runtime, map_id, report


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
    args = sys.argv[1:]
    unknown = [a for a in args if a != WITHMAP_FLAG]
    if unknown or args.count(WITHMAP_FLAG) > 1:
        print('用法: python update_config.py [%s]' % WITHMAP_FLAG)
        return 1
    withmap = WITHMAP_FLAG in args

    if not CONFIG.is_file():
        print('配置文件缺失: %s' % CONFIG)
        return 1

    log('读取配置文件 ' + CONFIG.as_posix())
    config = load_json(CONFIG)

    log('读取环境变量文件 ' + ENV_FILE.as_posix())
    env, env_state = load_env(ENV_FILE)
    if env_state == 'missing':
        log('  .env 不存在, 两个 path 回退到 runtime.config.json')
    else:
        for key in ENV_KEYS:
            log('  %s = %s' % (key, env[key] if env.get(key) else '(空, 回退配置文件)'))

    paths, sources, missing_keys = resolve_paths(env, config)
    if missing_keys:
        print('两个 path 均无有效值: %s' % ', '.join(missing_keys))
        print('请在 .env 或 runtime.config.json 中提供')
        return 1
    for key in ENV_KEYS:
        log('  %s 取自 %s: %s' % (key, sources[key], paths[key]))
    release_path = paths['RELEASE_PATH']
    runtimes_path = paths['RUNTIMES_PATH']

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

    targets = []
    if withmap:
        log('模式: 原有流程 + 自动推导 Map 映射(编辑距离相似度)')
        targets = load_targets(FORGE_MANIFEST, 'Forge') + load_targets(FABRIC_MANIFEST, 'Fabric')
        log('用于推导的目标 %d 个(Forge %d / Fabric %d)'
            % (len(targets), len(load_targets(FORGE_MANIFEST, 'Forge')),
               len(load_targets(FABRIC_MANIFEST, 'Fabric'))))
    else:
        log('模式: 原有流程, 两个 Map 只校验不推导')

    map_problems = check_maps(config, runtimes_existing)
    log('Map 映射人工检查: %d 个问题' % len(map_problems))
    for problem in map_problems:
        log('  ' + problem)

    infer_report = []
    low_confidence = []
    if withmap:
        log('按编辑距离相似度推导 Map 映射(阈值 %.2f)' % SIMILARITY_THRESHOLD)
        map_runtime, map_id, infer_report = infer_maps(config, runtimes_existing, targets)
        for mc, field, value, score, low in infer_report:
            mark = '  <-- 低于阈值, 需人工确认' if low else ''
            log('  %-20s %-16s %-28s 相似度 %.3f%s' % (mc, field, str(value), score, mark))
            if low:
                low_confidence.append('%s 的 %s = %s(相似度 %.3f)'
                                      % (mc, field, value, score))
        config['mc_map_runtime'] = map_runtime
        config['mc_map_id'] = map_id
        map_problems = check_maps(config, runtimes_existing)
        log('推导后复检 Map: %d 个问题' % len(map_problems))
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
    if withmap:
        print('mc_map_runtime    : %d 项(本轮自动推导)' % len(config.get('mc_map_runtime', {})))
        print('mc_map_id         : %d 项(本轮自动推导)' % len(config.get('mc_map_id', {})))
        print('推导明细          : %d 条, 其中 %d 条低于相似度阈值'
              % (len(infer_report), len(low_confidence)))
    else:
        print('mc_map_runtime    : %d 项(手动维护, 未改动)' % len(config.get('mc_map_runtime', {})))
        print('mc_map_id         : %d 项(手动维护, 未改动)' % len(config.get('mc_map_id', {})))
    print('缺失构建产物      : %d 个' % len(missing_jars))
    print('Map 映射问题      : %d 个' % len(map_problems))
    print('其它失败          : %d 项' % len([f for f in failures if '缺失构建产物' not in f]))

    with open(CONFIG, 'w', encoding='utf-8') as f:
        json.dump(config, f, ensure_ascii=False, indent=4)
        f.write('\n')
    log('已写回 ' + CONFIG.as_posix())

    if withmap:
        print()
        print('自动推导结果必须经 Agent 或人工二次核验配置文件后再使用')

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
