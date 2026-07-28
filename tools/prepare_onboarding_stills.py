from pathlib import Path

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "tools" / "onboarding_sources"
OUTPUT_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
SIZE = (960, 540)

SOURCES = (
    "page1_phone_ai.png",
    "page2_gears.png",
    "page3_bell.png",
    "page4_phone_warded_key.png",
    "page5_keyhole_door.png",
)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for index, name in enumerate(SOURCES, start=1):
        source = SOURCE_DIR / name
        output = OUTPUT_DIR / f"onboarding_bg_{index}.jpg"
        with Image.open(source) as image:
            prepared = ImageOps.fit(
                image.convert("RGB"),
                SIZE,
                method=Image.Resampling.LANCZOS,
            )
            prepared.save(output, "JPEG", quality=92, optimize=True, progressive=True)
        print(f"{source.name} -> {output.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
