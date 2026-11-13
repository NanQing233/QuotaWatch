"""
G1 造型（进度环 + 胶囊指针 + 圆点枢轴）的风格变体。

造型不动，只换配色、描边粗细、容器与是否用渐变：
  S1 Google 蓝     原版：亮蓝环 + 白针 + 蓝点，深色容器
  S2 单色蓝        环/针/点同色系，最克制
  S3 薄荷          冷绿系，表盘上最"健康感"
  S4 暖橙          琥珀系，和深色背景对比最柔和
  S5 浅蓝容器      Material You 双色调：浅蓝底 + 深蓝图形
  S6 渐变环        蓝→青→薄荷的低对比渐变（仍然平涂，不加光）
  S7 细描边        环与针都收细，留白更多，最"轻"
"""

import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
SS = 3
SIZE = 512
UNIT = 108.0
PX = SIZE * SS / UNIT
CX, CY = 54 * PX, 54 * PX

INK = (32, 33, 36)
INK_DEEP = (12, 13, 15)


def u(v):
    return v * PX


def container(kind="dark"):
    size = SIZE * SS
    if kind == "dark":
        yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
        c = size / 2.0
        r = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / c
        t = np.clip(r, 0, 1) ** 1.15
        rgb = np.array(INK, np.float32)[None, None, :] * (1 - t[..., None]) + \
              np.array(INK_DEEP, np.float32)[None, None, :] * t[..., None]
    else:
        rgb = np.ones((size, size, 3), np.float32) * np.array(kind, np.float32)[None, None, :]
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def arc_mask(r, a0, a1, w):
    m = Image.new("L", (SIZE * SS, SIZE * SS), 0)
    d = ImageDraw.Draw(m)
    d.arc([CX - r - w / 2, CY - r - w / 2, CX + r + w / 2, CY + r + w / 2],
          start=a0, end=a1, fill=255, width=max(1, int(w)))
    for a in (a0, a1):
        x, y = CX + r * math.cos(math.radians(a)), CY + r * math.sin(math.radians(a))
        d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=255)
    return m


def band(img, r, a0, a1, w, colors):
    """colors：单一色或 [(t, rgb), ...] 角度渐变。"""
    mask = arc_mask(r, a0, a1, w)
    if isinstance(colors, tuple):
        rgb = np.broadcast_to(np.array(colors, np.float32)[None, None, :], (SIZE * SS, SIZE * SS, 3))
    else:
        yy, xx = np.mgrid[0:SIZE * SS, 0:SIZE * SS].astype(np.float32)
        ang = (np.degrees(np.arctan2(yy - CY, xx - CX)) + 360.0) % 360.0
        span = (a1 - a0) % 360.0 or 360.0
        t = np.clip(((ang - a0) % 360.0) / span, 0, 1)[..., None]
        out = np.zeros((SIZE * SS, SIZE * SS, 3), np.float32)
        for i in range(len(colors) - 1):
            t0, c0 = colors[i]
            t1, c1 = colors[i + 1]
            sel = (t[..., 0] >= t0) & (t[..., 0] <= t1)
            k = np.clip((t - t0) / max(1e-6, t1 - t0), 0, 1)
            out[sel] = (np.array(c0, np.float32) * (1 - k[sel]) + np.array(c1, np.float32) * k[sel])
        rgb = out
    lay = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    lay.putalpha(mask)
    out = img.copy()
    out.alpha_composite(lay)
    return out


def capsule(img, p0, p1, w, color):
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


def g1_style(track, arc, needle, dotc, w=7.5, nw=4.2, bg="dark", dot_r=5.2):
    img = container(bg)
    r = u(30)
    img = band(img, r, 150, 390, u(w), track)
    img = band(img, r, 150, 335, u(w), arc)
    img = capsule(img, (CX, CY), pt(u(17), -52), u(nw), needle)
    img = dot(img, (CX, CY), u(dot_r), dotc)
    return img


