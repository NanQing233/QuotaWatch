"""
A 方案（仪表）的材质变体：同一套造型，五种质感。

  A1 玻璃   半透明弧 + 内壁反光 + 大面柔光，像一块弧形玻璃
  A2 金属   多段明暗渐变模拟各向异性拉丝，边缘有亮棱/暗棱
  A3 霓虹   宽彩光 + 细白芯 + 强辉光，发光管效果
  A4 哑光   去高光、去辉光，加细颗粒与顶部内阴影，像硅胶/磨砂
  A5 液态   角度渐变（青→紫→粉）+ 斜向高光扫过，像镜面球

所有渐变都走「角度/线性映射 + 蒙版」的 numpy 路线，不用半透明色覆盖，
避免 PIL 直接写像素导致的灰洞。
"""

import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
SS = 2
SIZE = 512
UNIT = 108.0
PX = SIZE * SS / UNIT

CYAN = (124, 224, 255)
VIOLET = (168, 140, 250)
PINK = (250, 160, 220)
INK = (18, 22, 30)
INK_DEEP = (4, 6, 10)

CX, CY, R = 54 * PX, 60 * PX, 29 * PX
PIVOT = 66 * PX


def u(v):
    return v * PX


def canvas():
    size = SIZE * SS
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    c = size / 2.0
    r = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / c
    t = np.clip(r, 0, 1) ** 1.2
    rgb = np.array(INK, np.float32)[None, None, :] * (1 - t[..., None]) + \
          np.array(INK_DEEP, np.float32)[None, None, :] * t[..., None]
    dl = np.sqrt((xx - size * 0.34) ** 2 + (yy - size * 0.26) ** 2) / (size * 0.66)
    rgb += np.clip(1 - dl, 0, 1)[..., None] ** 2.4 * np.array([26, 34, 46], np.float32)
    img = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    ImageDraw.Draw(img).ellipse([1, 1, size - 2, size - 2], outline=(130, 160, 190, 34), width=max(1, int(u(0.45))))
    return img


def layer():
    return Image.new("RGBA", (SIZE * SS, SIZE * SS), (0, 0, 0, 0))


def composite(base, lay, glow_px=0.0, glow_alpha=1.0):
    out = base
    if glow_px > 0:
        g = lay.filter(ImageFilter.GaussianBlur(glow_px))
        if glow_alpha != 1.0:
            g.putalpha(g.split()[3].point(lambda v: int(v * glow_alpha)))
        out = out.copy()
        out.alpha_composite(g)
    out = out.copy()
    out.alpha_composite(lay)
    return out


def arc_mask(r, a0, a1, w, centered=True):
    """环形带蒙版。centered=True 时 r 是中心线半径。"""
    m = Image.new("L", (SIZE * SS, SIZE * SS), 0)
    box = [CX - r - w / 2, CY - r - w / 2, CX + r + w / 2, CY + r + w / 2]
    ImageDraw.Draw(m).arc(box, start=a0, end=a1, fill=255, width=max(1, int(w)))
    for a in (a0, a1):
        x, y = CX + r * math.cos(math.radians(a)), CY + r * math.sin(math.radians(a))
        ImageDraw.Draw(m).ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=255)
    return m


def angular_rgb(stops, a0, a1):
    """按角度做分段渐变，返回 HxWx3 数组。stops=[(t, (r,g,b)), ...]。"""
    size = SIZE * SS
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    ang = (np.degrees(np.arctan2(yy - CY, xx - CX)) + 360.0) % 360.0
    span = (a1 - a0) % 360.0 or 360.0
    t = np.clip(((ang - a0) % 360.0) / span, 0, 1)
    out = np.zeros((size, size, 3), np.float32)
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]
        t1, c1 = stops[i + 1]
        sel = (t >= t0) & (t <= t1)
        k = np.clip((t - t0) / max(1e-6, (t1 - t0)), 0, 1)[..., None]
        out[sel] = (np.array(c0, np.float32) * (1 - k[sel]) + np.array(c1, np.float32) * k[sel])
    return out


def arc_layer(stops, a0, a1, w, r=R):
    mask = arc_mask(r, a0, a1, w)
    rgb = angular_rgb(stops, a0, a1)
    img = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    img.putalpha(mask)
    return img


