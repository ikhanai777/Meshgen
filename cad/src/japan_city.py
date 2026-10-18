"""City layer of the Japan shadow box: a pink skyline and a red Tokyo Tower."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import PINK, RED, band, circle, plate_compound, plate_parts, poly, rect

GROUND_TOP = 48.0
# (x0, x1, roof height)
BUILDINGS = [(-89, -76, 118), (-76, -66, 96), (-66, -58, 106), (-58, -50, 74), (-30, -20, 80),
             (-20, -8, 88), (-8, 4, 102), (4, 13, 92), (26, 38, 112), (38, 48, 96), (48, 60, 126),
             (60, 70, 104), (70, 80, 118), (80, 89, 100)]
TOWER_X = -40.0


def tower():
    c = TOWER_X
    outline = poly([(c - 12, GROUND_TOP - 2), (c - 4.5, 92), (c - 3, 120), (c - 1.6, 150), (c - 0.8, 178),
                    (c, 190), (c + 0.8, 178), (c + 1.6, 150), (c + 3, 120), (c + 4.5, 92), (c + 12, GROUND_TOP - 2)])
    outline = outline + rect(c - 6, c + 6, 116, 123) + rect(c - 3, c + 3, 148, 153)
    arch = rect(c - 5, c + 5, GROUND_TOP - 3, 56) + circle(c, 56, 5)
    return outline - arch


def city_parts():
    skyline = band([(-90.0, GROUND_TOP), (90.0, GROUND_TOP)])
    for x0, x1, top in BUILDINGS:
        skyline = skyline + rect(x0, x1, GROUND_TOP - 1, top)
        # a small step on every other roof
        if (x1 - x0) > 10:
            skyline = skyline + rect(x0 + 3, x0 + 6, top - 1, top + 4)
    return plate_parts("city", skyline, PINK, [("tokyo_tower", tower(), RED, 1.2)])


@step(out="../STEP/japan_city.step")
@stl(out="../STL/japan_city.stl")
@threemf(out="../3MF/japan_city.3mf")
def japan_city():
    return plate_compound("city", city_parts())


if __name__ == "__main__":
    japan_city()
