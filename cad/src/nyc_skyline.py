"""Far skyline layer of the New York shadow box: the Brooklyn Bridge, One
World Trade Center, the Chrysler Building and Manhattan towers in black."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import PLATE_X, band, circle, plate_compound, plate_parts, poly, rect, stroke
from nyc_common import NAVY_BLACK

GROUND = 44.0
DECK = (56.0, 61.0)
TOWER_X = -64.0
TOWER = (11.0, 116.0)  # width, top
# (x0, x1, roof)
TOWERS = [(-30, -18, 112), (-18, -6, 128), (16, 26, 120), (26, 34, 104), (58, 70, 132), (70, 80, 112),
          (80, 89, 124)]
WTC_X = 46.0
CHRYSLER_X = 6.0


def brooklyn_bridge():
    d0, d1 = DECK
    w, top = TOWER
    t = TOWER_X
    shape = rect(-PLATE_X, -28, d0, d1)
    tower = rect(t - w / 2, t + w / 2, GROUND - 1, top) + rect(t - w / 2 - 1, t + w / 2 + 1, top - 3, top)
    for dx in (-2.4, 2.4):  # twin pointed arches
        tower = tower - rect(t + dx - 1.6, t + dx + 1.6, d1 + 4, top - 18)
        tower = tower - poly([(t + dx - 1.6, top - 18), (t + dx + 1.6, top - 18), (t + dx, top - 13)])
    shape = shape + tower
    # main cables sagging to the deck on both sides, and a few suspenders
    left = [(t, top - 4), (t - 10, top - 26), (t - 20, top - 42), (-PLATE_X + 3, d1 + 0.5)]
    right = [(t, top - 4), (t + 10, top - 26), (t + 22, top - 44), (-30.0, d1 + 0.5)]
    shape = shape + stroke(left, 1.6) + stroke(right, 1.6)
    for x, y in ((t - 10, top - 26), (t - 20, top - 42), (t + 10, top - 26), (t + 22, top - 44)):
        shape = shape + rect(x - 0.6, x + 0.6, d1 - 0.5, y)
    for x in (-92.0, -40.0):  # bridge piers below the deck
        shape = shape + rect(x - 3 if x > -90 else -PLATE_X, x + 3, GROUND - 1, d0 + 0.5)
    return shape


def one_wtc():
    c = WTC_X
    body = poly([(c - 11, GROUND - 1), (c + 11, GROUND - 1), (c + 4, 172), (c - 4, 172)])
    body = body + rect(c - 4.5, c + 4.5, 170, 175)
    return body + rect(c - 0.9, c + 0.9, 174, 180)


def chrysler():
    c = CHRYSLER_X
    body = rect(c - 8, c + 8, GROUND - 1, 128) + rect(c - 6, c + 6, 127, 136)
    for k in range(4):  # stepped crown arches
        w = 5.5 - k * 1.2
        y = 135 + k * 6
        body = body + rect(c - w, c + w, y, y + 4) + circle(c, y + 4, w)
    return body + poly([(c - 1.2, 158), (c + 1.2, 158), (c, 176)])


def skyline_parts():
    outline = band([(-PLATE_X + 1, GROUND), (PLATE_X - 1, GROUND)])
    for x0, x1, top in TOWERS:
        outline = outline + rect(x0, x1, GROUND - 1, top)
        outline = outline + rect(x0 + 2, x1 - 2, top - 1, top + 5)
    outline = outline + brooklyn_bridge() + one_wtc() + chrysler()
    return plate_parts("nyc_skyline", outline, NAVY_BLACK, [])


@step(out="../STEP/nyc_skyline.step")
@stl(out="../STL/nyc_skyline.stl")
@threemf(out="../3MF/nyc_skyline.3mf")
def nyc_skyline():
    return plate_compound("nyc_skyline", skyline_parts())


if __name__ == "__main__":
    nyc_skyline()
