"""Space layer of the plaque: stars, a crescent moon, the battle station
and two fighters on a dark sky."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import step, stl, threemf

from japan_common import circle, poly, rect, stroke
from starwars_common import (
    FIT, DARK_GREY, GREY, LIGHT_GREY, SPACE, T_SPACE, WHITE, Z_SPACE, bolt_holes, layer_parts, opening, outline, star,
)
from starwars_hills import hills_base
from starwars_ship import ship_outline

STATION = (22.0, 82.0, 34.0)
DISH = (8.0, 96.0, 9.0)
MOON = (-52.0, 92.0, 13.0)
STARS = [(-30, 110, 5), (-12, 116, 4), (-64, 70, 3.5), (-50, 112, 3), (-24, 94, 3), (68, 40, 5.5),
         (74, 66, 3.5), (-8, 70, 4.5), (56, 112, 3), (40, 18, 3), (-78, 102, 3), (80, 20, 3)]
DOTS = [(-44, 104), (-70, 86), (-20, 82), (62, 56), (-56, 58), (34, 120), (-4, 104), (76, 50), (-36, 62)]
FIGHTERS = [(-64.0, 56.0, 1.0), (-28.0, 72.0, 0.7)]
STREAKS = [[(-34.0, 30.0), (-74.0, 40.0)], [(-30.0, 22.0), (-64.0, 26.0)], [(-30.0, 14.0), (-70.0, 12.0)],
           [(-26.0, 6.0), (-58.0, -2.0)]]


def station():
    x, y, r = STATION
    body = circle(x, y, r)
    # surface plating in the upper right, like the reference
    for dx, dy, w, h in ((12, 18, 10, 3), (18, 10, 12, 3), (22, 2, 9, 3), (8, 24, 8, 3), (24, 16, 6, 2.5),
                         (14, -8, 12, 2.5), (20, -16, 9, 2.5), (4, -20, 10, 2.5)):
        body = body - rect(x + dx, x + dx + w, y + dy, y + dy + h)
    return body


def trench():
    x, y, r = STATION
    return circle(x, y, r) & rect(x - r, x + r, y - 4.5, y - 2)


def dish():
    x, y, r = DISH
    return circle(x, y, r) - circle(x, y, r - 2.2)


def dish_core():
    x, y, r = DISH
    return circle(x, y, 2.5) + rect(x - r + 2, x + r - 2, y - 0.5, y + 0.5) + rect(x - 0.5, x + 0.5, y - r + 2, y + r - 2)


def moon():
    x, y, r = MOON
    return circle(x, y, r) - circle(x + 6, y + 4, r - 1.5)


def fighter(cx: float, cy: float, s: float):
    """Twin-wing fighter seen face on: two tall hexagonal wings and a ball cockpit."""
    def wing(x):
        return poly([(x - 3 * s, cy - 14 * s), (x + 3 * s, cy - 14 * s), (x + 4.5 * s, cy), (x + 3 * s, cy + 14 * s),
                     (x - 3 * s, cy + 14 * s), (x - 4.5 * s, cy)])
    shape = wing(cx - 12 * s) + wing(cx + 12 * s) + rect(cx - 12 * s, cx + 12 * s, cy - 1.6 * s, cy + 1.6 * s)
    return shape + circle(cx, cy, 5.5 * s)


def fighter_detail(cx: float, cy: float, s: float):
    window = circle(cx, cy, 2.6 * s)
    for x in (cx - 12 * s, cx + 12 * s):  # wing spokes
        window = window + rect(x - 0.5 * s, x + 0.5 * s, cy - 12 * s, cy + 12 * s)
        window = window + rect(x - 3 * s, x + 3 * s, cy - 0.5 * s, cy + 0.5 * s)
    return window


def space_parts():
    stars = None
    for x, y, r in STARS:
        stars = star(x, y, r) if stars is None else stars + star(x, y, r)
    for x, y in DOTS:
        stars = stars + circle(x, y, 1.0)
    streaks = None
    for pts in STREAKS:
        s = stroke(pts, 1.4)
        streaks = s if streaks is None else streaks + s
    fighters = None
    details = None
    for f in FIGHTERS:
        fighters = fighter(*f) if fighters is None else fighters + fighter(*f)
        details = fighter_detail(*f) if details is None else details + fighter_detail(*f)
    # Details only where the sky shows, so nothing stands under the layers above
    visible = opening(-FIT) - hills_base() - bd.offset(ship_outline(), FIT)
    features = [

        ("stars", stars, WHITE, 0.8),
        ("streaks", streaks, WHITE, 0.6),
        ("moon", moon(), WHITE, 1.0),
        ("station", station(), LIGHT_GREY, 1.0),
        ("station_trench", trench(), DARK_GREY, 0.6),
        ("station_dish", dish(), DARK_GREY, 1.2),
        ("station_dish_core", dish_core(), DARK_GREY, 1.4),
        ("fighters", fighters, GREY, 1.4),
        ("fighter_details", details, DARK_GREY, 1.8),
    ]
    features = [(label, shape & visible, color, relief) for label, shape, color, relief in features]
    return layer_parts("space", outline() - bolt_holes(), SPACE, features, T_SPACE, Z_SPACE)


@step(out="../STEP/starwars_space.step")
@stl(out="../STL/starwars_space.stl")
@threemf(out="../3MF/starwars_space.3mf")
def starwars_space():
    return bd.Compound(children=space_parts(), label="space")


if __name__ == "__main__":
    starwars_space()