def linear_overlay(mask, c1, c2, axis="y", k0=0.0, k1=1.0):
    size = SIZE * SS
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    base = ys if axis == "y" else xs
    lo, hi = base.min(), base.max()
    t = np.clip((base - lo) / (hi - lo), 0, 1)
    t = np.clip((t - k0) / max(1e-6, (k1 - k0)), 0, 1)[..., None]
    rgb = np.array(c1, np.float32)[None, None, :] * (1 - t) + np.array(c2, np.float32)[None, None, :] * t
    img = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    img.putalpha(mask)
    return img


def grain(img, amount=2.4):
    """细颗粒：只作用在圆内，幅度极小，缩图后是"磨砂感"而不是噪点。"""
    arr = np.array(img, np.float32)
    rng = np.random.default_rng(7)
    noise = rng.normal(0, amount, (arr.shape[0], arr.shape[1], 1))
    rgb = np.clip(arr[..., :3] + noise, 0, 255)
    out = np.dstack([rgb, arr[..., 3]]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def ticks_layer(alpha=1.0):
    lay = layer()
    d = ImageDraw.Draw(lay)
    for i in range(13):
        k = i / 12
        a = math.radians(150 + k * 240)
        shade = int(70 + 130 * (1 - abs(k - 0.5) * 2))
        r0, r1 = R + u(6.5), R + u(9.5)
        d.line([(CX + r0 * math.cos(a), CY + r0 * math.sin(a)),
                (CX + r1 * math.cos(a), CY + r1 * math.sin(a))],
               fill=(int(190 * alpha), int(215 * alpha), int(240 * alpha), 255),
               width=max(1, int(u(0.9) * (0.5 + shade / 200.0))))
    return lay


def needle_layer(body=(232, 244, 255), shade=(96, 124, 155), width=2.2, glow=1.4):
    nd = layer()
    d = ImageDraw.Draw(nd)
    ang = math.radians(-48)
    tip = (CX + u(16) * math.cos(ang), PIVOT + u(16) * math.sin(ang))
    nx, ny = math.cos(ang + math.pi / 2), math.sin(ang + math.pi / 2)
    bw = u(width)
    d.polygon([(CX + nx * bw, PIVOT + ny * bw), (CX - nx * bw, PIVOT - ny * bw), tip], fill=body + (255,))
    d.polygon([(CX, PIVOT), tip, (CX - nx * bw, PIVOT - ny * bw)], fill=shade + (255,))
    return nd, glow


def jewel_layer(ring=(230, 242, 255), inner=(22, 30, 42), dot=(150, 215, 250), glow=2.2):
    jw = layer()
    d = ImageDraw.Draw(jw)
    d.ellipse([CX - u(5.4), PIVOT - u(5.4), CX + u(5.4), PIVOT + u(5.4)], fill=ring + (255,))
    d.ellipse([CX - u(3.4), PIVOT - u(3.4), CX + u(3.4), PIVOT + u(3.4)], fill=inner + (255,))
    d.ellipse([CX - u(1.6), PIVOT - u(1.6), CX + u(1.6), PIVOT + u(1.6)], fill=dot + (255,))
    return jw, glow


def base_track(width=7.5, color=(48, 56, 70)):
    return arc_layer([(0, color), (1, color)], 150, 390, u(width))


# ---------------- A1 玻璃 ----------------
def a1_glass():
    img = canvas()
    w = u(8.0)

    # 背后的“透光”晕：先铺一层更宽更淡的同等弧
    halo = arc_layer([(0, (70, 120, 160)), (1, (120, 96, 170))], 150, 336, u(13))
    img = composite(img, halo, glow_px=u(5.0), glow_alpha=0.16)

    body = arc_layer([(0, (150, 226, 255)), (0.5, (180, 200, 255)), (1, (206, 170, 255))], 150, 336, w)
    img = composite(img, base_track(7.2))
    img = composite(img, body, glow_px=u(1.8), glow_alpha=0.55)

    # 内壁与上缘反光：细亮弧
    inner = arc_layer([(0, (235, 250, 255)), (1, (240, 225, 255))], 150, 336, u(1.6), r=R - u(3.0))
    img = composite(img, inner, glow_px=u(1.0), glow_alpha=0.5)
    outer = arc_layer([(0, (255, 255, 255)), (1, (235, 230, 255))], 150, 336, u(1.2), r=R + u(3.2))
    img = composite(img, outer, glow_px=u(0.8), glow_alpha=0.45)

    # 弧内窄高光（玻璃反光，只落在弧带上）
    glass_spec = arc_mask(R, 150, 320, u(8.0))
    glass_spec = scale_alpha(linear_overlay(glass_spec, (255, 255, 255), (200, 230, 255)), 0.30)
    img = composite(img, glass_spec, glow_px=u(0.8), glow_alpha=0.7)

    img = composite(img, ticks_layer(0.55))
    nd, g = needle_layer(width=2.0)
    img = composite(img, nd, glow_px=u(g), glow_alpha=0.45)
    jw, g = jewel_layer(glow=1.8)
    img = composite(img, jw, glow_px=u(g), glow_alpha=0.5)
    return grain(img, 1.6)


# ---------------- A2 金属 ----------------
def a2_metal():
    img = canvas()
    steel = (196, 214, 232)
    steel_d = (58, 72, 90)
    # 拉丝：多段明暗，模拟各向异性高光
    stops = [(0.0, steel_d), (0.18, steel), (0.34, steel_d), (0.5, (222, 236, 250)),
             (0.66, steel), (0.84, steel_d), (1.0, (150, 170, 192))]
    img = composite(img, base_track(7.6))
    body = arc_layer(stops, 150, 336, u(8.0))
    img = composite(img, body, glow_px=u(1.2), glow_alpha=0.35)

    # 上下棱线：上亮下暗，做出“圆管翻转”的体积
    img = composite(img, arc_layer([(0, (255, 255, 255)), (1, (255, 255, 255))], 150, 336, u(1.3), r=R - u(3.1)))
    img = composite(img, arc_layer([(0, (24, 30, 40)), (1, (24, 30, 40))], 150, 336, u(1.6), r=R + u(3.1)))

    img = composite(img, ticks_layer(0.9))
    nd, g = needle_layer(body=(240, 246, 252), shade=(120, 136, 158), width=2.4)
    img = composite(img, nd, glow_px=u(0.9), glow_alpha=0.3)
    jw, g = jewel_layer(ring=(226, 234, 244), inner=(30, 36, 46), dot=(200, 220, 240), glow=1.0)
    img = composite(img, jw, glow_px=u(g), glow_alpha=0.3)
    return grain(img, 3.0)


# ---------------- A3 霓虹 ----------------
def a3_neon():
    img = canvas()
    col_stops = [(0.0, CYAN), (0.55, VIOLET), (1.0, PINK)]

    # 外辉光（宽、淡、大模糊）
    wide = arc_layer(col_stops, 150, 336, u(14))
    img = composite(img, wide, glow_px=u(6.5), glow_alpha=0.55)
    # 灯管本体
    tube = arc_layer(col_stops, 150, 336, u(7.0))
    img = composite(img, tube, glow_px=u(3.0), glow_alpha=0.9)
    # 白芯
    core = arc_layer([(0, (245, 252, 255)), (1, (255, 246, 252))], 150, 336, u(1.3))
    img = composite(img, core, glow_px=u(1.4), glow_alpha=0.85)

    img = composite(img, ticks_layer(0.4))
    nd, g = needle_layer(body=(235, 250, 255), shade=(90, 180, 220), width=1.8)
    img = composite(img, nd, glow_px=u(2.4), glow_alpha=0.75)
    jw, g = jewel_layer(glow=3.0)
    img = composite(img, jw, glow_px=u(g), glow_alpha=0.8)
    return grain(img, 1.2)


# ---------------- A4 哑光 ----------------
def a4_matte():
    img = canvas()
    # 整体降低对比：去发光、去亮棱
    soft = [(0.0, (110, 190, 220)), (0.5, (140, 160, 215)), (1.0, (170, 150, 215))]
    img = composite(img, base_track(7.4, color=(38, 44, 56)))
    body = arc_layer(soft, 150, 336, u(7.4))
    img = composite(img, body)

    # 极淡的上缘提亮（1/3 强度）+ 顶部内阴影
    img = composite(img, arc_layer([(0, (196, 226, 244)), (1, (206, 196, 240))], 150, 336, u(1.0), r=R - u(2.9)))
    shade = Image.new("L", (SIZE * SS, SIZE * SS), 0)
    ImageDraw.Draw(shade).ellipse([u(2), u(-26), u(106), u(46)], fill=120)
    img = composite(img, linear_overlay(shade.filter(ImageFilter.GaussianBlur(u(7))), (0, 0, 0), (0, 0, 0)))

    img = composite(img, ticks_layer(0.35))
    nd, g = needle_layer(body=(214, 226, 238), shade=(120, 132, 148), width=2.0)
    img = composite(img, nd)
    jw, g = jewel_layer(ring=(206, 218, 230), inner=(30, 36, 46), dot=(150, 190, 215), glow=0.0)
    img = composite(img, jw)
    return grain(img, 3.2)


# ---------------- A5 液态镜面 ----------------
def a5_liquid():
    img = canvas()
    stops = [(0.0, CYAN), (0.42, VIOLET), (0.72, PINK), (1.0, (150, 210, 255))]
    img = composite(img, base_track(7.4))
    body = arc_layer(stops, 150, 336, u(8.2))
    img = composite(img, body, glow_px=u(2.2), glow_alpha=0.6)

    # 弧内斜向高光（模拟液态金属的流动反射）
    mask = arc_mask(R, 150, 336, u(8.2))
    grad = Image.new("L", (SIZE * SS, SIZE * SS), 0)
    ImageDraw.Draw(grad).polygon([(u(10), u(22)), (u(32), u(8)), (u(94), u(44)), (u(72), u(60))], fill=255)
    grad = grad.filter(ImageFilter.GaussianBlur(u(4)))
    spec = linear_overlay(Image.composite(mask, Image.new("L", mask.size, 0), grad), (255, 255, 255), (210, 235, 255))
    img = composite(img, scale_alpha(spec, 0.15), glow_px=u(0.6), glow_alpha=0.9)

    img = composite(img, arc_layer([(0, (255, 255, 255)), (1, (255, 255, 255))], 150, 336, u(0.9), r=R - u(3.0)))
    img = composite(img, ticks_layer(0.5))
    nd, g = needle_layer(body=(240, 250, 255), shade=(110, 150, 190), width=2.0)
    img = composite(img, nd, glow_px=u(1.2), glow_alpha=0.5)
    jw, g = jewel_layer(glow=2.0)
    img = composite(img, jw, glow_px=u(g), glow_alpha=0.6)
    return grain(img, 1.8)


def round_preview(img, size=SIZE):
    img = img.resize((size, size), Image.LANCZOS)
    m = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
    m = m.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    out.paste(img, (0, 0), m)
    return out


def font(px):
    for p in ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf", "C:/Windows/Fonts/segoeui.ttf"):
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, px, index=0)
            except Exception:
                return ImageFont.truetype(p, px)
    return ImageFont.load_default()


