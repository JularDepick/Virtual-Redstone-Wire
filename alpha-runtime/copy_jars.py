"""copy_jars.py

负责从目录 ${config.release_path}/${config.release_vsersion} 下, 复制文件
VirtualRedstoneWire-${config.release_vsersion}-${config.mc_map_id[mc][0]}.jar
到目录 ${config.runtimes_path}/${config.mc_map_runtime[mc]}/${config.runtimes_mods_dir} 下。

即: 对 mc_list 中每一个 mc 版本(带加载器名), 把能兼容它的第一个 jar 复制到
对应 Runtime 实例的 mods 目录。某侧缺失(清单, 产物, 实例, 映射)即拒绝执行。
"""

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


def validate(release_dir, runtimes_path, mods_dir, mc_list, map_runtime, map_id):
    """返回缺失项列表; 空列表表示可以执行"""
    missing = []
    if not release_dir.is_dir():
        missing.append('release 目录不存在: %s' % release_dir)
    if not runtimes_path.is_dir():
        missing.append('runtimes_path 不存在: %s' % runtimes_path)
    if not mc_list:
        missing.append('mc_list 为空')

    for mc in mc_list:
        if mc not in map_runtime:
            missing.append('mc_map_runtime 缺少 %s' % mc)
            continue
        runtime_dir = runtimes_path / map_runtime[mc]
        if not runtime_dir.is_dir():
            missing.append('实例目录不存在: %s' % runtime_dir)

        if mc not in map_id:
            missing.append('mc_map_id 缺少 %s' % mc)
            continue
        ids = map_id[mc] if isinstance(map_id[mc], list) else [map_id[mc]]
        if not ids:
            missing.append('mc_map_id[%s] 为空' % mc)
            continue
        jar = release_dir / jar_name(version, ids[0])
        if not jar.is_file():
            missing.append('jar 缺失: %s' % jar.name)
    return missing


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

    version = config['release_vsersion']
    release_dir = Path(env['RELEASE_PATH']) / version
    runtimes_path = Path(env['RUNTIMES_PATH'])
    mods_dir = config['runtimes_mods_dir']
    mc_list = config['mc_list']
    map_runtime = config['mc_map_runtime']
    map_id = config['mc_map_id']

    log('校验清单, 产物, 实例与映射')
    missing = validate(release_dir, runtimes_path, mods_dir, mc_list, map_runtime, map_id)
    if missing:
        print()
        print('=' * 60)
        print('拒绝执行: 以下项缺失')
        print('=' * 60)
        for item in missing:
            print('  - ' + item)
        print()
        print('共 %d 项缺失, 未复制任何文件' % len(missing))
        return 1

    copied = []
    print()
    print('=' * 60)
    print('开始复制')
    print('=' * 60)
    for mc in mc_list:
        target_id = (map_id[mc] if isinstance(map_id[mc], list) else [map_id[mc]])[0]
        runtime_dir = runtimes_path / map_runtime[mc] / mods_dir
        jar = release_dir / jar_name(version, target_id)
        runtime_dir.mkdir(parents=True, exist_ok=True)
        log('%s <- %s' % (mc, jar.name))
        log('  目标 %s' % runtime_dir.as_posix())
        shutil.copy2(jar, runtime_dir / jar.name)
        copied.append((mc, jar.name, runtime_dir))

    print()
    print('=' * 60)
    print('总结')
    print('=' * 60)
    print('复制条目: %d 个' % len(copied))
    for mc, name, target in copied:
        print('  %-24s %-52s -> %s' % (mc, name, target.as_posix()))
    print()
    print('全部复制完成')
    return 0


if __name__ == '__main__':
    sys.exit(main())
