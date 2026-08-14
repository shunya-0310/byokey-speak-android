from pathlib import Path

from PIL import Image, ImageChops


BASE = Path("byokey-speak-android/docs/marketing/play-screenshots-wireframes-2026-07-31")
SRC = BASE / "byokey speak_feature graffic.png"
LEFT = BASE / "byokey speak_feature graffic_split_01_left.png"
RIGHT = BASE / "byokey speak_feature graffic_split_02_right.png"

TARGET_W = 1080
TARGET_H = 1920


def main() -> None:
    src = Image.open(SRC).convert("RGBA")
    left = Image.open(LEFT).convert("RGBA")
    right = Image.open(RIGHT).convert("RGBA")

    scale = TARGET_W / left.width
    resized_h = round(left.height * scale)
    bg = src.getpixel((0, 0))

    outputs = []
    for image, name in [
        (left, "byokey speak_feature graffic_split_01_left_1080x1920.png"),
        (right, "byokey speak_feature graffic_split_02_right_1080x1920.png"),
    ]:
        resized = image.resize((TARGET_W, resized_h), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", (TARGET_W, TARGET_H), bg)
        canvas.paste(resized, (0, 0))
        out = BASE / name
        canvas.save(out)
        outputs.append(out)

    combined = Image.new("RGBA", (TARGET_W * 2, TARGET_H), bg)
    combined.paste(Image.open(outputs[0]).convert("RGBA"), (0, 0))
    combined.paste(Image.open(outputs[1]).convert("RGBA"), (TARGET_W, 0))
    combined_path = BASE / "byokey speak_feature graffic_split_combined_2160x1920_preview.png"
    combined.save(combined_path)

    top_combined = combined.crop((0, 0, TARGET_W * 2, resized_h))
    src_resized = src.resize((TARGET_W * 2, resized_h), Image.Resampling.LANCZOS)
    diff = ImageChops.difference(top_combined, src_resized).getbbox()

    print("outputs")
    for output in outputs:
        print(output, Image.open(output).size)
    print("combined", combined_path, combined.size)
    print("scaled_content_height", resized_h)
    print("combined_top_matches_full_resized_original", diff is None)


if __name__ == "__main__":
    main()
