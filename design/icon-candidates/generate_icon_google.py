"""
A 方案的「Google 原生简洁感」重画版。

Google 自家图标（Fit / Clock / Wallet / Maps）的共性：
  · 等宽粗描边，圆头圆角，没有渐细、没有刻度、没有高光与辉光
  · 形状能被一句话描述：一个环、一根针、一个点
  · 单一强调色 + 中性轨道色，最多两色
  · 留白克制但清楚，负空间参与构图

G1 进度环 + 指针     最接近"仪表"，但去掉刻度与宝石
G2 开口环            只有一条环，末端圆头，像 Google Fit 的活动环
G3 双弧              外圈短亮弧 + 内圈完整淡环，Material 的 "speed" 语汇
G4 浅色容器版        同一套图形放到浅色容器上（Google 应用常见的容器语言）
"""

import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
SS = 3  # 干净线条对锯齿更敏感，超采样到 3 倍
SIZE = 512
UNIT = 108.0
PX = SIZE * SS / UNIT

CX, CY = 54 * PX, 54 * PX

BLUE = (138, 180, 248)      # Google blue 300
BLUE_D = (66, 133, 244)     # Google blue 500
YELLOW = (253, 214, 99)     # Google yellow 300
TEAL = (109, 213, 237)
TRACK = (60, 64, 67)        # dark grey track
INK = (32, 33, 36)
INK_DEEP = (12, 13, 15)
LIGHT_BG = (232, 234, 237)
LIGHT_TRACK = (189, 193, 198)


def u(v):
    return v * PX


def canvas(dark=True):
    size = SIZE * SS
    if dark:
        yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
        c = size / 2.0
        r = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / c
        t = np.clip(r, 0, 1) ** 1.15
        rgb = np.array(INK, np.float32)[None, None, :] * (1 - t[..., None]) + \
              np.array(INK_DEEP, np.float32)[None, None, :] * t[..., None]
    else:
        rgb = np.ones((size, size, 3), np.float32) * np.array(LIGHT_BG, np.float32)[None, None, :]
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def ring(img, r, a0, a1, w, color, cap=True):
    """等宽圆头弧线。"""
    lay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.arc([CX - r - w / 2, CY - r - w / 2, CX + r + w / 2, CY + r + w / 2],
          start=a0, end=a1, fill=color + (255,), width=max(1, int(w)))
    if cap:
        for a in (a0, a1):
            x, y = CX + r * math.cos(math.radians(a)), CY + r * math.sin(math.radians(a))
            d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=color + (255,))
    out = img.copy()
    out.alpha_composite(lay)
    return out


def capsule(img, p0, p1, w, color):
    """圆头粗线段（指针用）。"""
    lay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.line([p0, p1], fill=color + (255,), width=max(1, int(w)))
    for p in (p0, p1):
        d.ellipse([p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2], fill=color + (255,))
    out = img.copy()
    out.alpha_composite(lay)
    return out


def dot(img, p, r, color):
    lay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(lay).ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=color + (255,))
    out = img.copy()
    out.alpha_composite(lay)
    return out


def pt(r, deg):
    return (CX + r * math.cos(math.radians(deg)), CY + r * math.sin(math.radians(deg)))


def round_preview(img, size=SIZE):
    img = img.resize((size, size), Image.LANCZOS)
    m = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
    m = m.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    out.paste(img, (0, 0), m)
    return out


# ---------------- G1 进度环 + 指针 ----------------
def g1():
    img = canvas()
    r, w = u(30), u(7.5)
    img = ring(img, r, 150, 390, w, TRACK)          # 完整轨道（留 120° 缺口在下方）
    img = ring(img, r, 150, 335, w, BLUE)           # 已用量
    # 指针：短胶囊，不渐细
    img = capsule(img, (CX, CY), pt(u(17), -52), u(4.2), (241, 243, 244))
    img = dot(img, (CX, CY), u(5.2), BLUE_D)
    return img


# ---------------- G2 开口环 ----------------
def g2():
    img = canvas()
    r, w = u(31), u(8.5)
    img = ring(img, r, 120, 420, w, BLUE)           # 300° 环，缺口对称居中在正下方
    img = dot(img, pt(r, 420), w / 1.9, YELLOW)     # 末端一个小标记点
    img = dot(img, (CX, CY), u(4.4), (241, 243, 244))
    return img


# ---------------- G3 双弧 ----------------
def g3():
    img = canvas()
    r_out, r_in = u(31), u(19)
    img = ring(img, r_out, 150, 390, u(5.0), TRACK)
    img = ring(img, r_out, 150, 320, u(5.0), BLUE)
    img = ring(img, r_in, 150, 390, u(2.4), (58, 62, 68))
    img = capsule(img, (CX, CY), pt(u(15.5), -52), u(4.6), (241, 243, 244))
    img = dot(img, (CX, CY), u(4.6), YELLOW)
    return img


# ---------------- G4 浅色容器 ----------------
def g4():
    img = canvas(dark=False)
    r, w = u(29), u(7.5)
    img = ring(img, r, 150, 390, w, LIGHT_TRACK)
    img = ring(img, r, 150, 335, w, BLUE_D)
    img = capsule(img, (CX, CY), pt(u(16.5), -52), u(4.2), (32, 33, 36))
    img = dot(img, (CX, CY), u(5.0), BLUE_D)
    return img


def font(px):
    for p in ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/segoeui.ttf"):
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, px, index=0)
            except Exception:
                return ImageFont.truetype(p, px)
    return ImageFont.load_default()


def build():
    items = [("g1", "G1 进度环+指针", g1, True), ("g2", "G2 开口环", g2, True),
             ("g3", "G3 双弧", g3, True), ("g4", "G4 浅色容器", g4, False)]
    tiles = []
    for key, label, fn, dark in items:
        img = fn()
        img.resize((SIZE, SIZE), Image.LANCZOS).save(os.path.join(OUT, f"google-{key}.png"))
        tiles.append((label, round_preview(img), dark))

    cell, gap, row_gap, bottom = SIZE, 22, 58, 20
    cols, rows = 2, 2
    sheet = Image.new("RGBA", (cols * cell + (cols + 1) * gap,
                               gap + rows * cell + (rows - 1) * row_gap + bottom + 40), (0, 0, 0, 255))
    f = font(28)
    for idx, (label, tile, _) in enumerate(tiles):
        r_, c_ = divmod(idx, cols)
        x, y = gap + c_ * (cell + gap), gap + r_ * (cell + row_gap)
        sheet.paste(tile, (x, y), tile)
        ImageDraw.Draw(sheet).text((x + 6, y + cell + 6), label, font=f, fill=(190, 200, 214, 255))
    sheet.save(os.path.join(OUT, "sheet-google.png"))
    print("ok", sheet.size)


if __name__ == "__main__":
    build()