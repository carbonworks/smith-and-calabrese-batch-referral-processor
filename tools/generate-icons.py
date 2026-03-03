"""
Generate Windows .ico and PNG icon files from the CarbonWorks origami bird SVG emblem.

Renders the origami bird polygon data (extracted from cw-emblem.svg) at multiple
resolutions using Pillow and produces:
  - app/src/main/resources/icon.ico  (multi-resolution: 16, 32, 48, 256)
  - app/src/main/resources/icon.png  (256x256 for Linux / Compose window icon)

The bird color is CarbonWorks brand teal #03757A.

Prerequisites: pip install pillow

Alternative: run `./gradlew :app:generateIcons` (uses Java AWT, no Python needed).
"""

from pathlib import Path
from PIL import Image, ImageDraw

# Brand teal color: #03757A
BRAND_TEAL = (3, 117, 122, 255)


def get_bird_polygons():
    """
    Return the origami bird as a list of polygon vertex lists.

    Vertices are in raw SVG coordinate space (from cw-emblem.svg path data).
    The Y coordinates include the SVG translate(0,-952.36218) offset that is
    baked into the original path.
    """
    return [
        # Upper right wing triangle
        [(60.7828, 964.36215), (87.9637, 965.24555), (60.7828, 991.24135)],
        # Upper left wing triangle
        [(58.8083, 965.81345), (58.8083, 992.59795), (33.5402, 992.59795)],
        # Beak triangle
        [(86.5136, 969.44145), (90.0, 970.64035), (77.4123, 978.11715)],
        # Left wing / body pentagon
        [
            (18.3301, 972.40705),
            (23.8527, 972.40705),
            (36.7181, 986.47755),
            (30.7327, 992.59795),
            (18.3301, 992.59795),
        ],
        # Small tail triangle
        [(16.3555, 973.63745), (16.3555, 979.47385), (10.0, 979.47385)],
        # Lower belly triangle
        [(19.7185, 994.61705), (58.3455, 994.61705), (47.578, 1024.0517)],
        # Right body / tail triangle
        [(58.8083, 999.15991), (58.8083, 1040.3622), (46.2205, 1033.4847)],
    ]


def normalize_polygons(polygons, target_size, padding_fraction=0.06):
    """Transform polygons to fit centered within a square canvas with padding."""
    all_x = [x for poly in polygons for x, y in poly]
    all_y = [y for poly in polygons for x, y in poly]
    min_x, max_x = min(all_x), max(all_x)
    min_y, max_y = min(all_y), max(all_y)

    width = max_x - min_x
    height = max_y - min_y

    padding = target_size * padding_fraction
    available = target_size - 2 * padding
    scale = available / max(width, height)

    scaled_width = width * scale
    scaled_height = height * scale
    offset_x = (target_size - scaled_width) / 2 - min_x * scale
    offset_y = (target_size - scaled_height) / 2 - min_y * scale

    return [
        [(x * scale + offset_x, y * scale + offset_y) for x, y in poly]
        for poly in polygons
    ]


def render_bird(size):
    """Render the origami bird at the given pixel size and return a PIL Image."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for poly in normalize_polygons(get_bird_polygons(), size):
        draw.polygon(poly, fill=BRAND_TEAL)

    return img


def main():
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parent
    resources_dir = project_root / "app" / "src" / "main" / "resources"
    resources_dir.mkdir(parents=True, exist_ok=True)

    ico_sizes = [16, 32, 48, 256]

    print("Generating icon images...")
    images = {size: render_bird(size) for size in ico_sizes}
    for size in ico_sizes:
        print(f"  Rendered {size}x{size}")

    # Save multi-resolution ICO using Pillow's built-in ICO writer
    ico_path = resources_dir / "icon.ico"
    images[256].save(
        str(ico_path),
        format="ICO",
        append_images=[images[s] for s in [16, 32, 48]],
    )
    print(f"  Saved {ico_path}")

    # Save 256x256 PNG for Compose window icon and Linux
    png_path = resources_dir / "icon.png"
    images[256].save(str(png_path), format="PNG")
    print(f"  Saved {png_path}")

    print("\nDone! Icon files generated in app/src/main/resources/")
    print("  icon.ico  - Windows installer/shortcut icon (16, 32, 48, 256)")
    print("  icon.png  - 256x256 PNG for Compose window icon and Linux")


if __name__ == "__main__":
    main()
