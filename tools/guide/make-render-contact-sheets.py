from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps


def page_number(path: Path) -> int:
    match = re.search(r"page-(\d+)$", path.stem)
    if not match:
        raise ValueError(f"Unexpected page filename: {path.name}")
    return int(match.group(1))


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: make-render-contact-sheets.py <rendered-dir> <output-dir>")

    rendered_dir = Path(sys.argv[1]).resolve()
    output_dir = Path(sys.argv[2]).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    pages = sorted(rendered_dir.glob("page-*.png"), key=page_number)
    if not pages:
        raise SystemExit(f"No rendered pages found in {rendered_dir}")

    columns = 3
    rows = 3
    thumb_width = 470
    thumb_height = 608
    label_height = 32
    gap = 14
    sheet_width = gap + columns * (thumb_width + gap)
    sheet_height = gap + rows * (thumb_height + label_height + gap)
    font = ImageFont.load_default(size=20)
    analysis: list[dict[str, object]] = []

    for start in range(0, len(pages), columns * rows):
        group = pages[start : start + columns * rows]
        canvas = Image.new("RGB", (sheet_width, sheet_height), "#d7dee8")
        draw = ImageDraw.Draw(canvas)

        for offset, path in enumerate(group):
            page_no = page_number(path)
            row, column = divmod(offset, columns)
            x = gap + column * (thumb_width + gap)
            y = gap + row * (thumb_height + label_height + gap)

            with Image.open(path) as source:
                page = source.convert("RGB")
                grayscale = ImageOps.grayscale(page)
                non_white = grayscale.point(lambda value: 255 if value < 248 else 0)
                bbox = non_white.getbbox()
                histogram = grayscale.histogram()
                total_pixels = page.width * page.height
                near_white = sum(histogram[248:])
                analysis.append(
                    {
                        "page": page_no,
                        "file": path.name,
                        "width": page.width,
                        "height": page.height,
                        "contentBox": list(bbox) if bbox else None,
                        "nearWhiteRatio": round(near_white / total_pixels, 6),
                    }
                )

                preview = ImageOps.contain(page, (thumb_width, thumb_height), Image.Resampling.LANCZOS)
                px = x + (thumb_width - preview.width) // 2
                py = y + (thumb_height - preview.height) // 2
                canvas.paste(preview, (px, py))

            label = f"Page {page_no}"
            draw.rounded_rectangle(
                (x, y + thumb_height + 2, x + thumb_width, y + thumb_height + label_height),
                radius=6,
                fill="#173552",
            )
            draw.text((x + 12, y + thumb_height + 7), label, fill="white", font=font)

        first_page = page_number(group[0])
        last_page = page_number(group[-1])
        canvas.save(output_dir / f"contact-{first_page:03d}-{last_page:03d}.png", optimize=True)

    (output_dir / "render-analysis.json").write_text(
        json.dumps(analysis, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps({"pages": len(pages), "sheets": (len(pages) + 8) // 9}))


if __name__ == "__main__":
    main()
