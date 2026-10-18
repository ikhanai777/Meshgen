"""Arched bridge layer of the New York shadow box: a white viaduct over a
strip of light blue water."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import PLATE_X, band, circle, plate_compound, plate_parts, rect
from nyc_common import WATER, WHITE

TOP = 52.0
WATER_TOP = 22.0
ARCH_X = [-60.0, -20.0, 20.0, 60.0]
ARCH_W = 26.0
SPRING = 30.0


def bridge_parts():
    deck = band([(-PLATE_X + 1, TOP), (PLATE_X - 1, TOP)])
    for x in ARCH_X:
        deck = deck - rect(x - ARCH_W / 2, x + ARCH_W / 2, WATER_TOP, SPRING) - circle(x, SPRING, ARCH_W / 2)
    for k in range(-21, 22):  # railing balusters
        x = k * 4.2
        deck = deck - rect(x - 0.9, x + 0.9, TOP - 6, TOP - 2.5)
    water = band([(-PLATE_X + 1, WATER_TOP), (PLATE_X - 1, WATER_TOP)])
    return plate_parts("nyc_bridge", deck, WHITE, [("water", water, WATER, 0.6)])


@step(out="../STEP/nyc_bridge.step")
@stl(out="../STL/nyc_bridge.stl")
@threemf(out="../3MF/nyc_bridge.3mf")
def nyc_bridge():
    return plate_compound("nyc_bridge", bridge_parts())


if __name__ == "__main__":
    nyc_bridge()
