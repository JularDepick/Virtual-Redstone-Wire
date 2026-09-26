#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
set_alpha.py —— 按透明度（alpha）阈值把像素点改为完全不透明或完全透明。

三种模式:
  默认（不透明）:            alpha > 0    -> 255     alpha == 0   -> 0（保持）
  -a / --transparent（透明）: alpha < 255  -> 0       alpha == 255 -> 255（保持）
  -b N / --threshold N:      按百分比阈值 N(0-100) 二值化（仅处理 PNG）:
        alpha <= ceil(N/100*255)  -> 0    完全透明
        alpha >  该分界值          -> 255  完全不透明

阈值模式说明:
  · N 的语义与「透明度百分比」一致（0% = 完全透明，100% = 完全不透明）：
        alpha <= N%   -> 0    完全透明
        alpha >  N%   -> 255  完全不透明
    分界值 = N/100*255 向下取整，如 -b 50 -> 127，即 alpha 0~127 全透明，128~255 全不透明。
  · N=0 时：只有 alpha==0 的像素算「透明 0%」，效果同默认模式。
  · N=100 时：整个 0-100% 区间都归入透明，即所有像素（含 alpha==255）都变完全透明。
    若想「保留完全不透明的像素、只把其余变透明」，请用 -a（alpha<255 -> 0）。
  · N 必为 0-100 的整数，-b 与 -a 互斥。

三种模式都不动「已经处于目标状态」的像素，只改另一侧的那些；RGB 颜色数据原样保留，
只重写 alpha 通道，因此脚本可重复运行（幂等）。注意 -a 与 -b 模式仅处理 PNG。

用法:
    python set_alpha.py                      # 当前目录全部图片 -> 完全不透明
    python set_alpha.py -a                   # 当前目录 -> 完全透明
    python set_alpha.py -b 50                # 阈值 50%：alpha<=128 全透明，其余全不透明
    python set_alpha.py -b 0 -r 图片目录      # 递归处理目录（仅 PNG）
    python set_alpha.py a.png b.png c.png    # 只处理指定文件
    python set_alpha.py -n                   # 只预演统计，不写文件(dry-run)

