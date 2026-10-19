"""The White Tree of Gondor beneath the seven stars."""
from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import step

from lib.bookmark import TASSEL_R, TASSEL_Y, blank, body, frame, polyline, ring, star, stroke, tassel_hole

TRUNK_BASE = (0.0, -62.0)
TRUNK_TOP = (0.0, -8.0)


def _branch(start, angle: float, length: float, width: float, depth: int):
    """A symmetric fork: each branch splits in two until `depth` runs out."""
    end = (start[0] + length * math.cos(math.radians(angle)), start[1] + length * math.sin(math.radians(angle)))
    shape = stroke(start, end, width, width * 0.72)
    if depth > 0:
        for turn in (-24.0, 24.0):
            shape = shape + _branch(end, angle + turn, length * 0.74, width * 0.72, depth - 1)
    return shape


def _white_tree():
    outline = blank()

    trunk = stroke(TRUNK_BASE, TRUNK_TOP, 5.0, 3.2)
    crown = _branch(TRUNK_TOP, 90.0, 11.0, 3.0, 3)
    crown = crown + _branch((0, -16), 90 + 40, 8.0, 2.4, 2) + _branch((0, -16), 90 - 40, 8.0, 2.4, 2)
    crown = crown + _branch((0, -27), 90 + 62, 8.5, 2.0, 1) + _branch((0, -27), 90 - 62, 8.5, 2.0, 1)

    roots = None
    for side in (-1, 1):
        for reach, drop in ((6.0, 4.0), (12.0, 6.5)):
            root = polyline([(0, -58), (side * reach * 0.5, -60 - drop * 0.5), (side * reach, -58 - drop)], 2.0, 1.0)
            roots = root if roots is None else roots + root
    ground = polyline([(-15, -70), (-7, -68.5), (0, -68), (7, -68.5), (15, -70)], 1.4)

    # Seven stars arched over the crown.
    stars = None
    for i in range(7):
        x = -14.4 + i * 4.8
        c = (x, 30 + 7 * (1 - (x / 14.4) ** 2))
        s = star(c, 5, 2.2, 0.95)
        stars = s if stars is None else stars + s

    relief = (
        frame(outline) + trunk + crown + roots + ground + stars
        + ring((0, TASSEL_Y), TASSEL_R + 1.4, TASSEL_R)
    )
    part = body(outline, relief, holes=tassel_hole())
    part.label = "white_tree_bookmark"
    return part


@step(out="../STEP/white_tree.step")
def white_tree():
    return _white_tree()


if __name__ == "__main__":
    white_tree()
