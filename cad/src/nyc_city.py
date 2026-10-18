"""Near city layer of the New York shadow box: pink buildings with window
holes and a red Empire State Building."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import PLATE_X, band, plate_compound, plate_parts, rect
from nyc_common import PINK, RED, windows

GROUND = 42.0
BUILDINGS = [(-90, -72, 80), (-72, -56, 70), (-14, 4, 94), (4, 22, 110), (22, 36, 86), (36, 56, 102),
             (56, 72, 120), (72, 90, 96)]
ESB_X = -32.0


def empire_state():
    c = ESB_X
    tiers = [(16, 72), (12, 98), (8.5, 128), (5.5, 140), (3.5, 148), (2.2, 156)]
    shape = None
    for half, top in tiers:
        r = rect(c - half, c + half, GROUND - 1, top)
        shape = r if shape is None else shape + r
    return shape + rect(c - 0.9, c + 0.9, 155, 174)


def city_parts():
    outline = band([(-PLATE_X + 1, GROUND), (PLATE_X - 1, GROUND)])
    holes = None
    for x0, x1, top in BUILDINGS:
        outline = outline + rect(x0, x1, GROUND - 1, top)
        if x1 - x0 >= 16:  # a water tower on the wider roofs
            outline = outline + rect(x0 + 3, x0 + 8, top - 1, top + 4) + rect(x0 + 4, x0 + 5, top + 3, top + 6)
        w = windows(x0 + 1, x1 - 1, GROUND + 2, top - 4)
        if w is not None:
            holes = w if holes is None else holes + w
    outline = outline - holes
    return plate_parts("nyc_city", outline, PINK, [("empire_state", empire_state(), RED, 1.2)])


@step(out="../STEP/nyc_city.step")
@stl(out="../STL/nyc_city.stl")
@threemf(out="../3MF/nyc_city.3mf")
def nyc_city():
    return plate_compound("nyc_city", city_parts())


if __name__ == "__main__":
    nyc_city()
