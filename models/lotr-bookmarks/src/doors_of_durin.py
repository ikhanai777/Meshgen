"""The Doors of Durin: pillars and arch, the Star of the House of Fëanor, the crown and seven stars."""
from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import step

from lib.bookmark import TASSEL_R, TASSEL_Y, blank, body, frame, polygon, ring, star, stroke, tassel_hole

ARCH_CENTER = (0.0, 12.0)
PILLAR_X = 12.0
PILLAR_W = 3.4


def _arc(center, radius: float, width: float, start: float = 0.0, end: float = 180.0):
    """A band of the given width along a circular arc."""
    shape = None
    steps = 18
    for i in range(steps):
        a0 = math.radians(start + (end - start) * i / steps)
        a1 = math.radians(start + (end - start) * (i + 1) / steps)
        seg = stroke(
            (center[0] + radius * math.cos(a0), center[1] + radius * math.sin(a0)),
            (center[0] + radius * math.cos(a1), center[1] + radius * math.sin(a1)),
            width,
        )
        shape = seg if shape is None else shape + seg
    return shape


def _doors_of_durin():
    outline = blank()
    cx, cy = ARCH_CENTER

    pillars = None
    for side in (-1, 1):
        x = side * PILLAR_X
        p = (
            bd.Pos(x, (cy - 60) / 2) * bd.Rectangle(PILLAR_W, cy + 60)
            + bd.Pos(x, cy - 1.0) * bd.Rectangle(PILLAR_W + 3.0, 2.0)  # capital
            + bd.Pos(x, -59.0) * bd.Rectangle(PILLAR_W + 3.0, 2.0)  # plinth
        )
        pillars = p if pillars is None else pillars + p

    arch = _arc(ARCH_CENTER, PILLAR_X, PILLAR_W) + _arc(ARCH_CENTER, PILLAR_X + 3.6, 1.2)
    steps = bd.Pos(0, -63.0) * bd.Rectangle(30.0, 2.4) + bd.Pos(0, -67.5) * bd.Rectangle(33.0, 2.4)

    # The Star of Fëanor: an eight-pointed star with long rays between the points.
    star_c = (0.0, -18.0)
    feanor = star(star_c, 8, 6.5, 2.6)
    for i in range(8):
        a = math.radians(i * 45 + 22.5 + 90)
        feanor = feanor + stroke(
            (star_c[0] + 3.0 * math.cos(a), star_c[1] + 3.0 * math.sin(a)),
            (star_c[0] + 9.5 * math.cos(a), star_c[1] + 9.5 * math.sin(a)),
            1.3, 0.9,
        )

    crown = polygon([(-5.5, 0), (5.5, 0), (6.0, 5.5), (3.0, 3.0), (0, 7.0), (-3.0, 3.0), (-6.0, 5.5)])
    crown = bd.Pos(0, -4.0) * crown

    stars = None
    for i in range(7):
        a = math.radians(160 - i * (140 / 6))
        s = star((cx + 6.8 * math.cos(a), cy + 6.8 * math.sin(a)), 4, 1.8, 0.75)
        stars = s if stars is None else stars + s

    # A lintel of runes over the arch, as alternating cut ticks in a raised band.
    lintel = bd.Pos(0, cy + 21.5) * bd.Rectangle(31.0, 3.6)
    for i in range(11):
        x = -13.5 + i * 2.7
        lintel = lintel - stroke((x - 0.5, cy + 20.6), (x + 0.5, cy + 22.4), 0.8)
    lamp = star((0, cy + 32.0), 8, 4.0, 1.7)

    relief = (
        frame(outline) + pillars + arch + steps + feanor + crown + stars + lintel + lamp
        + ring((0, TASSEL_Y), TASSEL_R + 1.4, TASSEL_R)
    )
    part = body(outline, relief, holes=tassel_hole())
    part.label = "doors_of_durin_bookmark"
    return part


@step(out="../STEP/doors_of_durin.step")
def doors_of_durin():
    return _doors_of_durin()


if __name__ == "__main__":
    doors_of_durin()
