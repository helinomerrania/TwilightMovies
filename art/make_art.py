"""Draws Twilight Movies' TV banner, launcher icon and start-up picture.

The same twilight landscape as Twilight TV's (landscape.py: navy sky, sunset glow, purple mountains, still
water), with a play button and the name. The name is in Aref Ruqaa Bold, the font Twilight TV already uses
(SIL Open Font License); this script reads it from the Twilight TV repository.

Needs Pillow, fontTools and brotli:  pip install pillow fonttools brotli
Run from the repository:  python3 art/make_art.py [path/to/aref-ruqaa-latin-700.woff2]
It writes the PNGs into app/src/main/res and previews into art/preview (not committed).
"""
import io
import os
import sys

from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from landscape import landscape, soft_ellipse, vertical

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DEFAULT_FONT = os.path.join(ROOT, "..", "twilight-tv-android", "app", "src", "main", "assets", "fonts",
                            "aref-ruqaa-latin-700.woff2")

CREAM = (255, 242, 228)
PEACH_TOP = (255, 196, 140)
PEACH_BOTTOM = (236, 128, 92)
INK = (36, 24, 52)


def load_font(path):
    data = io.BytesIO()
    f = TTFont(path)
    f.flavor = None
    f.save(data)
    return data.getvalue()


def font(data, size):
    return ImageFont.truetype(io.BytesIO(data), size)


def play_disc(size, cx, cy, r):
    """A sunset-coloured disc with a dark play triangle, and a soft glow behind it."""
    W, H = size
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    layer = Image.alpha_composite(layer, soft_ellipse(size, [cx - r * 1.9, cy - r * 1.9, cx + r * 1.9, cy + r * 1.9],
                                                      (255, 160, 110), 0.35, r * 0.7))
    grad = vertical((int(2 * r) + 2, int(2 * r) + 2), [(0, PEACH_TOP), (1, PEACH_BOTTOM)]).convert("RGBA")
    mask = Image.new("L", grad.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, grad.size[0] - 1, grad.size[1] - 1], fill=255)
    disc = Image.new("RGBA", size, (0, 0, 0, 0))
    disc.paste(grad, (int(cx - r), int(cy - r)), mask)
    layer = Image.alpha_composite(layer, disc)
    d = ImageDraw.Draw(layer)
    th = r * 0.95
    tw = th * 0.88
    ox = r * 0.09
    d.polygon([(cx - tw / 2 + ox, cy - th / 2), (cx - tw / 2 + ox, cy + th / 2), (cx + tw / 2 + ox, cy)], fill=INK + (255,))
    return layer


def text_glow(size, draw_fn, blur, color, alpha):
    m = Image.new("L", size, 0)
    draw_fn(ImageDraw.Draw(m), 255)
    m = m.filter(ImageFilter.GaussianBlur(blur))
    m = m.point(lambda v: int(v * alpha))
    layer = Image.new("RGBA", size, color + (0,))
    layer.putalpha(m)
    return layer


def banner(fdata, W=1280, H=720):
    img = landscape(W, H)
    title = "Twilight Movies"
    r = H * 0.095
    gap = H * 0.055
    size = int(H * 0.17)
    while True:   # the largest size at which play button + name fit in 84% of the width
        f = font(fdata, size)
        bx0, by0, bx1, by1 = f.getbbox(title)
        tw, th = bx1 - bx0, by1 - by0
        total = 2 * r + gap + tw
        if total <= W * 0.84 or size <= 20:
            break
        size -= 2
    x0 = (W - total) / 2
    cy = H * 0.36
    img = Image.alpha_composite(img, play_disc((W, H), x0 + r, cy, r))
    tx = x0 + 2 * r + gap - bx0
    ty = cy - th / 2 - by0 - H * 0.012

    def draw_text(d, fill):
        d.text((tx, ty), title, font=f, fill=fill)

    img = Image.alpha_composite(img, text_glow((W, H), draw_text, H * 0.03, (20, 10, 40), 0.85))
    d = ImageDraw.Draw(img)
    d.text((tx, ty), title, font=f, fill=CREAM + (255,))
    return img


def icon(S=768):
    img = landscape(S, S, seed=11)
    img = Image.alpha_composite(img, play_disc((S, S), S / 2, S * 0.40, S * 0.20))
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.18), fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print("wrote", os.path.relpath(path, ROOT), img.size)


def main():
    font_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_FONT
    fdata = load_font(font_path)
    big = banner(fdata)
    for folder, size in (("drawable-xhdpi", (320, 180)), ("drawable-xxhdpi", (480, 270)), ("drawable-xxxhdpi", (640, 360))):
        save(big.resize(size, Image.LANCZOS).convert("RGB"), os.path.join(RES, folder, "banner.png"))
    save(big.convert("RGB"), os.path.join(RES, "drawable-nodpi", "splash_art.png"))
    ic = icon()
    for folder, px in (("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)):
        save(ic.resize((px, px), Image.LANCZOS), os.path.join(RES, folder, "ic_launcher.png"))
    prev = os.path.join(ROOT, "art", "preview")
    save(big.convert("RGB"), os.path.join(prev, "banner_large.png"))
    save(ic.resize((384, 384), Image.LANCZOS), os.path.join(prev, "icon_large.png"))


if __name__ == "__main__":
    main()
