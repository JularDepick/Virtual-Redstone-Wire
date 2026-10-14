"""update_config.py — 更新 alpha-runtime/runtime.config.json 的可变列表

用法:
    python update_config.py            更新可变列表, 两个 Map 只校验
    python update_config.py --withmap  额外推导两个 Map(编辑距离相似度)

可变列表来源:
    jar_files_id      扫描 ${RELEASE_PATH}/${release_vsersion} 下的 jar 文件名
    runtimes_list     扫描 ${RUNTIMES_PATH} 的子目录
    mc_list           把 jar_files_id 的 MC 区间拆成单个 MC 版本(带加载器名)
    runtimes_mods_dir 固定 mods

两个 Map 推导结果必须经 Agent 或人工二次核验。
"""

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CONFIG = HERE / 'runtime.config.json'
ENV_FILE = HERE / '.env'
JAR_PREFIX = 'VirtualRedstoneWire-'
PATH_KEYS = {'RELEASE_PATH': 'release_path', 'RUNTIMES_PATH': 'runtimes_path'}
LOADER_SUFFIX = {'forge': 'Forge', 'fabric': 'Fabric'}
LOADER_RANK = {'forge': 0, 'fabric': 1}
THRESHOLD = 0.50


def log(msg):
    print('[update_config] ' + msg)


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


def scan_jars(release_dir, version):
    """扫描 release 目录, 从文件名取出 jar ID 列表"""
    prefix = '%s%s-' % (JAR_PREFIX, version)
    ids, problems = [], []
    for path in sorted(release_dir.glob(JAR_PREFIX + '*.jar')):
        name = path.name
        if not name.startswith(prefix):
            problems.append('文件名版本号不符: %s' % name)
            continue
        target_id = name[len(prefix):-len('.jar')]
        if target_id in ids:
            problems.append('jar ID 重复: %s' % target_id)
            continue
        ids.append(target_id)
    return ids, problems


def split_id(target_id):
    """mc-1.20_1.20.1-forge -> (['1.20', '1.20.1'], 'forge'); 解析失败返回 None"""
    body = target_id[3:] if target_id.startswith('mc-') else target_id
    for loader in LOADER_RANK:
        suffix = '-' + loader
        if body.endswith(suffix):
            versions = body[:-len(suffix)].split('_')
            if all(v and 'x' not in v for v in versions):
                return versions, loader
            return None
    return None


def derive_mc_list(ids):
    """把 jar ID 的 MC 区间拆成单个 MC 版本(带加载器名), 去重排序"""
    keys, problems = set(), []
    for target_id in ids:
        parsed = split_id(target_id)
        if parsed is None:
            problems.append('无法从 jar ID 解析 MC 版本与加载器: %s' % target_id)
            continue
        versions, loader = parsed
        split = ['mc-%s-%s' % (version, loader) for version in versions]
        log('  %-28s -> %s' % (target_id, ', '.join(split)))
        keys.update(split)
    return sorted(keys, key=version_key), problems


def numbers_of(version):
    """1.20.1 -> (1, 20, 1)"""
    return tuple(int(x) if x.isdigit() else 0 for x in version.split('.'))


def version_key(mc):
    """mc-1.20.1-forge -> ((1, 20, 1), 加载器序号), 用于排序"""
    version, loader = split_mc(mc)
    return numbers_of(version), LOADER_RANK.get(loader, 9)


def id_key(target_id):
    """jar ID 排序: 先按加载器, 再按起始 MC 版本"""
    parsed = split_id(target_id)
    if not parsed:
        return 9, ()
    versions, loader = parsed
    return LOADER_RANK.get(loader, 9), numbers_of(versions[0])


def split_mc(mc):
    """mc-1.20.1-forge -> ('1.20.1', 'forge')"""
    body = mc[3:] if mc.startswith('mc-') else mc
    head, _, loader = body.rpartition('-')
    return head, loader


def levenshtein(a, b):
    """编辑距离(滚动数组, 空间 O(len(b)))"""
    if a == b:
        return 0
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1,
                               previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def similarity(a, b):
    longest = max(len(a), len(b))
    return 1.0 if longest == 0 else 1.0 - levenshtein(a, b) / longest


def pick_runtime(mc, runtimes):
    """按 <MC版本>-<Forge|Fabric> 拼期望目录名, 取相似度最高的实例"""
    version, loader = split_mc(mc)
    expected = '%s-%s' % (version, LOADER_SUFFIX.get(loader, ''))
    best = max(((similarity(expected, name), name) for name in runtimes), default=(0.0, ''))
    return best[1], best[0]


def pick_jar(mc, ids):
    """取覆盖该 MC 版本且加载器一致的 jar ID; 区间越短越优先, 再比相似度"""
    version, loader = split_mc(mc)
    candidates = []
    for target_id in ids:
        parsed = split_id(target_id)
        if parsed and parsed[1] == loader and version in parsed[0]:
            candidates.append((len(parsed[0]), -similarity(mc, target_id), target_id))
    if not candidates:
        return '', 0.0
    target_id = min(candidates)[2]
    return target_id, similarity(mc, target_id)


