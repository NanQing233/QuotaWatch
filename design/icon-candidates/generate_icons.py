"""
余额表图标候选渲染（设计稿预览）。

质感来自四件事：
  1. 底板径向渐变 + 左上柔光 + 内圈细亮边；
  2. 图形"自发光"：元素单独成层，高斯模糊后叠加；
  3. 弧形用 蒙版 + 线性渐变 生成（不要用一段段线拼，会出梳齿）；
  4. 明暗一律用**不透明**的混色（PIL 画半透明色是覆盖不是混合，会出现灰洞）。

输出 512px 圆形成稿 + 四宫格联系表 sheet.png。
"""

import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
SS = 2
SIZE = 512
UNIT = 108.0
PX = SIZE * SS / UNIT  # 1 设计单位 = PX 像素

CYAN = (124, 224, 255)
VIOLET = (168, 140, 250)
MINT = (110, 231, 183)
INK = (18, 22, 30)
INK_DEEP = (4, 6, 10)


def u(v):
    return v * PX


def mixc(a, b, k):
    """把 a 往 b 混 k（0..1），返回不透明色。"""
    k = max(0.0, min(1.0, k))
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))


def canvas():
    size = SIZE * SS
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    c = size / 2.0
    r = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / c
    t = np.clip(r, 0, 1) ** 1.2
    rgb = np.array(INK, np.float32)[None, None, :] * (1 - t[..., None]) + \
          np.array(INK_DEEP, np.float32)[None, None, :] * t[..., None]
    dl = np.sqrt((xx - size * 0.34) ** 2 + (yy - size * 0.26) ** 2) / (size * 0.66)
    rgb = rgb + np.clip(1 - dl, 0, 1)[..., None] ** 2.4 * np.array([26, 34, 46], np.float32)
    img = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    ImageDraw.Draw(img).ellipse([1, 1, size - 2, size - 2], outline=(130, 160, 190, 34), width=max(1, int(u(0.5))))
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


def grad_band(size, mask, c1, c2, axis="x"):
    """用蒙版 + 线性渐变生成彩色带（alpha 来自蒙版）。"""
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    t = xs / (size - 1) if axis == "x" else ys / (size - 1)
    t = np.clip(t, 0, 1)[..., None]
    rgb = np.array(c1, np.float32)[None, None, :] * (1 - t) + np.array(c2, np.float32)[None, None, :] * t
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    out = Image.fromarray(rgb, "RGB").convert("RGBA")
    out.putalpha(mask)
    return out


def arc_mask(cx, cy, r, a0, a1, w):
    m = Image.new("L", (SIZE * SS, SIZE * SS), 0)
    ImageDraw.Draw(m).arc(
        [cx - r - w / 2, cy - r - w / 2, cx + r + w / 2, cy + r + w / 2],
        start=a0, end=a1, fill=255, width=max(1, int(w)),
    )
    # 圆头
    for a in (a0, a1):
        x = cx + r * math.cos(math.radians(a))
        y = cy + r * math.sin(math.radians(a))
        ImageDraw.Draw(m).ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=255)
    return m


def soft_dot(d, x, y, r, col):
    """实心圆 + 左上不透明高光 + 右下暗边（模拟球面）。"""
    d.ellipse([x - r, y - r, x + r, y + r], fill=mixc(col, (0, 0, 0), 0.18) + (255,))
    d.ellipse([x - r * 0.94, y - r * 0.94, x + r * 0.94, y + r * 0.94], fill=col + (255,))
    hi = mixc(col, (255, 255, 255), 0.55)
    d.ellipse([x - r * 0.66, y - r * 0.80, x + r * 0.24, y - r * 0.08], fill=hi + (255,))


