"""Colors and shared shapes for the New York layered shadow box.

The New York box reuses the Japan box's frame, mat and plate system
(japan_common): the same slots, plate size and inlaid-detail scheme.
"""

from __future__ import annotations

from cadgen import srgb

from japan_common import circle, rect

NAVY_BLACK = srgb("#24252B")
SKY = srgb("#A9CBE6")
GOLD = srgb("#D4A537")
BRONZE = srgb("#A8743A")
CLOUD = srgb("#F7F7F5")
PINK = srgb("#F2A7C3")
ROSE = srgb("#9E2B48")
RED = srgb("#D3262B")
WATER = srgb("#BFDDF2")
WHITE = srgb("#F4F4F2")


def windows(x0: float, x1: float, y0: float, y1: float, w: float = 2.6, h: float = 4.0,
            gap_x: float = 3.0, gap_y: float = 4.0):
    """Grid of window holes inside a building's face, or None if none fit."""
    holes = None
    nx = int((x1 - x0 - gap_x) // (w + gap_x))
    ny = int((y1 - y0 - gap_y) // (h + gap_y))
    if nx < 1 or ny < 1:
        return None
    left = x0 + (x1 - x0 - (nx * w + (nx - 1) * gap_x)) / 2
    for i in range(nx):
        for j in range(ny):
            x = left + i * (w + gap_x)
            y = y0 + gap_y + j * (h + gap_y)
            r = rect(x, x + w, y, y + h)
            holes = r if holes is None else holes + r
    return holes


def cloud(cx: float, cy: float, s: float):
    """Flat-bottomed cartoon cloud about 4 * s wide."""
    shape = circle(cx - 1.2 * s, cy, 0.75 * s) + circle(cx, cy + 0.35 * s, 1.05 * s)
    shape = shape + circle(cx + 1.25 * s, cy + 0.05 * s, 0.8 * s)
    shape = shape + rect(cx - 1.9 * s, cx + 2.0 * s, cy - 0.6 * s, cy)
    return shape - rect(cx - 3 * s, cx + 3 * s, cy - 3 * s, cy - 0.6 * s)
