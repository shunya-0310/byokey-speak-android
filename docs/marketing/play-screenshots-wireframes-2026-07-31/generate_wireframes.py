from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math
import random


ROOT = Path(__file__).resolve().parent
ASSETS = ROOT.parent / "feature-graphic-2026-07-30" / "assets"
OUT = ROOT

W, H = 1080, 1920

NAVY = (8, 20, 35)
NAVY_2 = (16, 35, 58)
PANEL = (29, 51, 80)
GOLD = (186, 149, 67)
GOLD_SOFT = (216, 194, 125)
CORAL = (226, 148, 132)
INK = (245, 239, 222)
MUTED = (201, 190, 166)
LINE = (146, 118, 54)

FONT_SANS = "C:/Windows/Fonts/NotoSansJP-VF.ttf"
FONT_SERIF = "C:/Windows/Fonts/NotoSerifJP-VF.ttf"


def font(path, size):
    return ImageFont.truetype(path, size)


F_SERIF_32 = font(FONT_SERIF, 32)
F_SERIF_54 = font(FONT_SERIF, 54)
F_SERIF_72 = font(FONT_SERIF, 72)
F_SANS_28 = font(FONT_SANS, 28)
F_SANS_34 = font(FONT_SANS, 34)
F_SANS_40 = font(FONT_SANS, 40)
F_SANS_46 = font(FONT_SANS, 46)
F_SANS_54 = font(FONT_SANS, 54)
F_SANS_58 = font(FONT_SANS, 58)
F_SANS_64 = font(FONT_SANS, 64)
F_SANS_72 = font(FONT_SANS, 72)


def text_size(draw, text, fnt):
    box = draw.textbbox((0, 0), text, font=fnt)
    return box[2] - box[0], box[3] - box[1]


