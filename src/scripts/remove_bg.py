"""
remove_bg.py - 素材预处理工具(HSV色相去绿版)

功能:
  1. 将图片居中补齐到 1:1 方形(不拉伸)
  2. 通过 HSV 色相范围过滤去除绿色背景(覆盖黄绿~青绿渐变),
     饱和度下限保护低饱和度的灰色/黑色物品主体
  3. 缩放到 512x512 输出

用法:
  python src/scripts/remove_bg.py

输入:  src/assets/*.png
输出:  src/assets/processed/*.png(已补齐方形、绿色背景透明、512x512)
"""

import os
from collections import Counter
from PIL import Image

BASE_DIR    = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS_DIR  = os.path.join(BASE_DIR, "assets")
OUTPUT_DIR  = os.path.join(ASSETS_DIR, "processed")
HUE_MIN     = 35        # 色相下界(黄绿)
HUE_MAX     = 115       # 色相上界(青绿)
SAT_MIN     = 40        # 饱和度下限,低于此值视为灰阶(物品)保留
OUTPUT_SIZE = 512

FILES = [
    "virtual_cable.png",
    "cable_cutter.png",
    "cable_magnifier.png",
]

# ── 图像工具 ──────────────────────────────────────

def pad_to_square(img: Image.Image) -> Image.Image:
    """居中补齐到 1:1，不拉伸"""
    w, h = img.size
    if w == h:
        return img
    side = max(w, h)
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    left = (side - w) // 2
    top  = (side - h) // 2
    out.paste(img, (left, top), img)
    return out


def sample_background_color(img: Image.Image, step: int = 4) -> tuple:
    """
    在图片四边缘 10px 宽条内采样，返回出现次数最多的颜色
    """
    w, h = img.size
    px   = img.load()
    samples = []

    for x in range(0, w, step):
        for y in (0, h - 1):
            for dy in range(10):
                yy = min(y + dy, h - 1) if y == 0 else max(y - dy, 0)
                r, g, b, a = px[x, yy]
                if a > 0:
                    samples.append((r, g, b))
    for y in range(0, h, step):
        for x in (0, w - 1):
            for dx in range(10):
                xx = min(x + dx, w - 1) if x == 0 else max(x - dx, 0)
                r, g, b, a = px[xx, y]
                if a > 0:
                    samples.append((r, g, b))

    if not samples:
        return (255, 255, 255)
    return Counter(samples).most_common(1)[0][0]


def is_green(h: int, s: int) -> bool:
    """HSV 判断是否为绿色背景像素(黄绿~青绿、饱和度足够)"""
    return HUE_MIN <= h <= HUE_MAX and s > SAT_MIN


def remove_green_background(img: Image.Image) -> int:
    """
    将所有绿色背景像素设为透明，返回处理像素数
    """
    w, h = img.size
    hsv  = img.convert("HSV")
    px   = img.load()
    hp   = hsv.load()
    removed = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h_, s, _ = hp[x, y]
            if is_green(h_, s):
                px[x, y] = (r, g, b, 0)
                removed += 1
    return removed


# ── 主流程 ────────────────────────────────────────

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    for fname in FILES:
        in_path = os.path.join(ASSETS_DIR, fname)
        if not os.path.isfile(in_path):
            print(f"[SKIP] {fname} not found")
            continue

        img = Image.open(in_path).convert("RGBA")
        orig_w, orig_h = img.size

        # 1. 补齐方形
        img = pad_to_square(img)
        pad_added = img.size[0] - orig_w if img.size[0] > orig_w else img.size[1] - orig_h

        # 2. 采样背景色(仅记录)
        bg = sample_background_color(img)
        print(f"  bg_color: {bg}")

        # 3. 去除绿色背景
        removed = remove_green_background(img)
        print(f"  green_pixels_removed: {removed} / {img.size[0] * img.size[1]}")

        # 4. 缩放到 512x512(LANCZOS 高质量缩放)
        img = img.resize((OUTPUT_SIZE, OUTPUT_SIZE), Image.LANCZOS)
        total_px = img.size[0] * img.size[1]

        # 5. 输出
        out_name = fname
        out_path = os.path.join(OUTPUT_DIR, out_name)
        img.save(out_path, "PNG")

        # 统计
        alpha_hist = img.split()[3].histogram()
        opq = alpha_hist[255]
        clr = alpha_hist[0]
        semi = total_px - opq - clr
        print(f"  [{out_name}]  {img.size}  (pad={pad_added})"
              f"  opaque={opq}({opq*100//total_px}%)"
              f"  semi={semi}({semi*100//total_px}%)"
              f"  clear={clr}({clr*100//total_px}%)")

    print("\nDone.")


if __name__ == "__main__":
    main()