VARIANTS = [
    # key, 名称, 参数
    ("s1", "Google 蓝",
     dict(track=(60, 64, 67), arc=(138, 180, 248), needle=(241, 243, 244), dotc=(66, 133, 244))),
    ("s2", "单色蓝",
     dict(track=(38, 52, 74), arc=(138, 180, 248), needle=(174, 203, 250), dotc=(138, 180, 248),
          nw=4.0, dot_r=4.6)),
    ("s3", "薄荷",
     dict(track=(46, 66, 60), arc=(109, 226, 178), needle=(235, 250, 244), dotc=(45, 178, 122))),
    ("s4", "暖橙",
     dict(track=(70, 54, 44), arc=(255, 178, 116), needle=(255, 240, 226), dotc=(244, 144, 12))),
    ("s5", "浅蓝容器",
     dict(track=(168, 199, 250), arc=(11, 87, 208), needle=(32, 33, 36), dotc=(11, 87, 208),
          bg=(211, 227, 253))),
    ("s6", "渐变环",
     dict(track=(52, 58, 66),
          arc=[(0.0, (138, 180, 248)), (0.55, (109, 213, 237)), (1.0, (109, 226, 178))],
          needle=(236, 250, 255), dotc=(109, 213, 237))),
    ("s7", "细描边",
     dict(track=(56, 60, 66), arc=(138, 180, 248), needle=(241, 243, 244), dotc=(66, 133, 244),
          w=5.2, nw=3.0, dot_r=4.2)),
]


def round_preview(img, size=SIZE):
    img = img.resize((size, size), Image.LANCZOS)
    m = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
    m = m.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    out.paste(img, (0, 0), m)
    return out


def font(px):
    for p in ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/segoeui.ttf"):
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, px, index=0)
            except Exception:
                return ImageFont.truetype(p, px)
    return ImageFont.load_default()


def build():
    tiles = []
    for key, name, kw in VARIANTS:
        img = g1_style(**kw)
        img.resize((SIZE, SIZE), Image.LANCZOS).save(os.path.join(OUT, f"g1-{key}-{name}.png"))
        tiles.append((f"{key.upper()} {name}", round_preview(img)))

    cell, gap, row_gap, bottom = SIZE, 20, 56, 18
    cols = 3
    rows = math.ceil(len(tiles) / cols)
    sheet = Image.new("RGBA", (cols * cell + (cols + 1) * gap,
                               gap + rows * cell + (rows - 1) * row_gap + bottom + 40), (0, 0, 0, 255))
    f = font(26)
    for idx, (label, tile) in enumerate(tiles):
        r_, c_ = divmod(idx, cols)
        x, y = gap + c_ * (cell + gap), gap + r_ * (cell + row_gap)
        sheet.paste(tile, (x, y), tile)
        ImageDraw.Draw(sheet).text((x + 4, y + cell + 4), label, font=f, fill=(190, 200, 214, 255))
    sheet.save(os.path.join(OUT, "sheet-g1-styles.png"))
    print("ok", sheet.size)



def build_sizes():
    """小尺寸可读性预览：48 / 32 / 24 px（约等于 24dp/16dp/12dp 在手表上的观感）。"""
    sizes = [(96, "48dp"), (64, "32dp"), (48, "24dp")]
    pad, row_h, label_w = 14, 96, 165
    cols = len(sizes)
    sheet_w = label_w + cols * (row_h + pad) + pad
    sheet_h = pad + len(VARIANTS) * (row_h + pad) + 30
    sheet = Image.new("RGBA", (sheet_w, sheet_h), (0, 0, 0, 255))
    f = font(22)
    for idx, (key, name, kw) in enumerate(VARIANTS):
        img = g1_style(**kw)
        y = pad + idx * (row_h + pad)
        ImageDraw.Draw(sheet).text((10, y + row_h / 2 - 12), f"{key.upper()} {name}", font=f, fill=(185, 195, 208, 255))
        for j, (px, _) in enumerate(sizes):
            small = round_preview(img, px)
            x = label_w + j * (row_h + pad) + (row_h - px) // 2
            sheet.paste(small, (x, y + (row_h - px) // 2), small)
    sheet.save(os.path.join(OUT, "sheet-g1-sizes.png"))
    print("sizes ok", sheet.size)


if __name__ == "__main__":
    build_sizes()