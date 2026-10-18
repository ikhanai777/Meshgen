"""Hills layer of the plaque: layered ridges and a far-off city, with a
pocket the ship drops into."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import step, stl, threemf

from japan_common import circle, rect, wave
from starwars_common import FIT, LIGHT_GREY, NAVY, SLATE, T_HILLS, Z_TOP, layer_parts, opening
from starwars_ship import ship_outline

RIDGE = [(-100.0, 22.0), (-75.0, 30.0), (-52.0, 12.0), (-28.0, 0.0), (0.0, -10.0), (30.0, -6.0),
         (55.0, 10.0), (75.0, 8.0), (100.0, 4.0)]
LOW_RIDGE = [(-100.0, 0.0), (-60.0, -8.0), (-20.0, -22.0), (20.0, -26.0), (60.0, -12.0), (100.0, -16.0)]


def city():
    shape = rect(56, 61, 6, 30) + rect(57.8, 59.2, 29, 40)  # thin spire tower
    shape = shape + rect(64, 80, 6, 18) + circle(72, 18, 6)  # domed hall
    shape = shape + rect(71.2, 72.8, 22, 34)
    return shape + rect(83, 87, 6, 26) + rect(84.4, 85.6, 25, 32)


def hills_base():
    return ((wave(RIDGE, -60.0) + city()) & opening(-FIT)) - bd.offset(ship_outline(), FIT)


def hills_parts():
    return layer_parts("hills", hills_base(), NAVY, [
        ("low_ridge", wave(LOW_RIDGE, -60.0), SLATE, 0.8),
        ("city", city(), LIGHT_GREY, 1.2),
    ], T_HILLS, Z_TOP)


@step(out="../STEP/starwars_hills.step")
@stl(out="../STL/starwars_hills.stl")
@threemf(out="../3MF/starwars_hills.3mf")
def starwars_hills():
    return bd.Compound(children=hills_parts(), label="hills")


if __name__ == "__main__":
    starwars_hills()