退出码: 0 全部成功 / 1 有文件失败 / 2 参数或无图片错误
"""

from __future__ import annotations

import argparse
import os
import sys
from collections import Counter

try:
    from PIL import Image, ImageChops, ImageSequence
except ImportError:  # pragma: no cover
    sys.stderr.write("[错误] 需要 Pillow：python -m pip install Pillow\n")
    raise SystemExit(2)

# Windows 控制台默认 GBK，中文输出会乱码/报错；统一走 UTF-8（失败也不影响功能）
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# 默认扫描的扩展名（Pillow 能读能写的常见格式）
DEFAULT_EXTS = ".png,.webp,.tif,.tiff,.bmp,.gif,.jpg,.jpeg"

# -b / -a 模式只处理 PNG
PNG_ONLY_EXTS = ".png"

# 扫描目录时默认跳过的文件（本脚本自己产生的校验图）
SKIP_NAMES = {"_check_contact_sheet.png"}

# 运行模式，由 main() 根据参数设置: "opaque" | "transparent" | "threshold"
MODE = "opaque"
THRESHOLD: int | None = None


def threshold_to_alpha(n: int) -> int:
    """
    阈值百分比 -> alpha 分界值（向下取整）。n=0 -> 0，n=100 -> 255。
    alpha <= 分界值 视为「透明度在 0-n% 范围内」。
    """
    return min(255, max(0, (int(n) * 255) // 100))


def threshold_arg(text: str) -> int:
    """argparse 类型校验：必须是 0-100 的整数。"""
    try:
        n = int(text)
    except ValueError:
        raise argparse.ArgumentTypeError(f"阈值必须是整数，收到 {text!r}")
    if not 0 <= n <= 100:
        raise argparse.ArgumentTypeError(f"阈值必须在 0-100 之间，收到 {n}")
    return n


def parse_args(argv=None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="set_alpha.py",
        description="按透明度阈值把像素改为完全不透明或完全透明（-b 阈值模式仅处理 PNG）。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "示例:\n"
            "  python set_alpha.py                    # 当前目录全部图片 -> 完全不透明\n"
            "  python set_alpha.py -a                 # 当前目录 -> 完全透明\n"
            "  python set_alpha.py -b 50              # alpha<=127 全透明，其余全不透明\n"
            "  python set_alpha.py -b 0 -r 图片目录     # 递归处理目录（仅 PNG）\n"
            "  python set_alpha.py icon.png -n         # 预演，不写盘\n"
        ),
    )
    p.add_argument("targets", nargs="*", default=["."],
                   help="图片文件或目录，默认当前目录 '.'")
    grp = p.add_mutually_exclusive_group()
    grp.add_argument("-a", "--transparent", dest="mode", action="store_const",
                     const="transparent", default="opaque",
                     help="改为【完全透明】（默认改为【完全不透明】）")
    grp.add_argument("-b", "--threshold", dest="threshold", type=threshold_arg, default=None,
                     metavar="0-100",
                     help="阈值百分比（0-100 整数）：alpha<=阈值 的像素变完全透明，"
                          "其余变完全不透明；与 -a 互斥，且只处理 PNG")
    p.add_argument("-r", "--recursive", action="store_true",
                   help="目录输入时递归子目录")
    p.add_argument("-n", "--dry-run", action="store_true",
                   help="只统计并打印将要发生的改动，不写文件")
    p.add_argument("--ext", default=None,
                   help="扫描目录时纳入的扩展名，逗号分隔"
                        f"（默认 {DEFAULT_EXTS}；-a/-b 模式下强制为 {PNG_ONLY_EXTS}）")
    return p.parse_args(argv)


def describe(nums: list[int], limit: int = 12) -> str:
    """把一组数值压成 'a,b,c ... (+N)' 的紧凑形式。"""
    head = ", ".join(str(n) for n in nums[:limit])
    if len(nums) > limit:
        head += f" ... (+{len(nums) - limit})"
    return head or "-"


def alpha_table(alpha: Image.Image) -> str:
    """给出一行 alpha 分布摘要：透明 / 不透明 / 中间 各多少像素。"""
    c = Counter(alpha.get_flattened_data())
    total = alpha.width * alpha.height
    transparent = c.get(0, 0)
    opaque = c.get(255, 0)
    partial = total - transparent - opaque
    distinct = sorted(v for v in c if v not in (0, 255))
    return (f"像素={total} 透明(0)={transparent} 不透明(255)={opaque} "
            f"中间({len(distinct)}种)={partial}")


def as_rgba(im: Image.Image) -> tuple[Image.Image, bool, str]:
    """
    规范化到 RGBA。
    返回 (rgba图像, 是否伪造了alpha, 说明文字)。
    原本没有 alpha 通道的图（如 RGB 不透明图）按 alpha 全 255 处理。
    """
    fmt = (im.format or "").upper()
    if im.mode in ("RGBA", "LA", "PA") or "transparency" in im.info:
        return im.convert("RGBA"), False, ""
    note = f"{im.mode} 无 alpha 通道（视为全部不透明）"
    if fmt in ("JPEG", "JPG"):
        note += "；JPEG 不支持透明，将改写为 PNG"
    return im.convert("RGBA"), True, note


def new_alpha_channel(alpha: Image.Image) -> Image.Image:
    """核心规则：把不在目标状态的像素改到目标端点，已在目标状态的像素不动。"""
    if MODE == "threshold":
        cut = threshold_to_alpha(THRESHOLD)
        # alpha <= cut -> 0（完全透明），否则 -> 255（完全不透明）
        return alpha.point(lambda v: 0 if v <= cut else 255, "L")
    if MODE == "transparent":               # alpha < 255 -> 0
        return alpha.point(lambda v: 0 if v < 255 else 255, "L")
    return alpha.point(lambda v: 255 if v > 0 else 0, "L")   # alpha > 0 -> 255


def plan_for(path: str) -> dict:
    """
    读取一个文件并算出改动计划（不改动原文件）。
    返回计划字典；多帧格式额外返回 rebuild 回调，用于按帧重建。
    """
    raw = Image.open(path)          # 不放进 with：多帧时后面还要用
    raw.load()
    fmt = (raw.format or "").upper()
    wh = raw.size
    n_frames = getattr(raw, "n_frames", 1)
    rgba, fake_alpha, note = as_rgba(raw)

    alpha = rgba.getchannel("A")
    new_alpha = new_alpha_channel(alpha)
    # 有像素真的变了？用差分求包围盒，比逐像素比较快得多
    changed = ImageChops.difference(alpha, new_alpha).getbbox() is not None
    rgba.putalpha(new_alpha)

    rebuild = None
    if n_frames > 1:
        frames = [f.convert("RGBA") for f in ImageSequence.Iterator(raw)]
        raw.close()
        for fr in frames:
            fr.putalpha(new_alpha_channel(fr.getchannel("A")))
        rebuild = frames
        note = (note + "；" if note else "") + f"多帧({n_frames})：全部帧一并处理，动画结构保留"

    return {
        "src": path, "dst": path, "format": fmt, "size": wh,
        "before": alpha_table(alpha), "after": alpha_table(new_alpha),
        "changed": changed, "note": note, "n_frames": n_frames,
        "image": rgba, "rebuild": rebuild,
    }


def save_plan(plan: dict) -> None:
    """按原格式写回；alpha 已烘焙进像素数据（不依赖调色板透明索引）。"""
    img, fmt, dst = plan["image"], plan["format"], plan["dst"]
    frames, kwargs = plan["rebuild"], {}

    if fmt in ("JPEG", "JPG"):
        # JPEG 不支持透明：改写为同名 PNG
        fmt = "PNG"
        dst = os.path.splitext(dst)[0] + ".png"
        plan["dst"] = dst
    elif fmt in ("BMP", "PPM"):
        kwargs["optimize"] = True

    tmp = dst + ".tmp"
    if frames is not None and fmt == "GIF":
        # 重编码动画：逐帧转 P 模式并用「丢弃 alpha」处理，透明部分保留
        pal_frames = []
        for fr in frames:
            p = fr.convert("RGBA")
            bg = Image.new("RGBA", p.size, (0, 0, 0, 0))
            pal_frames.append(Image.alpha_composite(bg, p).convert("P", palette=Image.Palette.ADAPTIVE))
        pal_frames[0].save(tmp, format="GIF", save_all=True, append_images=pal_frames[1:],
                           transparency=0, disposal=2, optimize=False)
    elif frames is not None:
        # 其它多帧格式（如 WebP/TIFF）：逐帧写入
        frames[0].save(tmp, format=fmt, save_all=True, append_images=frames[1:], **kwargs)
    else:
        img.save(tmp, format=fmt, **kwargs)

    os.replace(tmp, dst)


def collect_files(targets: list[str], recursive: bool, exts: set[str],
                  png_only: bool) -> tuple[list[str], list[str], list[str]]:
    """
    收集待处理文件。
    png_only=True 时（-a / -b 模式）：目录扫描只取 .png；
    显式传入的非 PNG 文件不静默忽略，而是放进 skipped 提示用户。
    返回 (待处理, 错误, 被跳过)。
    """
    files: list[str] = []
    errors: list[str] = []
    skipped: list[str] = []
    for t in targets:
        if os.path.isdir(t):
            if recursive:
                walker = (os.path.join(r, f) for r, _, fs in os.walk(t) for f in fs)
            else:
                walker = (os.path.join(t, f) for f in os.listdir(t))
            for p in sorted(walker):
                if os.path.splitext(p)[1].lower() in exts and os.path.basename(p) not in SKIP_NAMES:
                    files.append(p)
        elif os.path.isfile(t):
            if png_only and os.path.splitext(t)[1].lower() != ".png":
                skipped.append(t)
            else:
                files.append(t)
        else:
            errors.append(f"路径不存在: {t}")
    # 去重且保持顺序
    seen: set[str] = set()
    uniq = [f for f in files if not (f in seen or seen.add(f))]
    return uniq, errors, skipped


def main(argv=None) -> int:
    global MODE, THRESHOLD
    args = parse_args(argv)
    MODE, THRESHOLD = ("threshold" if args.threshold is not None else args.mode), args.threshold
    png_only = MODE in ("threshold", "transparent")

    # -a / -b 模式只处理 PNG；默认模式仍按 --ext 或 DEFAULT_EXTS
    if args.ext is not None:
        exts = {e if e.startswith(".") else "." + e for e in (x.strip().lower() for x in args.ext.split(",")) if e}
        if png_only and exts != {PNG_ONLY_EXTS}:
            print(f"[提示] {'-b' if MODE == 'threshold' else '-a'} 模式只处理 PNG，"
                  f"已忽略 --ext={args.ext}")
            exts = {PNG_ONLY_EXTS}
    else:
        exts = {PNG_ONLY_EXTS} if png_only else {
            e if e.startswith(".") else "." + e for e in DEFAULT_EXTS.split(",") if e}

    files, errors, skipped = collect_files(args.targets, args.recursive, exts, png_only)

    if MODE == "threshold":
        cut = threshold_to_alpha(THRESHOLD)
        label = (f"阈值 {THRESHOLD}% -> alpha<={cut} 变完全透明，"
                 f"alpha>{cut} 变完全不透明  (仅 PNG)")
    elif MODE == "transparent":
        label = "完全透明 (alpha<255 -> 0)  (仅 PNG)"
    else:
        label = "完全不透明 (alpha>0 -> 255)"
    print(f"模式: {label}{'  [dry-run 不写盘]' if args.dry_run else ''}")
    print(f"待处理: {len(files)} 个文件")
    for s in skipped:
        print(f"  [跳过] 非 PNG（该模式只处理 PNG）: {s}")
    if not files and not errors:
        print("没有找到可处理的图片。")
        return 2

    ok = changed_n = 0
    for path in files:
        print("-" * 64)
        print(f"文件: {path}")
        try:
            plan = plan_for(path)
        except Exception as exc:  # 单文件失败不影响其余文件
            print(f"  [失败] 无法处理: {exc}")
            errors.append(f"{path}: {exc}")
            continue

        print(f"  格式={plan['format']} 尺寸={plan['size'][0]}x{plan['size'][1]} 帧={plan['n_frames']}")
        if plan["note"]:
            print(f"  [提示] {plan['note']}")
        print(f"  改动前: {plan['before']}")
        print(f"  改动后: {plan['after']}")
        print(f"  结果: {'有改动，已处理' if plan['changed'] else '无需改动（所有像素已在目标状态）'}")

        if not args.dry_run:
            try:
                save_plan(plan)
                print(f"  写出: {plan['dst']}")
            except Exception as exc:
                print(f"  [失败] 写盘出错: {exc}")
                errors.append(f"{path}: {exc}")
                continue
        ok += 1
        changed_n += int(plan["changed"])

    print("=" * 64)
    print(f"完成: 成功 {ok}/{len(files)}，其中 {changed_n} 个文件发生了像素改动。")
    if errors:
        print(f"错误 {len(errors)} 项:")
        for e in errors:
            print(f"  - {e}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
