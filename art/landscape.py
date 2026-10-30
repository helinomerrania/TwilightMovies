"""The twilight landscape Twilight Movies' pictures are drawn on: Twilight TV's navy sky with stars, the sunset
glow on the horizon, two ranges of purple mountains, and still water with the glow's reflection on it.
make_art.py puts the play button and the name on it.
"""
import math
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SKY = [(0.00, (11, 14, 45)), (0.22, (20, 23, 63)), (0.34, (30, 29, 75)), (0.45, (50, 38, 88)),
       (0.55, (95, 56, 98)), (0.63, (120, 66, 100))]
GLOW = (241, 178, 123)
FAR = (84, 58, 103)
NEAR = (42, 32, 70)
WATER = [(0.0, (34, 27, 60)), (0.5, (20, 16, 41)), (1.0, (10, 9, 25))]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(stops, t):
    if t <= stops[0][0]:
        return stops[0][1]
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            return lerp(c0, c1, (t - t0) / (t1 - t0))
    return stops[-1][1]


def vertical(size, stops, top=0.0, bottom=1.0):
    w, h = size
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = top + (bottom - top) * (y / max(1, h - 1))
        col.putpixel((0, y), ramp(stops, t))
    return col.resize((w, h))


def soft_ellipse(size, box, color, alpha, blur):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).ellipse(box, fill=int(255 * alpha))
    m = m.filter(ImageFilter.GaussianBlur(blur))
    layer = Image.new("RGBA", size, color + (0,))
    layer.putalpha(m)
    return layer


def ridge(w, base, amp, seed, peaks):
    """A mountain outline: a few smooth bumps plus small noise."""
    rnd = random.Random(seed)
    phases = [rnd.uniform(0, math.tau) for _ in range(4)]
    pts = []
    for i in range(0, w + 1, max(1, w // 400)):
        x = i / w
        y = 0.0
        for k, (pos, height, width) in enumerate(peaks):
            y += height * math.exp(-((x - pos) / width) ** 2)
        y += 0.08 * math.sin(x * 9 + phases[0]) + 0.05 * math.sin(x * 23 + phases[1])
        y += 0.03 * math.sin(x * 57 + phases[2])
        pts.append((i, base - amp * y))
    return pts


def landscape(W, H, seed=7):
    horizon = int(H * 0.66)
    img = vertical((W, horizon), SKY, 0.0, 0.66).convert("RGBA")
    full = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    full.paste(img, (0, 0))

    # stars, fewer near the glow
    rnd = random.Random(seed)
    stars = Image.new("L", (W, H), 0)
    sd = ImageDraw.Draw(stars)
    for _ in range(int(W * H / 9000)):
        x = rnd.uniform(0, W)
        y = rnd.uniform(0, H * 0.5) ** 1.15 / (H * 0.5) ** 0.15
        r = rnd.choice([0.6, 0.8, 1.0, 1.0, 1.4]) * W / 1280
        a = int(rnd.uniform(60, 200) * (1 - y / (H * 0.55)))
        if a > 0:
            sd.ellipse([x - r, y - r, x + r, y + r], fill=a)
    star_layer = Image.new("RGBA", (W, H), (255, 240, 225, 0))
    star_layer.putalpha(stars)
    full = Image.alpha_composite(full, star_layer)

    # the sunset glow on the horizon
    cx = W * 0.5
    full = Image.alpha_composite(full, soft_ellipse((W, H), [cx - W * 0.45, horizon - H * 0.20, cx + W * 0.45, horizon + H * 0.10],
                                                    (196, 110, 104), 0.55, H * 0.08))
    full = Image.alpha_composite(full, soft_ellipse((W, H), [cx - W * 0.20, horizon - H * 0.09, cx + W * 0.20, horizon + H * 0.04],
                                                    GLOW, 0.85, H * 0.04))

    d = ImageDraw.Draw(full)
    far = ridge(W, horizon - H * 0.005, H * 0.18, seed + 1,
                [(0.08, 0.95, 0.10), (0.30, 0.55, 0.12), (0.78, 0.80, 0.11), (0.97, 1.05, 0.09)])
    d.polygon(far + [(W, horizon + 2), (0, horizon + 2)], fill=FAR + (255,))
    near = ridge(W, horizon + H * 0.01, H * 0.10, seed + 2,
                 [(0.0, 0.9, 0.12), (0.22, 0.4, 0.10), (0.85, 0.75, 0.12), (1.0, 0.9, 0.07)])
    d.polygon(near + [(W, horizon + 2), (0, horizon + 2)], fill=NEAR + (255,))
    # bright rim where the sun sits
    full = Image.alpha_composite(full, soft_ellipse((W, H), [cx - W * 0.12, horizon - H * 0.025, cx + W * 0.12, horizon + H * 0.01],
                                                    (255, 205, 150), 0.65, H * 0.018))

    # still water with the glow's reflection: a soft column, and a few faint ripples on it
    water = vertical((W, H - horizon), WATER).convert("RGBA")
    full.paste(water, (0, horizon))
    refl = Image.new("L", (W, H), 0)
    rd = ImageDraw.Draw(refl)
    steps = 60
    for i in range(steps):
        t = i / (steps - 1)
        y0 = horizon + (H - horizon) * t
        y1 = horizon + (H - horizon) * (t + 1.0 / steps) + 1
        half = W * (0.13 - 0.07 * t)
        rd.rectangle([cx - half, y0, cx + half, y1], fill=int(120 * (1 - t) ** 2.2))
    refl = refl.filter(ImageFilter.GaussianBlur(W / 60))
    ripples = Image.new("L", (W, H), 0)
    pd = ImageDraw.Draw(ripples)
    rnd2 = random.Random(seed + 5)
    for i in range(14):
        t = (i + rnd2.uniform(0.1, 0.9)) / 14
        y = horizon + (H - horizon) * 0.85 * t + H * 0.01
        half = W * (0.10 - 0.06 * t) * rnd2.uniform(0.55, 1.0)
        off = W * rnd2.uniform(-0.02, 0.02)
        pd.line([(cx - half + off, y), (cx + half + off, y)], fill=int(90 * (1 - t) ** 1.5), width=max(1, int(H / 400)))
    ripples = ripples.filter(ImageFilter.GaussianBlur(H / 500))
    refl = ImageChops.add(refl, ripples)
    refl_layer = Image.new("RGBA", (W, H), (222, 156, 124, 0))
    refl_layer.putalpha(refl)
    full = Image.alpha_composite(full, refl_layer)
    # the waterline, brightest under the sun and fading out to the sides
    line = Image.new("L", (W, H), 0)
    ld = ImageDraw.Draw(line)
    for x in range(0, W, max(1, W // 320)):
        a = int(110 * math.exp(-((x - cx) / (W * 0.22)) ** 2))
        ld.line([(x, horizon), (x + max(1, W // 320), horizon)], fill=a, width=max(1, int(H / 400)))
    line_layer = Image.new("RGBA", (W, H), (255, 214, 176, 0))
    line_layer.putalpha(line.filter(ImageFilter.GaussianBlur(0.6)))
    full = Image.alpha_composite(full, line_layer)
    return full
