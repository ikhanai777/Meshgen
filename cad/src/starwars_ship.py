"""Ship layer of the plaque: a saucer-shaped freighter flying up and to the
right, printed 6 mm thick so it stands out from the scene."""

from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import step, stl, threemf

from japan_common import circle, poly, rect, stroke
from starwars_common import DARK_GREY, GREY, LIGHT_GREY, ORANGE, T_SHIP, Z_TOP, layer_parts

CENTER = (-2.0, 30.0)
HEADING = 28.0  # degrees, nose direction
SCALE = 1.0
HULL_R = 34.0


def _place(shape):
    return shape.rotate(bd.Axis.Z, HEADING).scale(SCALE).moved(bd.Location((*CENTER, 0)))


def hull():
    """Ship outline in its own frame: nose (the two prongs) toward +X."""
    r = HULL_R
    shape = circle(0, 0, r)
    for sy in (-1, 1):  # forward prongs with a slot between them
        y0, y1 = sorted((sy * 5.5, sy * 17.0))
        shape = shape + rect(10, 50, y0, y1) + circle(50, (y0 + y1) / 2, (y1 - y0) / 2)
    shape = shape + stroke([(6, -26), (28, -35)], 9.0) + circle(31, -36, 7.0)  # side cockpit
    return shape


def plating():
    """Lighter raised hull panels."""
    shape = circle(0, 0, 14) - circle(0, 0, 9)
    for a in (110, 150, 200, 240, 290):  # radial panels around the hub
        t = math.radians(a)
        x, y = 22 * math.cos(t), 22 * math.sin(t)
        shape = shape + rect(x - 4, x + 4, y - 2.5, y + 2.5).rotate(bd.Axis((x, y, 0), (0, 0, 1)), a)
    for sy in (-1, 1):
        y0, y1 = sorted((sy * 8.0, sy * 14.0))
        shape = shape + rect(22, 46, y0, y1)
    return shape


def dark_details():
    shape = circle(-8, 16, 7.5) - circle(-8, 16, 5.5) + circle(-8, 16, 2.0)  # radar dish
    shape = shape + circle(0, 0, 5.0)  # gun turret
    shape = shape + circle(31, -36, 4.0)  # cockpit window
    for k in range(3):
        shape = shape + rect(-24 + k * 6, -21 + k * 6, -20, -12)
    return shape


def engine_glow():
    r = HULL_R
    return (circle(0, 0, r) - circle(0, 0, r - 4)) & poly([(0, 0), (-60, -30), (-60, 30)])


def accents():
    return rect(30, 36, 9, 13) + rect(14, 19, -25, -21) + rect(-16, -11, 22, 26) + rect(24, 28, -14, -10)


def ship_outline():
    return _place(hull())


def ship_parts():
    return layer_parts("ship", ship_outline(), GREY, [
        ("plating", _place(plating()), LIGHT_GREY, 1.2),
        ("details", _place(dark_details()), DARK_GREY, 1.6),
        ("engine_glow", _place(engine_glow()), ORANGE, 1.0),
        ("accents", _place(accents()), ORANGE, 1.4),
    ], T_SHIP, Z_TOP)


@step(out="../STEP/starwars_ship.step")
@stl(out="../STL/starwars_ship.stl")
@threemf(out="../3MF/starwars_ship.3mf")
def starwars_ship():
    return bd.Compound(children=ship_parts(), label="ship")


if __name__ == "__main__":
    starwars_ship()
