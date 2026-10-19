"""The One Ring glowing above Mount Doom, lava channels running down the mountain."""
from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import step

from lib.bookmark import TASSEL_R, TASSEL_Y, blank, body, frame, polygon, polyline, ring, stroke, tassel_hole

RING_CENTER = (0.0, 30.0)
RING_OUT, RING_IN = 12.5, 8.8


def _one_ring():
    outline = blank()

    mountain = polygon([
        (-17, -72), (17, -72), (17, -63), (11, -54), (6, -41), (3.2, -32),
        (1.4, -34), (-1.4, -34), (-3.2, -32), (-6.5, -43), (-12, -56), (-17, -65),
    ])
    lava = (
        polyline([(0, -34), (-1.2, -41), (1.0, -48), (-2.0, -57), (-0.5, -70)], 1.3, 1.0)
        + polyline([(1.8, -35), (4.6, -44), (3.6, -51), (8.5, -60), (10, -70)], 1.1, 0.9)
        + polyline([(-1.6, -38), (-5.5, -48), (-6.5, -58), (-11, -68)], 1.1, 0.9)
    )
    mountain = mountain - lava
    eruption = (
        stroke((0, -32), (0, -23), 2.4, 0.9)
        + stroke((-1.8, -32), (-5.5, -25.5), 1.8, 0.9)
        + stroke((1.8, -32), (5.5, -25.5), 1.8, 0.9)
    )

    # Smoke curling up from the crater towards the ring.
    smoke = polyline([(4.0 * math.sin(i * 0.55), -21 + i * 2.0) for i in range(16)], 1.6, 1.0)

    # The ring, its fiery inscription shown as dashes cut along the band.
    band = ring(RING_CENTER, RING_OUT, RING_IN)
    mid = (RING_OUT + RING_IN) / 2
    for i in range(14):
        a0 = math.radians(i * 360 / 14 + 4)
        a1 = a0 + math.radians(15)
        p = (RING_CENTER[0] + mid * math.cos(a0), RING_CENTER[1] + mid * math.sin(a0))
        q = (RING_CENTER[0] + mid * math.cos(a1), RING_CENTER[1] + mid * math.sin(a1))
        band = band - stroke(p, q, 0.8)
    glow = None
    for i in range(24):
        a = math.radians(i * 15)
        r0, r1 = RING_OUT + 1.6, RING_OUT + (4.5 if i % 2 == 0 else 3.0)
        ray = stroke(
            (RING_CENTER[0] + r0 * math.cos(a), RING_CENTER[1] + r0 * math.sin(a)),
            (RING_CENTER[0] + r1 * math.cos(a), RING_CENTER[1] + r1 * math.sin(a)),
            1.1, 0.9,
        )
        glow = ray if glow is None else glow + ray

    relief = (
        frame(outline) + mountain + eruption + smoke + band + glow
        + ring((0, TASSEL_Y), TASSEL_R + 1.4, TASSEL_R)
    )
    part = body(outline, relief, holes=tassel_hole())
    part.label = "one_ring_bookmark"
    return part


@step(out="../STEP/one_ring.step")
def one_ring():
    return _one_ring()


if __name__ == "__main__":
    one_ring()
