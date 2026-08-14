from pathlib import Path
import random

from PIL import Image
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Inches, Pt


BASE = Path("byokey-speak-android/docs/marketing/play-screenshots-wireframes-2026-07-31")
ASSETS = Path("byokey-speak-android/docs/marketing/feature-graphic-2026-07-30/assets")
ATTACH = Path(".codex-remote-attachments/019fb222-aba3-7352-8189-59f0acb45451/76e4f069-b1eb-45d6-8d7c-57d452ae8706/1-Photo-1.jpg")

OUT = BASE / "byokey-speak-feature-panorama-2160x1920-editable.pptx"
PREVIEW = BASE / "byokey-speak-feature-panorama-2160x1920-preview.png"
BG = BASE / "feature-panorama-bg.png"
KEYHOLE = BASE / "assets-keyhole-transparent.png"
SCREENSHOT = BASE / "feature-panorama-phone-screen.png"

W, H = 2160, 1920
SCALE = 100

NAVY = RGBColor(8, 20, 35)
NAVY_2 = RGBColor(14, 31, 52)
CARD = RGBColor(29, 50, 78)
GOLD = RGBColor(185, 145, 55)
GOLD_SOFT = RGBColor(219, 193, 111)
CORAL = RGBColor(232, 151, 132)
WHITE = RGBColor(248, 245, 235)
MUTED = RGBColor(210, 194, 150)

FONT_HEAD = "Noto Sans JP"
FONT_SERIF = "Noto Serif JP"


def u(px: float):
    return Inches(px / SCALE)


def add_text(slide, x, y, w, h, text, size, color=WHITE, bold=True, font=FONT_HEAD, align=PP_ALIGN.LEFT):
    box = slide.shapes.add_textbox(u(x), u(y), u(w), u(h))
    tf = box.text_frame
    tf.clear()
    tf.margin_left = 0
    tf.margin_right = 0
    tf.margin_top = 0
    tf.margin_bottom = 0
    tf.vertical_anchor = MSO_ANCHOR.TOP
    for idx, line in enumerate(text.split("\n")):
        p = tf.paragraphs[0] if idx == 0 else tf.add_paragraph()
        p.alignment = align
        run = p.add_run()
        run.text = line
        run.font.name = font
        run.font.size = Pt(size)
        run.font.bold = bold
        run.font.color.rgb = color
    return box


def add_rect(slide, x, y, w, h, fill, line=None, radius=True, line_width=1.0, transparency=0):
    shape = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE,
        u(x),
        u(y),
        u(w),
        u(h),
    )
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill
    shape.fill.transparency = transparency
    if line is None:
        shape.line.fill.background()
    else:
        shape.line.color.rgb = line
        shape.line.width = Pt(line_width)
    return shape


def add_line(slide, x1, y1, x2, y2, color=GOLD, width=3):
    line = slide.shapes.add_connector(1, u(x1), u(y1), u(x2), u(y2))
    line.line.color.rgb = color
    line.line.width = Pt(width)
    return line


def make_background():
    random.seed(2160)
    img = Image.new("RGB", (W, H), (8, 20, 35))
    px = img.load()
    for y in range(H):
        for x in range(W):
            grad = int(18 * (x / W) + 10 * (y / H))
            noise = random.randint(-5, 5)
            px[x, y] = (
                max(0, min(255, 8 + grad + noise)),
                max(0, min(255, 20 + grad + noise)),
                max(0, min(255, 35 + grad + noise)),
            )
    img.save(BG)


def make_keyhole():
    if KEYHOLE.exists():
        return
    logo = Image.open(ASSETS / "byokey-lab-logo.png").convert("RGBA")
    crop = logo.crop((392, 320, 635, 665))
    data = crop.load()
    for y in range(crop.height):
        for x in range(crop.width):
            r, g, b, a = data[x, y]
            if r > 246 and g > 246 and b > 246:
                data[x, y] = (255, 255, 255, 0)
    crop.save(KEYHOLE)


def make_phone_screen():
    src = Image.open(ATTACH).convert("RGB")
    # Keep the real screenshot content. Crop a tall top-focused slice so it reads
    # like the final feature graphic while remaining replaceable in Canva.
    src.thumbnail((650, 1458), Image.Resampling.LANCZOS)
    src.save(SCREENSHOT)


def build_pptx():
    prs = Presentation()
    prs.slide_width = u(W)
    prs.slide_height = u(H)
    slide = prs.slides.add_slide(prs.slide_layouts[6])

    slide.shapes.add_picture(str(BG), 0, 0, width=u(W), height=u(H))
    # Split guide. Kept subtle but editable; user can delete it in Canva.
    add_line(slide, 1080, 0, 1080, H, RGBColor(38, 57, 80), 1)

    # Brand block
    slide.shapes.add_picture(str(KEYHOLE), u(92), u(92), height=u(138))
    add_text(slide, 250, 105, 520, 52, "BYOKey Speak", 43, WHITE, True, FONT_SERIF)
    add_text(slide, 252, 164, 690, 34, "AI英会話は、“Bring Your Own Key”で新たな体験へ", 21, GOLD_SOFT, True)

    # Hero copy
    add_text(slide, 92, 430, 900, 118, "英語で話す。", 68, WHITE, True)
    add_text(slide, 92, 570, 960, 118, "言葉に詰まっても、", 68, WHITE, True)
    add_text(slide, 92, 710, 850, 118, "日本語で聞ける。", 72, CORAL, True)
    add_line(slide, 92, 855, 760, 855, GOLD, 5)
    add_text(slide, 92, 970, 900, 52, "話し方も、教え方も、自分好みに。", 29, MUTED, True)

    # Cost badge
    add_rect(slide, 92, 1108, 710, 72, CARD, GOLD, True, 1.5)
    add_text(slide, 122, 1123, 650, 42, "アプリは買い切り、毎月の費用は使った分だけ", 25, WHITE, True, align=PP_ALIGN.CENTER)

    # A few small accent strokes crossing the panorama.
    for x, y, w, color in [
        (1240, 365, 66, GOLD),
        (1325, 505, 88, CORAL),
        (1260, 640, 72, RGBColor(161, 177, 119)),
        (1820, 166, 132, GOLD),
    ]:
        add_line(slide, x, y, x + w, y, color, 2)

    # Phone mockup and real screenshot. Bottom extends slightly below the canvas.
    phone_x, phone_y, phone_w, phone_h = 1315, -52, 735, 2035
    add_rect(slide, phone_x + 24, phone_y + 38, phone_w, phone_h, RGBColor(0, 0, 0), None, True, transparency=55)
    add_rect(slide, phone_x, phone_y, phone_w, phone_h, RGBColor(5, 13, 25), GOLD, True, 3)
    add_rect(slide, phone_x + 32, phone_y + 32, phone_w - 64, phone_h - 64, RGBColor(10, 25, 44), GOLD, True, 1.5)
    slide.shapes.add_picture(str(SCREENSHOT), u(phone_x + 55), u(phone_y + 58), width=u(phone_w - 110))

    # Bottom extension on the left half so it feels intentionally full-height.
    add_text(slide, 92, 1690, 750, 44, "英語だけで頑張らなくていい。", 30, RGBColor(117, 139, 109), True)
    add_text(slide, 92, 1742, 780, 42, "会話の途中で、先生に相談するように聞ける。", 24, MUTED, True)

    prs.save(OUT)


def main():
    make_background()
    make_keyhole()
    make_phone_screen()
    build_pptx()
    print(OUT)


if __name__ == "__main__":
    main()