# ---------------- A. 仪表 ----------------
def icon_a():
    img = canvas()
    cx, cy, r = u(54), u(60), u(29)
    pivot = u(66)
    w = u(7.5)

    # 外圈刻度
    ticks = layer()
    td = ImageDraw.Draw(ticks)
    for i in range(13):
        k = i / 12
        a = math.radians(150 + k * 240)
        v = mixc((190, 215, 240), (60, 72, 90), abs(k - 0.5) * 1.6)
        r0, r1 = r + u(6.5), r + u(9.5)
        td.line([(cx + r0 * math.cos(a), cy + r0 * math.sin(a)),
                 (cx + r1 * math.cos(a), cy + r1 * math.sin(a))],
                fill=v + (255,), width=max(1, int(u(0.9))))
    img = composite(img, ticks)

    # 轨道 + 已用量
    img = composite(img, grad_band(SIZE * SS, arc_mask(cx, cy, r, 150, 390, w), (48, 56, 70), (48, 56, 70)))
    used = grad_band(SIZE * SS, arc_mask(cx, cy, r, 150, 336, w), CYAN, VIOLET, axis="y")
    img = composite(img, used, glow_px=u(2.4), glow_alpha=0.85)

    # 指针（锥形）+ 枢轴宝石
    nd = layer()
    ndd = ImageDraw.Draw(nd)
    ang = math.radians(-48)
    tip = (cx + u(16) * math.cos(ang), pivot + u(16) * math.sin(ang))
    nx, ny = math.cos(ang + math.pi / 2), math.sin(ang + math.pi / 2)
    base_w = u(2.2)
    ndd.polygon([(cx + nx * base_w, pivot + ny * base_w),
                 (cx - nx * base_w, pivot - ny * base_w),
                 tip], fill=(232, 244, 255, 255))
    ndd.polygon([(cx, pivot), (tip[0], tip[1]), (cx - nx * base_w, pivot - ny * base_w)],
                fill=mixc((232, 244, 255), (90, 120, 150), 0.55) + (255,))
    img = composite(img, nd, glow_px=u(1.4), glow_alpha=0.5)

    jw = layer()
    jd = ImageDraw.Draw(jw)
    jd.ellipse([cx - u(5.4), pivot - u(5.4), cx + u(5.4), pivot + u(5.4)], fill=(230, 242, 255, 255))
    jd.ellipse([cx - u(3.4), pivot - u(3.4), cx + u(3.4), pivot + u(3.4)], fill=(22, 30, 42, 255))
    jd.ellipse([cx - u(1.6), pivot - u(1.6), cx + u(1.6), pivot + u(1.6)], fill=(150, 215, 250, 255))
    img = composite(img, jw, glow_px=u(2.2), glow_alpha=0.55)
    return img


# ---------------- B. 六家环 ----------------
def icon_b():
    img = canvas()
    cx, cy, r = u(54), u(54), u(25)

    ring = layer()
    ImageDraw.Draw(ring).ellipse([cx - r, cy - r, cx + r, cy + r], outline=(54, 64, 80, 255), width=int(u(2.2)))
    img = composite(img, ring)

    specs = [(6.8, CYAN), (6.0, CYAN), (5.2, MINT), (4.6, MINT), (3.9, VIOLET), (3.2, VIOLET)]
    for i, (dr, col) in enumerate(specs):
        a = math.radians(-90 + i * 60)
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        lay = layer()
        soft_dot(ImageDraw.Draw(lay), x, y, dr * PX, col)
        img = composite(img, lay, glow_px=u(1.8), glow_alpha=0.6)
    return img