def check_maps(config, runtimes):
    """校验两个 Map 与 mc_list / jar_files_id 的一致性"""
    mc_list = config.get('mc_list', [])
    ids = set(config.get('jar_files_id', []))
    problems = []
    for field in ('mc_map_runtime', 'mc_map_id'):
        table = config.get(field, {})
        for mc in sorted(set(table) - set(mc_list)):
            problems.append('%s 的键不在 mc_list: %s' % (field, mc))
        for mc in mc_list:
            if mc not in table:
                problems.append('%s 缺少 mc_list 的键: %s' % (field, mc))
    for mc, name in config.get('mc_map_runtime', {}).items():
        if name not in runtimes:
            problems.append('mc_map_runtime[%s] 的实例不存在: %s' % (mc, name))
    for mc, values in config.get('mc_map_id', {}).items():
        values = values if isinstance(values, list) else [values]
        if not values:
            problems.append('mc_map_id[%s] 为空' % mc)
        for target_id in values:
            if target_id not in ids:
                problems.append('mc_map_id[%s] 的值不在 jar_files_id: %s' % (mc, target_id))
    return problems


def main():
    args = sys.argv[1:]
    if args not in ([], ['--withmap']):
        print('用法: python update_config.py [--withmap]')
        return 1
    withmap = bool(args)

    log('读取 ' + CONFIG.as_posix())
    if not CONFIG.is_file():
        print('配置文件缺失')
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
    if not release_dir.is_dir():
        print('release 目录不存在: %s' % release_dir)
        return 1
    ids, hard = scan_jars(release_dir, version)
    log('release 目录 %s: jar %d 个' % (release_dir.as_posix(), len(ids)))
    if not ids:
        print('release 目录下没有匹配的 jar')
        return 1

    runtimes_path = Path(paths['RUNTIMES_PATH'])
    if not runtimes_path.is_dir():
        print('runtimes_path 不存在: %s' % runtimes_path)
        return 1
    runtimes = sorted(p.name for p in runtimes_path.iterdir() if p.is_dir())
    log('runtimes_path %s: 实例 %d 个' % (runtimes_path.as_posix(), len(runtimes)))

    log('由 jar_files_id 拆解 mc_list:')
    mc_list, mc_hard = derive_mc_list(ids)
    hard.extend(mc_hard)
    log('拆出 MC 版本 %d 个' % len(mc_list))
    if hard:
        # jar ID 解析失败时不写回, 避免把残缺列表落进配置
        print()
        for item in hard:
            print('未通过: ' + item)
        print('配置未改动')
        return 1

    config['jar_files_id'] = sorted(ids, key=id_key)
    config['runtimes_list'] = runtimes
    config['runtimes_mods_dir'] = 'mods'
    config['mc_list'] = mc_list
    config['release_path'] = ''
    config['runtimes_path'] = ''
    log('可变列表: jar_files_id %d, runtimes_list %d, mc_list %d'
        % (len(config['jar_files_id']), len(runtimes), len(mc_list)))

    soft = []
    if withmap:
        log('推导两个 Map(阈值 %.2f)' % THRESHOLD)
        map_runtime, map_id, low = {}, {}, []
        for mc in mc_list:
            name, score = pick_runtime(mc, runtimes)
            target_id, score_id = pick_jar(mc, ids)
            map_runtime[mc] = name
            map_id[mc] = [target_id] if target_id else []
            log('  %-22s -> %-16s %.3f | %-28s %.3f'
                % (mc, name or '(无)', score, target_id or '(无)', score_id))
            if score < THRESHOLD:
                low.append('%s 的实例 %s(%.3f)' % (mc, name or '(无)', score))
            if score_id < THRESHOLD:
                low.append('%s 的产物 %s(%.3f)' % (mc, target_id or '(无)', score_id))
        config['mc_map_runtime'] = map_runtime
        config['mc_map_id'] = map_id
        if low:
            soft.append('相似度低于阈值 %d 项, 需人工确认' % len(low))

    problems = check_maps(config, runtimes)
    for problem in problems:
        log('Map 问题: ' + problem)
    if problems:
        soft.append('Map 映射问题 %d 个' % len(problems))

    CONFIG.write_text(json.dumps(config, ensure_ascii=False, indent=4) + '\n', encoding='utf-8')
    log('已写回 ' + CONFIG.as_posix())

    print()
    print('=' * 56)
    print('总结')
    print('=' * 56)
    print('jar_files_id   : %d' % len(config['jar_files_id']))
    print('runtimes_list  : %d' % len(config['runtimes_list']))
    print('mc_list        : %d' % len(config['mc_list']))
    print('mc_map_runtime : %d%s' % (len(config['mc_map_runtime']),
                                    '(本轮推导)' if withmap else '(人工)'))
    print('mc_map_id      : %d%s' % (len(config['mc_map_id']),
                                    '(本轮推导)' if withmap else '(人工)'))
    print('Map 问题       : %d' % len(problems))
    if withmap:
        print('推导结果需 Agent 或人工二次核验')
    if soft:
        print()
        for item in soft:
            print('未通过: ' + item)
        return 1
    print()
    print('全部校验通过')
    return 0


if __name__ == '__main__':
    sys.exit(main())
