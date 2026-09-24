#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Generate the range-config GUI panel texture (rounded dark card, 176x200)."""
from PIL import Image, ImageDraw
import os

W, H = 176, 200
img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# ---- rounded card ----
R = 8
x0, y0, x1, y1 = 0, 0, W - 1, H - 1

# outer border (1px, subtle light) + body (dark blue-gray, translucent)
border = (255, 255, 255, 26)      # rgba(255,255,255,0.10)
body   = (24, 26, 32, 235)        # #181A20 @ 0.92

d.rounded_rectangle([x0, y0, x1, y1], radius=R, fill=border)
d.rounded_rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], radius=R - 1, fill=body)

# top highlight (soft light from above)
hl = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
hd = ImageDraw.Draw(hl)
hd.rounded_rectangle([x0 + 1, y0 + 1, x1 - 1, y0 + 22], radius=R - 1, fill=(255, 255, 255, 14))
img = Image.alpha_composite(img, hl)

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "range.png")
img.save(out)
print("written:", out)
