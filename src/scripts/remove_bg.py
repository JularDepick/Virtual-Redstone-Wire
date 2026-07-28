"""
remove_bg.py — 素材预处理工具（连通分量版）

功能:
  1. 将图片居中补齐到 1:1 方形（不拉伸）
  2. 自动取样背景色（边缘出现次数最多的颜色）
  3. 找出所有与背景色相似的像素，仅保留面积最大的连通块作透明化
     （避免误伤非背景内容中与背景色同色的部分）
  4. 移除文件名中的 "_resized" 后缀

用法:
  python src/scripts/remove_bg.py

输入:  src/assets/*.png
输出:  src/assets/processed/*.png（已补齐方形、去背景）
"""

import os
from collections import Counter, deque
from PIL import Image

BASE_DIR    = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS_DIR  = os.path.join(BASE_DIR, "assets")
OUTPUT_DIR  = os.path.join(ASSETS_DIR, "processed")
TOLERANCE   = 50        # 曼哈顿距离容差

FILES = [
    "virtual_redstone_wire_resized.png",
    "wire_cutter_resized.png",
    "wire_magnifier_resized.png",
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


def color_similar(c1: tuple, c2: tuple, tol: int = TOLERANCE) -> bool:
    """曼哈顿距离判断两颜色是否相似"""
    return (abs(c1[0] - c2[0]) + abs(c1[1] - c2[1]) + abs(c1[2] - c2[2])) <= tol


# ── 连通分量分析 ──────────────────────────────────

def find_largest_background_blob(img: Image.Image, bg: tuple, tol: int = TOLERANCE) -> list:
    """
    遍历所有像素，找出与 bg 相似的颜色像素中面积最大的连通块，
    返回该连通块中所有像素坐标的列表。
    使用四连通（上/下/左/右）。
    """
    w, h = img.size
    px = img.load()

    # 1. 标记所有"候选"像素（与背景色相似且非透明）
    is_candidate = [[False] * h for _ in range(w)]
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 0 and color_similar((r, g, b), bg, tol):
                is_candidate[x][y] = True

    visited = [[False] * h for _ in range(w)]
    best_blob = []
    directions = [(-1,0), (1,0), (0,-1), (0,1)]

    for y in range(h):
        for x in range(w):
            if not is_candidate[x][y] or visited[x][y]:
                continue

            # BFS 搜集当前连通块
            blob = []
            dq = deque()
            dq.append((x, y))
            visited[x][y] = True

            while dq:
                cx, cy = dq.popleft()
                blob.append((cx, cy))
                for dx_, dy_ in directions:
                    nx, ny = cx + dx_, cy + dy_
                    if 0 <= nx < w and 0 <= ny < h:
                        if is_candidate[nx][ny] and not visited[nx][ny]:
                            visited[nx][ny] = True
                            dq.append((nx, ny))

            if len(blob) > len(best_blob):
                best_blob = blob

    return best_blob


def remove_background_blob(img: Image.Image, blob: list):
    """将指定连通块中的所有像素设为透明"""
    px = img.load()
    for (x, y) in blob:
        r, g, b, a = px[x, y]
        if a > 0:
            px[x, y] = (r, g, b, 0)


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

        # 2. 采样背景色
        bg = sample_background_color(img)
        print(f"  bg_color: {bg}")

        # 3. 找最大连通块
        blob = find_largest_background_blob(img, bg)
        print(f"  background_blob_pixels: {len(blob)} / {img.size[0] * img.size[1]}")

        # 4. 只删除该连通块
        remove_background_blob(img, blob)
        total_px = img.size[0] * img.size[1]

        # 5. 输出 — 去掉 _resized
        out_name = fname.replace("_resized", "")
        out_path = os.path.join(OUTPUT_DIR, out_name)
        img.save(out_path, "PNG")

        # 统计
        px_data = list(img.getdata())
        opq = sum(1 for _, _, _, a in px_data if a == 255)
        clr = sum(1 for _, _, _, a in px_data if a == 0)
        semi = total_px - opq - clr
        print(f"  [{out_name}]  {img.size}  (pad={pad_added})"
              f"  opaque={opq}({opq*100//total_px}%)"
              f"  semi={semi}({semi*100//total_px}%)"
              f"  clear={clr}({clr*100//total_px}%)")

    print("\nDone.")


if __name__ == "__main__":
    main()
