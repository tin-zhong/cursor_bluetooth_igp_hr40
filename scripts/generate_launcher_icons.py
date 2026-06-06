#!/usr/bin/env python3
"""Generate Android mipmap launcher icons from a 1024x1024 source PNG."""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def square_crop(image: Image.Image) -> Image.Image:
    width, height = image.size
    side = min(width, height)
    left = (width - side) // 2
    top = (height - side) // 2
    return image.crop((left, top, left + side, top + side))


def generate(source: Path, output_root: Path) -> None:
    image = square_crop(Image.open(source).convert("RGBA"))
    for folder, size in SIZES.items():
        target_dir = output_root / folder
        target_dir.mkdir(parents=True, exist_ok=True)
        resized = image.resize((size, size), Image.Resampling.LANCZOS)
        launcher = target_dir / "ic_launcher.png"
        round_icon = target_dir / "ic_launcher_round.png"
        resized.save(launcher, format="PNG")
        resized.save(round_icon, format="PNG")


def main() -> int:
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <source.png> <output_res_dir>", file=sys.stderr)
        return 1
    source = Path(sys.argv[1])
    output_root = Path(sys.argv[2])
    if not source.is_file():
        print(f"Source not found: {source}", file=sys.stderr)
        return 1
    generate(source, output_root)
    print(f"OK: {output_root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