def scale_alpha(img, k):
    out = img.copy()
    out.putalpha(out.split()[3].point(lambda v: int(v * k)))
    return out


def build():
    variants = [
        ("a1", "玻璃", a1_glass),
        ("a2", "金属", a2_metal),
        ("a3", "霓虹", a3_neon),
        ("a4", "哑光", a4_matte),
        ("a5", "液态", a5_liquid),
    ]
    tiles = []
    for key, name, fn in variants:
        img = fn()
        img.resize((SIZE, SIZE), Image.LANCZOS).save(os.path.join(OUT, f"a-{key}-{name}.png"))
        tiles.append((f"A{key[1]} {name}", round_preview(img)))

    cell, gap, row_gap, bottom = SIZE, 24, 64, 16
    cols, rows = 3, 2
    sheet_w = cols * cell + (cols + 1) * gap
    sheet_h = gap + rows * cell + (rows - 1) * row_gap + 48
    sheet = Image.new("RGBA", (sheet_w, sheet_h), (0, 0, 0, 255))
    f = font(30)
    for idx, (label, tile) in enumerate(tiles):
        r_, c_ = divmod(idx, cols)
        x = gap + c_ * (cell + gap)
        y = gap + r_ * (cell + row_gap)
        sheet.paste(tile, (x, y), tile)
        ImageDraw.Draw(sheet).text((x + 6, y + cell + 8), label, font=f, fill=(190, 200, 214, 255))
    sheet.save(os.path.join(OUT, "sheet-a.png"))
    print("ok", sheet.size)


if __name__ == "__main__":
    build()