# ---------------- C. 柱状凹槽 ----------------
def icon_c():
    img = canvas()

    dish = layer()
    ImageDraw.Draw(dish).ellipse([u(19), u(19), u(89), u(89)], fill=(8, 11, 16, 255))
    img = composite(img, dish)

    rim = layer()
    rd = ImageDraw.Draw(rim)
    rd.arc([u(19), u(19), u(89), u(89)], 150, 330, fill=(104, 120, 142, 210), width=int(u(1.2)))
    rd.arc([u(19), u(19), u(89), u(89)], -30, 150, fill=(255, 255, 255, 26), width=int(u(1.2)))
    img = composite(img, rim)

    base_y = u(75)
    bars = [(u(37), u(19), CYAN), (u(51), u(33), MINT), (u(65), u(47), VIOLET)]
    w = u(9.5)
    for x, h, col in bars:
        # 柱子做成竖向渐变的圆柱：顶部提亮、底部压暗，没有硬分界线
        mask = Image.new("L", (SIZE * SS, SIZE * SS), 0)
        ImageDraw.Draw(mask).rounded_rectangle([x, base_y - h, x + w, base_y], radius=w / 2, fill=255)
        bar = grad_band(SIZE * SS, mask, mixc(col, (255, 255, 255), 0.45),
                        mixc(col, (0, 0, 0), 0.34), axis="y")
        # 只让渐变作用在柱子自身的纵向范围里：重新按柱子区间做一次映射
        ys = np.mgrid[0:SIZE * SS, 0:SIZE * SS][0].astype(np.float32)
        top, bot = base_y - h, base_y
        t = np.clip((ys - top) / max(1.0, (bot - top)), 0, 1)[..., None]
        c1 = np.array(mixc(col, (255, 255, 255), 0.45), np.float32)
        c2 = np.array(mixc(col, (0, 0, 0), 0.34), np.float32)
        rgb = np.clip(c1[None, None, :] * (1 - t) + c2[None, None, :] * t, 0, 255).astype(np.uint8)
        bar = Image.fromarray(rgb, "RGB").convert("RGBA")
        bar.putalpha(mask)
        img = composite(img, bar, glow_px=u(1.6), glow_alpha=0.45)

    floor = layer()
    ImageDraw.Draw(floor).ellipse([u(28), u(73), u(80), u(82)], fill=(120, 200, 255, 46))
    img = composite(img, floor.filter(ImageFilter.GaussianBlur(u(2.6))))
    return img


# ---------------- D. 刻度环 ----------------
def icon_d():
    img = canvas()
    cx, cy, r = u(54), u(54), u(26)

    seg = layer()
    sd = ImageDraw.Draw(seg)
    n = 30
    for i in range(n):
        a = math.radians(-90 + i * (360 / n))
        k = i / (n - 1)
        col = (50, 60, 76) if k < 0.6 else mixc(CYAN, VIOLET, (k - 0.6) / 0.4)
        r0, r1 = r - u(6), r - u(0.6)
        sd.line([(cx + r0 * math.cos(a), cy + r0 * math.sin(a)),
                 (cx + r1 * math.cos(a), cy + r1 * math.sin(a))],
                fill=col + (255,), width=max(1, int(u(2.0))))
    img = composite(img, seg, glow_px=u(2.2), glow_alpha=0.5)

    core = layer()
    cd = ImageDraw.Draw(core)
    cd.ellipse([cx - u(7.6), cy - u(7.6), cx + u(7.6), cy + u(7.6)], outline=(118, 198, 235, 255), width=int(u(2.0)))
    cd.ellipse([cx - u(2.6), cy - u(2.6), cx + u(2.6), cy + u(2.6)], fill=(210, 238, 255, 255))
    img = composite(img, core, glow_px=u(1.8), glow_alpha=0.45)
    return img


def round_preview(img, size=SIZE):
    img = img.resize((size, size), Image.LANCZOS)
    m = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
    m = m.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    out.paste(img, (0, 0), m)
    return out


def font(px):
    for p in ("C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, px)
    return ImageFont.load_default()


def build():
    makers = [("A", icon_a), ("B", icon_b), ("C", icon_c), ("D", icon_d)]
    tiles = []
    for key, fn in makers:
        img = fn()
        img.resize((SIZE, SIZE), Image.LANCZOS).save(os.path.join(OUT, f"icon-{key.lower()}.png"))
        tiles.append((key, round_preview(img)))

    cell, gap = SIZE, 26
    sheet = Image.new("RGBA", (2 * cell + 3 * gap, 2 * cell + 3 * gap + 46), (0, 0, 0, 255))
    f = font(38)
    for idx, (key, tile) in enumerate(tiles):
        r_, c_ = divmod(idx, 2)
        x, y = gap + c_ * (cell + gap), gap + r_ * (cell + gap)
        sheet.paste(tile, (x, y), tile)
        ImageDraw.Draw(sheet).text((x + cell / 2 - 11, y + cell + 4), key, font=f, fill=(185, 195, 210, 255))
    sheet.save(os.path.join(OUT, "sheet.png"))
    print("ok", sheet.size)


if __name__ == "__main__":
    build()