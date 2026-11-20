#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把桌宠 GIF 动画转成 sprite sheet，供 Kivy 逐帧播放。

素材来源：https://github.com/1TRYOUT/dsh-whale-pixel-pet （MIT）
"""

import json
import os
import sys

from PIL import Image, ImageSequence

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(os.path.dirname(HERE), "_src")
OUT = os.path.join(os.path.dirname(HERE), "assets")

# 动画名 -> 源 GIF
SOURCES = {
    "idle": "idle.gif",      # 待机呼吸休闲
    "happy": "happy.gif",    # 点击回应-开心跃动
    "drag": "drag.gif",      # 被鼠标拖拽悬空反馈
    "squash": "squash.gif",  # 原地重力下蹲压缩
}

COLS = 8
ROWS = 15
PAD = 2   # 每帧四周留 2px 透明边，避免缩放时采样到相邻帧


def _clean_alpha(img):
    """像素画不该有半透明边：alpha 二值化，并把透明区 RGB 归零。"""
    r, g, b, a = img.split()
    a = a.point(lambda v: 255 if v >= 128 else 0)
    out = Image.merge("RGBA", (r, g, b, a))
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                px[x, y] = (0, 0, 0, 0)
    return out


def convert(name, src_name):
    path = os.path.join(SRC, src_name)
    im = Image.open(path)
    frames = [_clean_alpha(fr.convert("RGBA")) for fr in ImageSequence.Iterator(im)]
    if not frames:
        raise SystemExit("no frames: " + path)

    fw, fh = frames[0].size
    if len(frames) > COLS * ROWS:
        frames = frames[: COLS * ROWS]

    cw, ch = fw + PAD * 2, fh + PAD * 2
    sheet = Image.new("RGBA", (COLS * cw, ROWS * ch), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        row, col = divmod(i, COLS)
        sheet.paste(fr, (col * cw + PAD, row * ch + PAD))

    out = os.path.join(OUT, name + "_sheet.png")
    sheet.save(out, optimize=True)
    return {
        "file": name + "_sheet.png",
        "cols": COLS,
        "rows": ROWS,
        "count": len(frames),
        "frame_w": fw,
        "frame_h": fh,
        "cell_w": cw,
        "cell_h": ch,
        "pad": PAD,
        "interval": 0.08,
    }, os.path.getsize(out)


def main():
    os.makedirs(OUT, exist_ok=True)
    meta = {}
    for name, src in SOURCES.items():
        info, size = convert(name, src)
        meta[name] = info
        print("%-8s %2d frames  %dx%d  %.0f KB" % (name, info["count"], info["frame_w"], info["frame_h"], size / 1024))
    with open(os.path.join(OUT, "anim_meta.json"), "w", encoding="utf-8") as fh:
        json.dump(meta, fh, indent=2, ensure_ascii=False)
    print("meta ->", os.path.join(OUT, "anim_meta.json"))


if __name__ == "__main__":
    main()