def rounded_rect(draw, xy, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def background(seed):
    random.seed(seed)
    img = Image.new("RGB", (W, H), NAVY)
    px = img.load()
    for y in range(H):
        for x in range(W):
            v = int(10 * (y / H) + random.randint(-5, 5))
            px[x, y] = (
                max(0, min(255, NAVY[0] + v)),
                max(0, min(255, NAVY[1] + v)),
                max(0, min(255, NAVY[2] + v)),
            )
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    for y in range(80, H, 118):
        d.line((0, y, W, y + random.randint(-8, 8)), fill=(255, 255, 255, 10), width=1)
    for i in range(90):
        x = random.randint(40, W - 40)
        y = random.randint(40, H - 40)
        r = random.choice([1, 1, 2])
        d.ellipse((x, y, x + r, y + r), fill=(245, 223, 154, random.randint(28, 70)))
    return Image.alpha_composite(img.convert("RGBA"), overlay)


def draw_logo(draw, img):
    logo = Image.open(ASSETS / "byokey-lab-logo.png").convert("RGBA")
    bbox = logo.getbbox()
    logo = logo.crop(bbox)
    # Remove near-white outer field.
    alpha = logo.getchannel("A")
    data = logo.load()
    for y in range(logo.height):
        for x in range(logo.width):
            r, g, b, a = data[x, y]
            if r > 246 and g > 246 and b > 246:
                data[x, y] = (255, 255, 255, 0)
    logo.thumbnail((95, 95), Image.Resampling.LANCZOS)
    img.alpha_composite(logo, (72, 64))
    draw.text((182, 72), "BYOKey Speak", font=F_SERIF_32, fill=INK)
    draw.text((182, 114), "The key is in your hand.", font=font(FONT_SERIF, 19), fill=GOLD_SOFT)


def wrap_chars(text, max_chars):
    lines = []
    for raw in text.split("\n"):
        if len(raw) <= max_chars:
            lines.append(raw)
            continue
        line = ""
        for ch in raw:
            if len(line) >= max_chars:
                lines.append(line)
                line = ch
            else:
                line += ch
        if line:
            lines.append(line)
    return lines


def wrap_px(draw, text, fnt, max_width):
    lines = []
    current = ""
    for ch in text:
        candidate = current + ch
        if current and text_size(draw, candidate, fnt)[0] > max_width:
            lines.append(current)
            current = ch
        else:
            current = candidate
    if current:
        lines.append(current)
    return lines


def draw_multiline(draw, xy, lines, fonts, fills, line_gap=16):
    x, y = xy
    for i, line in enumerate(lines):
        fnt = fonts[i] if isinstance(fonts, list) else fonts
        fill = fills[i] if isinstance(fills, list) else fills
        draw.text((x, y), line, font=fnt, fill=fill)
        y += text_size(draw, line, fnt)[1] + line_gap
    return y


def draw_phone(draw, x, y, w, h, label, items, accent_index=None):
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    rounded_rect(sd, (x + 18, y + 28, x + w + 18, y + h + 28), 70, (0, 0, 0, 90))
    blur = shadow.filter(ImageFilter.GaussianBlur(16))
    base.alpha_composite(blur)
    rounded_rect(draw, (x, y, x + w, y + h), 72, (4, 12, 23), outline=GOLD, width=5)
    rounded_rect(draw, (x + 24, y + 24, x + w - 24, y + h - 24), 52, (12, 28, 48), outline=(80, 67, 38), width=2)
    draw.rounded_rectangle((x + w * 0.36, y + 18, x + w * 0.64, y + 32), radius=8, fill=(35, 35, 42))
    draw.text((x + 52, y + 76), label, font=F_SANS_34, fill=GOLD_SOFT)
    draw.line((x + 50, y + 130, x + w - 50, y + 130), fill=(95, 78, 45), width=2)
    cy = y + 178
    for idx, (title, body) in enumerate(items):
        wrapped = wrap_px(draw, body, F_SANS_28, w - 156)
        card_h = 112 + 34 * max(1, len(wrapped))
        rounded_rect(draw, (x + 48, cy, x + w - 48, cy + card_h), 24, PANEL, outline=GOLD, width=2)
        color = CORAL if idx == accent_index else GOLD_SOFT
        draw.text((x + 78, cy + 24), title, font=F_SANS_28, fill=color)
        body_y = cy + 68
        for line in wrapped:
            draw.text((x + 78, body_y), line, font=F_SANS_28, fill=INK)
            body_y += 34
        cy += card_h + 28
        if cy > y + h - 230:
            break
    rounded_rect(draw, (x + 62, y + h - 132, x + w - 62, y + h - 66), 22, (21, 38, 62), outline=(93, 77, 47), width=2)
    draw.text((x + 92, y + h - 120), "ここに実画面スクショを差し替え", font=font(FONT_SANS, 22), fill=MUTED)


def draw_badge(draw, text, x, y, wide=False):
    fnt = F_SANS_28
    tw, th = text_size(draw, text, fnt)
    pad_x = 24
    bw = max(tw + pad_x * 2, 530 if wide else 0)
    rounded_rect(draw, (x, y, x + bw, y + 58), 10, (24, 42, 65), outline=LINE, width=2)
    draw.text((x + pad_x, y + 12), text, font=fnt, fill=INK)


slides = [
    {
        "file": "01-chat-jp-en-wireframe.png",
        "lines": ["英語で話す。", "言葉に詰まっても、", "日本語で聞ける。"],
        "accent": 2,
        "sub": "先生に相談するように、そのまま会話を続けられます。",
        "phone": "通常チャット画面",
        "items": [
            ("英語の会話", "I missed the train."),
            ("日本語で相談", "“乗り遅れた”って自然に言うと？"),
            ("コーチの返答", "英語表現＋日本語の短い説明"),
        ],
        "badge": "アプリは買い切り｜AI利用料は使った分だけ",
    },
    {
        "file": "02-coach-settings-wireframe.png",
        "lines": ["話し方も、教え方も。", "あなたの言葉で決められる。"],
        "accent": 1,
        "sub": "性格・添削方針・説明言語・会話スタイルを自由に設定",
        "phone": "Coach Personalities & Skills",
        "items": [
            ("設定欄", "間違いは日本語で短く説明してください。"),
            ("添削方針", "会話を止めず、最後にまとめて添削。"),
            ("話し方", "質問を多めにして、やさしく励ます。"),
        ],
    },
    {
        "file": "03-quick-assist-wireframe.png",
        "lines": ["返信を選ぶのではなく、", "言いたいことを英語にする。"],
        "accent": 1,
        "sub": "日本語でも、英語でも、混ざっていても大丈夫です。",
        "phone": "Quick Assist",
        "items": [
            ("入力", "子どもを寝かしつけないといけない"),
            ("候補 1", "I need to put my kids to bed."),
            ("候補 2", "It’s time for bed, so I have to go."),
        ],
        "accent_item": 0,
    },
    {
        "file": "04-analysis-wireframe.png",
        "lines": ["話したあとに、", "次に伸ばすところが", "見えてくる。"],
        "accent": 2,
        "sub": "会話履歴から、強み・傾向・改善ポイントを分析",
        "phone": "会話分析結果",
        "items": [
            ("強み", "理由を添えて話せている"),
            ("よくある傾向", "表現が直訳になりやすい"),
            ("改善ポイント", "短い自然な言い換えを増やす"),
            ("次の練習項目", "反対意見をやさしく伝える"),
        ],
    },
    {
        "file": "05-byok-pricing-wireframe.png",
        "lines": ["使うAIも、", "毎月の費用も、", "自分で選ぶ。"],
        "accent": 2,
        "sub": "アプリは買い切り。AI利用料は使った分だけ。",
        "phone": "AIサービス・モデル選択画面",
        "items": [
            ("LLM provider", "Gemini / OpenAI / Claude"),
            ("Model", "会話品質や費用感で選択"),
            ("API key", "対応AIサービスのAPIキーを入力"),
        ],
        "note": "利用には対応AIサービスのAPIキーが必要です。",
        "small": "会話履歴は原則端末内に保存",
    },
]


for index, slide in enumerate(slides, start=1):
    base = background(index)
    d = ImageDraw.Draw(base)
    draw_logo(d, base)

    d.text((72, 198), f"{index}/5", font=F_SANS_28, fill=GOLD_SOFT)
    y = 270
    for i, line in enumerate(slide["lines"]):
        fill = CORAL if i == slide["accent"] else INK
        fnt = F_SANS_64 if len(line) <= 13 else F_SANS_58
        d.text((72, y), line, font=fnt, fill=fill)
        y += text_size(d, line, fnt)[1] + 24
    d.line((72, y + 8, 520, y + 8), fill=GOLD, width=5)
    y += 70
    for line in wrap_chars(slide["sub"], 22):
        d.text((72, y), line, font=F_SANS_34, fill=MUTED)
        y += 46

    if slide.get("badge"):
        draw_badge(d, slide["badge"], 72, H - 156, wide=True)

    draw_phone(
        d,
        245,
        740,
        590,
        980,
        slide["phone"],
        slide["items"],
        slide.get("accent_item"),
    )

    if slide.get("note"):
        d.text((72, H - 188), slide["note"], font=F_SANS_28, fill=MUTED)
    if slide.get("small"):
        draw_badge(d, slide["small"], 72, H - 110)

    out = OUT / slide["file"]
    base.convert("RGB").save(out, quality=95)

print("generated", len(slides), "wireframes in", OUT)
