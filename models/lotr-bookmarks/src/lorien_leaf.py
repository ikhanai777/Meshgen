"""A Leaf of Lórien: leaf-shaped bookmark with raised midrib and veins, hung from its stem."""
from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import step

from lib.bookmark import TASSEL_R, body, frame, polygon, polyline, ring, stroke

BASE_Y = 48.0  # where the stem meets the leaf
TIP_Y = -74.0
HALF_WIDTH = 19.0
LOOP = (0.0, 66.0)
LOOP_R = 6.0


def _half_width(t: float) -> float:
    """Leaf half-width at t (0 at the tip, 1 at the stem): widest about two thirds of the way up."""
    return HALF_WIDTH * math.sin(math.pi * t ** 1.35) ** 0.75


def _edge(n: int = 60):
    right = []
    for i in range(1, n):
        t = i / n
        right.append((_half_width(t), TIP_Y + (BASE_Y - TIP_Y) * t))
    left = [(-x, y) for x, y in reversed(right)]
    return [(0.0, TIP_Y)] + right + [(0.0, BASE_Y)] + left


def _lorien_leaf():
    leaf = polygon(_edge())
    stem = stroke((0, BASE_Y - 3), (0, LOOP[1] - LOOP_R + 1), 4.2, 3.4)
    loop = bd.Pos(*LOOP) * bd.Circle(LOOP_R)
    outline = leaf + stem + loop

    midrib = stroke((0, BASE_Y + 8), (0, TIP_Y + 6), 2.4, 0.9)
    veins = None
    for i in range(8):
        t = 0.88 - i * 0.1
        y0 = TIP_Y + (BASE_Y - TIP_Y) * t
        reach = _half_width(t - 0.08) - 3.4
        for side in (-1, 1):
            # Each vein sweeps out from the midrib and curls toward the tip.
            pts = [(side * reach * k, y0 - 9.0 * k ** 1.6) for k in (0.0, 0.35, 0.7, 1.0)]
            v = polyline(pts, 1.5, 0.9)
            veins = v if veins is None else veins + v
    border = frame(leaf, inset=1.2, width=1.2)
    loop_ring = ring(LOOP, LOOP_R - 0.9, TASSEL_R + 1.2)

    relief = midrib + veins + border + loop_ring
    part = body(outline, relief, holes=bd.Pos(*LOOP) * bd.Circle(TASSEL_R))
    part.label = "lorien_leaf_bookmark"
    return part


@step(out="../STEP/lorien_leaf.step")
def lorien_leaf():
    return _lorien_leaf()


if __name__ == "__main__":
    lorien_leaf()
