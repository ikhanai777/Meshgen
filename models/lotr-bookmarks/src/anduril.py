"""Andúril, Flame of the West: a sword-shaped bookmark with sun, moon and seven stars along the blade."""
from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import step

from lib.bookmark import TASSEL_R, body, polygon, ring, star, stroke

POMMEL = (0.0, 66.0)
POMMEL_R = 7.0
GUARD_Y = 40.0  # bottom of the cross-guard, where the blade starts
BLADE_W = 13.0
TIP_Y = -75.0


def _outline():
    blade = polygon([
        (-BLADE_W / 2, GUARD_Y + 1), (-BLADE_W / 2 + 1.2, -52), (0, TIP_Y),
        (BLADE_W / 2 - 1.2, -52), (BLADE_W / 2, GUARD_Y + 1),
    ])
    guard = polygon([
        (-20, GUARD_Y + 5.5), (-15, GUARD_Y + 3.0), (-6, GUARD_Y + 3.8), (0, GUARD_Y + 6.5),
        (6, GUARD_Y + 3.8), (15, GUARD_Y + 3.0), (20, GUARD_Y + 5.5),
        (15, GUARD_Y - 1.0), (6, GUARD_Y + 0.4), (0, GUARD_Y - 3.0), (-6, GUARD_Y + 0.4), (-15, GUARD_Y - 1.0),
    ])
    grip = polygon([(-3.6, GUARD_Y + 4), (3.6, GUARD_Y + 4), (3.0, POMMEL[1] - 4), (-3.0, POMMEL[1] - 4)])
    pommel = bd.Pos(*POMMEL) * bd.Circle(POMMEL_R)
    return blade + guard + grip + pommel


def _anduril():
    outline = _outline()

    # Blade: a raised edge bevel and a fuller down the middle, broken by the emblems.
    blade_face = polygon([
        (-BLADE_W / 2, GUARD_Y - 2), (-BLADE_W / 2 + 1.2, -52), (0, TIP_Y),
        (BLADE_W / 2 - 1.2, -52), (BLADE_W / 2, GUARD_Y - 2),
    ])
    edge = bd.offset(blade_face, -0.9) - bd.offset(blade_face, -2.2)

    sun_c = (0.0, 26.0)
    sun = bd.Pos(*sun_c) * bd.Circle(2.4)
    for i in range(8):
        a = math.radians(i * 45)
        sun = sun + stroke(
            (sun_c[0] + 2.8 * math.cos(a), sun_c[1] + 2.8 * math.sin(a)),
            (sun_c[0] + 3.8 * math.cos(a), sun_c[1] + 3.8 * math.sin(a)),
            0.9,
        )
    moon_c = (0.0, -42.0)
    moon = bd.Pos(*moon_c) * bd.Circle(3.6) - bd.Pos(moon_c[0] + 1.6, moon_c[1] + 0.6) * bd.Circle(3.0)
    stars = None
    for i in range(7):
        s = star((0, 16 - i * 7.5), 4, 2.2, 0.8, rotation=90)
        stars = s if stars is None else stars + s
    fuller = (
        stroke((0, 32), (0, 36), 1.0) + stroke((0, -32), (0, -36), 1.0)
        + stroke((0, -48.5), (0, -60), 1.0, 0.9)
    )

    # Hilt: a gem in the guard, a wrapped grip, and a ring round the tassel hole in the pommel.
    guard_gem = star((0, GUARD_Y + 1.8), 4, 2.6, 1.2, rotation=90)
    guard_line = stroke((-15.5, GUARD_Y + 1.0), (-4.0, GUARD_Y + 1.8), 1.0) + stroke((4.0, GUARD_Y + 1.8), (15.5, GUARD_Y + 1.0), 1.0)
    wrap = None
    for i in range(5):
        y = GUARD_Y + 6.5 + i * 3.4
        w = stroke((-2.4, y), (2.4, y + 1.6), 1.0)
        wrap = w if wrap is None else wrap + w
    pommel_ring = ring(POMMEL, POMMEL_R - 0.9, TASSEL_R + 1.2)

    relief = edge + sun + moon + stars + fuller + guard_gem + guard_line + wrap + pommel_ring
    part = body(outline, relief, holes=bd.Pos(*POMMEL) * bd.Circle(TASSEL_R))
    part.label = "anduril_bookmark"
    return part


@step(out="../STEP/anduril.step")
def anduril():
    return _anduril()


if __name__ == "__main__":
    anduril()
