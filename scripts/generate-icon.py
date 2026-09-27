"""Create the Windows icon from the four-tile Goaltree mark."""

from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
image = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)
draw.rounded_rectangle((0, 0, 255, 255), radius=30, fill="#29244b")
draw.rectangle((48, 48, 227, 227), fill="#17142d")
draw.rectangle((28, 28, 219, 219), fill="#17142d")
draw.rectangle((36, 36, 211, 211), fill="#fffdfb")
draw.rectangle((52, 52, 115, 115), fill="#6254d9")
draw.rectangle((132, 52, 195, 115), fill="#74d8dc")
draw.rectangle((52, 132, 115, 195), fill="#74d8dc")
draw.rectangle((132, 132, 195, 195), fill="#f0bb55")
image.save(root / "frontend" / "public" / "favicon.ico", format="ICO", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
