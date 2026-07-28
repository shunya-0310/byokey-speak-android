import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
LOCAL_PACKAGES = ROOT / ".python-packages"
if LOCAL_PACKAGES.exists():
    sys.path.insert(0, str(LOCAL_PACKAGES))

LOTTIE_OUT = ROOT / "app" / "src" / "main" / "assets" / "animations" / "purchase_api_fee_loop.json"
MP4_OUT = ROOT / "docs" / "previews" / "purchase_api_fee_loop.mp4"
POSTER_OUT = ROOT / "docs" / "previews" / "purchase_api_fee_loop_poster.png"

W, H = 960, 540
FPS = 30
OP = 210
DURATION = OP / FPS

INK = "#2A2420"
BURGUNDY = "#7E2430"
GOLD = "#D6A84F"
GOLD_DARK = "#A87824"
PARCHMENT = "#F7EBD2"
PARCHMENT_DEEP = "#E7D0A4"
GREEN = "#2DAA67"
BLUE = "#4C7DFF"
PURPLE = "#8F6BFF"
MINT = "#8FE6D0"
WHITE = "#FFFFFF"


def hex_to_rgba(color, alpha=255):
    color = color.lstrip("#")
    return tuple(int(color[i : i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def rgba01(color):
    color = color.lstrip("#")
    return [int(color[i : i + 2], 16) / 255 for i in (0, 2, 4)]


def text_layer(text, x, y, size, color=INK, name=None):
    return {
        "ddd": 0,
        "ind": 0,
        "ty": 5,
        "nm": name or text,
        "sr": 1,
        "ks": {
            "o": {"a": 0, "k": 100},
            "r": {"a": 0, "k": 0},
            "p": {"a": 0, "k": [x, y, 0]},
            "a": {"a": 0, "k": [0, 0, 0]},
            "s": {"a": 0, "k": [100, 100, 100]},
        },
        "ao": 0,
        "t": {
            "d": {
                "k": [
                    {
                        "s": {
                            "sz": [280, 60],
                            "ps": [0, 0],
                            "s": size,
                            "f": "Arial",
                            "t": text,
                            "j": 2,
                            "tr": 0,
                            "lh": size * 1.2,
                            "ls": 0,
                            "fc": rgba01(color),
                        },
                        "t": 0,
                    }
                ]
            },
            "p": {},
            "m": {"g": 1, "a": {"a": 0, "k": [0, 0]}},
            "a": [],
        },
        "ip": 0,
        "op": OP,
        "st": 0,
        "bm": 0,
    }


def transform(x=0, y=0, scale=100, opacity=100, pos_keys=None, opacity_keys=None):
    p = {"a": 0, "k": [x, y, 0]} if pos_keys is None else {"a": 1, "k": pos_keys}
    o = {"a": 0, "k": opacity} if opacity_keys is None else {"a": 1, "k": opacity_keys}
    return {
        "o": o,
        "r": {"a": 0, "k": 0},
        "p": p,
        "a": {"a": 0, "k": [0, 0, 0]},
        "s": {"a": 0, "k": [scale, scale, 100]},
    }


def key(t, s, e=None, hold=False):
    item = {"t": t, "s": s if isinstance(s, list) else [s]}
    if e is not None:
        item["e"] = e if isinstance(e, list) else [e]
        item["i"] = {"x": [0.65], "y": [1]}
        item["o"] = {"x": [0.35], "y": [0]}
    if hold:
        item["h"] = 1
    return item


def pos_key(t, s, e=None, hold=False):
    ss = [s[0], s[1], 0]
    ee = None if e is None else [e[0], e[1], 0]
    return key(t, ss, ee, hold)


def fill(color):
    return {"ty": "fl", "c": {"a": 0, "k": rgba01(color) + [1]}, "o": {"a": 0, "k": 100}, "r": 1}


def stroke(color, width=4, opacity=100):
    return {
        "ty": "st",
        "c": {"a": 0, "k": rgba01(color) + [1]},
        "o": {"a": 0, "k": opacity},
        "w": {"a": 0, "k": width},
        "lc": 2,
        "lj": 2,
        "ml": 4,
    }


def rect(w, h, r=0, x=0, y=0):
    return {"ty": "rc", "p": {"a": 0, "k": [x, y]}, "s": {"a": 0, "k": [w, h]}, "r": {"a": 0, "k": r}}


def ellipse(w, h, x=0, y=0):
    return {"ty": "el", "p": {"a": 0, "k": [x, y]}, "s": {"a": 0, "k": [w, h]}}


def path(points, closed=True):
    return {
        "ty": "sh",
        "ks": {
            "a": 0,
            "k": {
                "i": [[0, 0] for _ in points],
                "o": [[0, 0] for _ in points],
                "v": points,
                "c": closed,
            },
        },
    }


def group(name, items):
    return {"ty": "gr", "nm": name, "it": items + [{"ty": "tr", "p": {"a": 0, "k": [0, 0]}, "a": {"a": 0, "k": [0, 0]}, "s": {"a": 0, "k": [100, 100]}, "r": {"a": 0, "k": 0}, "o": {"a": 0, "k": 100}}]}


def shape_layer(name, groups, x=0, y=0, scale=100, pos_keys=None, opacity_keys=None):
    return {
        "ddd": 0,
        "ind": 0,
        "ty": 4,
        "nm": name,
        "sr": 1,
        "ks": transform(x=x, y=y, scale=scale, pos_keys=pos_keys, opacity_keys=opacity_keys),
        "ao": 0,
        "shapes": groups,
        "ip": 0,
        "op": OP,
        "st": 0,
        "bm": 0,
    }


def user_icon():
    return [
        group(
            "user",
            [
                ellipse(86, 86),
                fill(PARCHMENT_DEEP),
                stroke(BURGUNDY, 5),
                ellipse(30, 30, 0, -16),
                fill(BURGUNDY),
                ellipse(54, 34, 0, 28),
                fill(BURGUNDY),
            ],
        )
    ]


def play_store_icon():
    return [
        group(
            "play-store",
            [
                rect(84, 84, 18),
                fill(WHITE),
                stroke(GREEN, 5),
                path([[-18, -26], [28, 0], [-18, 26]]),
                fill(GREEN),
            ],
        )
    ]


def game_controller():
    return [
        group(
            "game-controller-app",
            [
                rect(94, 58, 22),
                fill(INK),
                stroke(GOLD, 4),
                rect(24, 7, 2, -23, 0),
                fill(WHITE),
                rect(7, 24, 2, -23, 0),
                fill(WHITE),
                ellipse(10, 10, 22, -8),
                fill(GREEN),
                ellipse(10, 10, 38, 8),
                fill(BLUE),
                ellipse(18, 18, 0, -26),
                fill(BURGUNDY),
            ],
        )
    ]


def money_bill():
    return [
        group(
            "dollar-bill",
            [
                rect(92, 48, 9),
                fill(GREEN),
                stroke(WHITE, 3),
                ellipse(34, 24),
                stroke(WHITE, 3),
                path([[-4, -15], [2, -15], [2, 15], [-4, 15]], False),
                stroke(WHITE, 4),
            ],
        )
    ]


def coin():
    return [
        group(
            "api-coin",
            [
                ellipse(38, 38),
                fill(GOLD),
                stroke(GOLD_DARK, 4),
                path([[0, -11], [4, -3], [13, -3], [6, 2], [9, 12], [0, 6], [-9, 12], [-6, 2], [-13, -3], [-4, -3]]),
                fill(WHITE),
            ],
        )
    ]


def provider_icon():
    return [
        group(
            "provider",
            [
                rect(122, 104, 22),
                fill(WHITE),
                stroke(PURPLE, 5),
                path([[0, -34], [8, -10], [34, 0], [8, 10], [0, 34], [-8, 10], [-34, 0], [-8, -10]]),
                fill(PURPLE),
                ellipse(48, 48, 30, 24),
                stroke(MINT, 5),
                path([[12, 24], [54, 24]], False),
                stroke(MINT, 5),
            ],
        )
    ]


def make_lottie():
    app_keys = [pos_key(0, (142, 88), (226, 330)), pos_key(50, (226, 330), (226, 330), True), pos_key(OP - 1, (226, 330))]
    bill_keys = [pos_key(0, (150, 360), (142, 88)), pos_key(50, (142, 88), (142, 88), True), pos_key(OP - 1, (142, 88))]
    bill_opacity = [key(0, 100, 100), key(48, 100, 0), key(55, 0, 0), key(OP - 1, 0)]

    layers = [
        shape_layer("background", [group("bg", [rect(W, H, 0), fill(PARCHMENT)])], W / 2, H / 2),
        shape_layer("one-time-payment-line", [group("line", [path([[150, 340], [142, 112]], False), stroke(GOLD_DARK, 3, 45)])]),
        shape_layer("api-fee-line", [group("line", [path([[196, 360], [748, 360]], False), stroke(GOLD_DARK, 3, 45)])]),
        shape_layer("play-store", play_store_icon(), 142, 88),
        text_layer("Play Store", 142, 158, 22, GREEN, "play-store-label"),
        shape_layer("user", user_icon(), 150, 360),
        text_layer("User", 150, 438, 24, BURGUNDY, "user-label"),
        shape_layer("provider", provider_icon(), 810, 360),
        text_layer("Gemini / ChatGPT", 810, 438, 22, PURPLE, "provider-label"),
        shape_layer("app-icon-arrives", game_controller(), pos_keys=app_keys),
        shape_layer("one-time-money", money_bill(), pos_keys=bill_keys, opacity_keys=bill_opacity),
    ]

    for idx, start in enumerate([62, 106, 150], start=1):
        coin_keys = [
            pos_key(start, (198, 360), (810, 360)),
            pos_key(start + 34, (810, 360), (810, 360), True),
            pos_key(start + 42, (810, 360), (810, 360), True),
            pos_key(OP - 1, (810, 360)),
        ]
        opacity_keys = [key(0, 0, 0), key(start, 100, 100), key(start + 34, 100, 0), key(start + 42, 0, 0), key(OP - 1, 0)]
        layers.append(shape_layer(f"api-coin-{idx}", coin(), pos_keys=coin_keys, opacity_keys=opacity_keys))

    layers.extend(
        [
            text_layer("One-time app purchase", 310, 116, 24, BURGUNDY, "purchase-label"),
            text_layer("API usage fee repeats monthly", 480, 316, 24, BURGUNDY, "api-label"),
            text_layer("1", 366, 378, 20, GOLD_DARK, "coin-count-1"),
            text_layer("2", 480, 378, 20, GOLD_DARK, "coin-count-2"),
            text_layer("3", 594, 378, 20, GOLD_DARK, "coin-count-3"),
        ]
    )

    for i, layer in enumerate(reversed(layers), start=1):
        layer["ind"] = i

    data = {
        "v": "5.10.2",
        "fr": FPS,
        "ip": 0,
        "op": OP,
        "w": W,
        "h": H,
        "nm": "BYOKey purchase and API fee loop",
        "ddd": 0,
        "assets": [],
        "layers": list(reversed(layers)),
        "markers": [],
    }
    LOTTIE_OUT.parent.mkdir(parents=True, exist_ok=True)
    LOTTIE_OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def load_font(size, bold=False):
    candidates = [
        "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
        "C:/Windows/Fonts/meiryo.ttc",
    ]
    for candidate in candidates:
        if Path(candidate).exists():
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


def ease(t):
    return 3 * t * t - 2 * t * t * t


def interp(a, b, t):
    t = max(0.0, min(1.0, t))
    e = ease(t)
    return a[0] + (b[0] - a[0]) * e, a[1] + (b[1] - a[1]) * e


def draw_text_center(draw, text, xy, font, fill):
    bbox = draw.textbbox((0, 0), text, font=font)
    x = xy[0] - (bbox[2] - bbox[0]) / 2
    y = xy[1] - (bbox[3] - bbox[1]) / 2
    draw.text((x, y), text, font=font, fill=fill)


def draw_user(draw, x, y):
    draw.ellipse((x - 43, y - 43, x + 43, y + 43), fill=hex_to_rgba(PARCHMENT_DEEP), outline=hex_to_rgba(BURGUNDY), width=5)
    draw.ellipse((x - 15, y - 31, x + 15, y - 1), fill=hex_to_rgba(BURGUNDY))
    draw.ellipse((x - 27, y + 9, x + 27, y + 43), fill=hex_to_rgba(BURGUNDY))


def draw_play(draw, x, y):
    draw.rounded_rectangle((x - 42, y - 42, x + 42, y + 42), radius=18, fill=hex_to_rgba(WHITE), outline=hex_to_rgba(GREEN), width=5)
    draw.polygon([(x - 18, y - 26), (x + 28, y), (x - 18, y + 26)], fill=hex_to_rgba(GREEN))


def draw_controller(draw, x, y):
    draw.rounded_rectangle((x - 47, y - 29, x + 47, y + 29), radius=22, fill=hex_to_rgba(INK), outline=hex_to_rgba(GOLD), width=4)
    draw.rectangle((x - 35, y - 4, x - 11, y + 4), fill=hex_to_rgba(WHITE))
    draw.rectangle((x - 27, y - 12, x - 19, y + 12), fill=hex_to_rgba(WHITE))
    draw.ellipse((x + 17, y - 13, x + 27, y - 3), fill=hex_to_rgba(GREEN))
    draw.ellipse((x + 33, y + 3, x + 43, y + 13), fill=hex_to_rgba(BLUE))
    draw.ellipse((x - 9, y - 35, x + 9, y - 17), fill=hex_to_rgba(BURGUNDY))


def draw_bill(draw, x, y, alpha=255):
    draw.rounded_rectangle((x - 46, y - 24, x + 46, y + 24), radius=9, fill=hex_to_rgba(GREEN, alpha), outline=hex_to_rgba(WHITE, alpha), width=3)
    draw.ellipse((x - 17, y - 12, x + 17, y + 12), outline=hex_to_rgba(WHITE, alpha), width=3)
    draw.line((x - 3, y - 15, x - 3, y + 15), fill=hex_to_rgba(WHITE, alpha), width=4)
    draw.line((x + 3, y - 15, x + 3, y + 15), fill=hex_to_rgba(WHITE, alpha), width=2)


def draw_coin(draw, x, y, alpha=255):
    draw.ellipse((x - 19, y - 19, x + 19, y + 19), fill=hex_to_rgba(GOLD, alpha), outline=hex_to_rgba(GOLD_DARK, alpha), width=4)
    pts = [(0, -11), (4, -3), (13, -3), (6, 2), (9, 12), (0, 6), (-9, 12), (-6, 2), (-13, -3), (-4, -3)]
    draw.polygon([(x + px, y + py) for px, py in pts], fill=hex_to_rgba(WHITE, alpha))


def draw_provider(draw, x, y):
    draw.rounded_rectangle((x - 61, y - 52, x + 61, y + 52), radius=22, fill=hex_to_rgba(WHITE), outline=hex_to_rgba(PURPLE), width=5)
    pts = [(0, -34), (8, -10), (34, 0), (8, 10), (0, 34), (-8, 10), (-34, 0), (-8, -10)]
    draw.polygon([(x + px, y + py) for px, py in pts], fill=hex_to_rgba(PURPLE))
    draw.ellipse((x + 6, y, x + 54, y + 48), outline=hex_to_rgba(MINT), width=5)
    draw.line((x + 12, y + 24, x + 54, y + 24), fill=hex_to_rgba(MINT), width=5)


def draw_frame(frame):
    img = Image.new("RGBA", (W, H), hex_to_rgba(PARCHMENT))
    draw = ImageDraw.Draw(img)
    font_small = load_font(22)
    font_med = load_font(24, bold=True)
    font_big = load_font(30, bold=True)

    draw.line((150, 340, 142, 112), fill=hex_to_rgba(GOLD_DARK, 110), width=3)
    draw.line((196, 360, 748, 360), fill=hex_to_rgba(GOLD_DARK, 110), width=3)

    draw_play(draw, 142, 88)
    draw_text_center(draw, "Play Store", (142, 158), font_small, hex_to_rgba(GREEN))
    draw_user(draw, 150, 360)
    draw_text_center(draw, "User", (150, 438), font_med, hex_to_rgba(BURGUNDY))
    draw_provider(draw, 810, 360)
    draw_text_center(draw, "Gemini / ChatGPT", (810, 438), font_small, hex_to_rgba(PURPLE))

    draw_text_center(draw, "One-time app purchase", (310, 116), font_med, hex_to_rgba(BURGUNDY))
    draw_text_center(draw, "API usage fee repeats monthly", (480, 316), font_med, hex_to_rgba(BURGUNDY))
    for x, n in [(366, "1"), (480, "2"), (594, "3")]:
        draw_text_center(draw, n, (x, 378), font_small, hex_to_rgba(GOLD_DARK))

    if frame <= 50:
        x, y = interp((142, 88), (226, 330), frame / 50)
    else:
        x, y = (226, 330)
    draw_controller(draw, x, y)

    if frame <= 50:
        x, y = interp((150, 360), (142, 88), frame / 50)
        alpha = int(255 * (1 - max(0, frame - 42) / 8))
        draw_bill(draw, x, y, max(0, alpha))

    for start in [62, 106, 150]:
        if start <= frame <= start + 42:
            x, y = interp((198, 360), (810, 360), (frame - start) / 34)
            alpha = 255 if frame <= start + 34 else int(255 * (1 - (frame - start - 34) / 8))
            draw_coin(draw, x, y, max(0, alpha))

    return img.convert("RGB")


def make_mp4():
    MP4_OUT.parent.mkdir(parents=True, exist_ok=True)
    frames = [draw_frame(i) for i in range(OP)]
    frames[70].save(POSTER_OUT)
    try:
        import imageio.v2 as imageio
        import numpy as np

        writer = imageio.get_writer(MP4_OUT, fps=FPS, codec="libx264", quality=8, macro_block_size=1)
        for frame in frames:
            writer.append_data(np.asarray(frame))
        writer.close()
    except Exception as exc:
        raise RuntimeError("MP4 encoding requires imageio and imageio-ffmpeg") from exc


if __name__ == "__main__":
    make_lottie()
    make_mp4()
    print(f"Lottie: {LOTTIE_OUT}")
    print(f"MP4: {MP4_OUT}")
    print(f"Poster: {POSTER_OUT}")
