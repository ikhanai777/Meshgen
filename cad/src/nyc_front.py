"""Front layer of the New York shadow box: a park with the Washington Square
Arch, a street lamp, an iron fence and two trees on a pink and rose ground."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import band, circle, plate_compound, plate_parts, rect, stroke
from nyc_common import NAVY_BLACK, PINK, ROSE

GROUND = [(-90.0, 30.0), (-50.0, 32.0), (-10.0, 30.0), (30.0, 40.0), (60.0, 54.0), (90.0, 60.0)]
STRIP = [(-90.0, 16.0), (0.0, 18.0), (90.0, 26.0)]
ARCH_X = -10.0
LAMP_X = -74.0
FENCE = (16.0, 44.0)
TREE_DARK = (68.0, 110.0)
TREE_ROSE = (52.0, 92.0)


def washington_arch():
    c = ARCH_X
    shape = rect(c - 20, c + 20, 26, 90) + rect(c - 22, c + 22, 86, 92) + rect(c - 18, c + 18, 92, 100)
    shape = shape + rect(c - 22, c + 22, 100, 102)
    opening = rect(c - 9, c + 9, 25, 60) + circle(c, 60, 9)
    panels = rect(c - 17, c - 12, 36, 74) + rect(c + 12, c + 17, 36, 74)
    return shape - opening - panels


def street_lamp():
    c = LAMP_X
    shape = stroke([(c, 28), (c, 101)], 2.6) + rect(c - 3, c + 3, 28, 34)
    shape = shape + rect(c - 11, c + 11, 92, 94) + stroke([(c - 11, 93), (c - 11, 97)], 1.4)
    shape = shape + stroke([(c + 11, 93), (c + 11, 97)], 1.4)
    for x, y in ((c - 11, 97), (c + 11, 97), (c, 100)):
        shape = shape + rect(x - 2.8, x + 2.8, y, y + 7) + rect(x - 3.6, x + 3.6, y + 7, y + 8.5)
        shape = shape + rect(x - 0.6, x + 0.6, y + 8, y + 11)
    return shape


def fence():
    x0, x1 = FENCE
    shape = rect(x0, x1, 46, 48.5) + rect(x0, x1, 60, 62)
    for x in (x0, x1):
        shape = shape + rect(x - 1.8, x + 1.8, 36, 72) + rect(x - 2.8, x + 2.8, 72, 75) + circle(x, 76.5, 2)
    x = x0 + 4
    while x < x1 - 2:
        shape = shape + rect(x - 0.6, x + 0.6, 40, 64)
        x += 4
    return shape


def tree(cx: float, cy: float, r: float, trunk_bottom: float):
    shape = stroke([(cx, trunk_bottom), (cx, cy - r * 0.4)], r * 0.26)
    shape = shape + stroke([(cx, cy - r * 0.6), (cx - r * 0.5, cy - r * 0.1)], r * 0.14)
    for dx, dy, k in ((0, 0, 1.0), (-0.7, -0.3, 0.7), (0.7, -0.25, 0.72), (-0.4, 0.55, 0.65), (0.45, 0.6, 0.6)):
        shape = shape + circle(cx + dx * r, cy + dy * r, k * r)
    return shape


def front_parts():
    return plate_parts("nyc_front", band(GROUND), PINK, [
        ("strip", band(STRIP), ROSE, 0.8),
        ("tree_dark", tree(*TREE_DARK, 13.0, 50.0), NAVY_BLACK, 1.2),
        ("tree_rose", tree(*TREE_ROSE, 10.0, 44.0), ROSE, 1.8),
        ("fence", fence(), NAVY_BLACK, 1.6),
        ("street_lamp", street_lamp(), NAVY_BLACK, 1.6),
        ("washington_arch", washington_arch(), NAVY_BLACK, 1.6),
    ])


@step(out="../STEP/nyc_front.step")
@stl(out="../STL/nyc_front.stl")
@threemf(out="../3MF/nyc_front.3mf")
def nyc_front():
    return plate_compound("nyc_front", front_parts())


if __name__ == "__main__":
    nyc_front()
