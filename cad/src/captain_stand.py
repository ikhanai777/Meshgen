"""Base and post of the ship's-wheel phone stand, with "CAPTAIN" lettering.

The base's top carries a ring of wave crests. The tapered post rises behind
the wheel and ends in the wheel's back plane with a square peg that keys the
wheel so it cannot turn.
"""

from __future__ import annotations

import math
from pathlib import Path

from cadgen import build123d as bd
from cadgen import step, stl

from captain_common import (
    BASE_H,
    BASE_R,
    NAVY,
    PEG,
    PEG_LEN,
    SILVER,
    WHEEL_BACK_CENTER,
    wheel_axes,
)

FONT = Path(__file__).resolve().parent.parent / "fonts" / "DejaVuSerif-Bold.ttf"
LABEL = "CAPTAIN"
LABEL_SIZE = 8.0
LABEL_RELIEF = 1.0

BASE_CHAMFER = 1.0
# Wave rings: (count, ring radius, tangential, radial, height, angle offset)
WAVE_RINGS = [
    (16, 41.0, 10.0, 6.0, 6.0, 0.0),
    (12, 29.0, 9.0, 5.0, 4.5, 15.0),
]

POST_BOTTOM = ((-24.0, 6.0), (24.0, 46.0))  # (x, y) corners at the base
POST_TOP_HALF = (11.0, 13.0)  # half width, half height in the wheel plane


def _add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def _mul(v, k):
    return tuple(x * k for x in v)


def base() -> bd.Solid:
    body = bd.Solid.make_cylinder(BASE_R, BASE_H)
    body = bd.chamfer(body.edges().filter_by(bd.GeomType.CIRCLE), BASE_CHAMFER)

    waves = []
    for count, ring_r, tan, rad, h, offset in WAVE_RINGS:
        for i in range(count):
            a = offset + i * 360.0 / count
            crest = bd.scale(bd.Solid.make_sphere(1.0), by=(rad, tan, h))
            crest = crest.rotate(bd.Axis.Z, a)
            crest = crest.moved(
                bd.Location(
                    (ring_r * math.cos(math.radians(a)), ring_r * math.sin(math.radians(a)), BASE_H - 0.5)
                )
            )
            waves.append(crest)
    keep = bd.Solid.make_cylinder(BASE_R - BASE_CHAMFER - 0.5, 30, bd.Plane((0, 0, BASE_H - 1)))
    for crest in waves:
        body += crest & keep
    return body


def post() -> bd.Solid:
    (x0, y0), (x1, y1) = POST_BOTTOM
    z = BASE_H - 2.0
    bottom = bd.Wire.make_polygon([(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z)], close=True)
    up, _ = wheel_axes()
    hx, hu = POST_TOP_HALF
    corners = []
    for sx, su in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        corners.append(_add(_add(WHEEL_BACK_CENTER, (sx * hx, 0, 0)), _mul(up, su * hu)))
    top = bd.Wire.make_polygon(corners, close=True)
    return bd.Solid.make_loft([bottom, top], ruled=True)


def peg() -> bd.Solid:
    up, normal = wheel_axes()
    plane = bd.Plane(WHEEL_BACK_CENTER, x_dir=(1, 0, 0), z_dir=normal)
    body = bd.Solid.make_box(PEG, PEG, PEG_LEN, bd.Plane((-PEG / 2, -PEG / 2, -1.0)))
    tip = body.edges().group_by(bd.Axis.Z)[-1]
    body = bd.chamfer(tip, 1.0)
    return plane.location * body


def stand_body() -> bd.Solid:
    part = (base() + post() + peg()).clean()
    solids = part.solids()
    if len(solids) != 1:
        raise ValueError(f"stand should be one solid, got {len(solids)}")
    return solids[0]


def lettering() -> bd.Shape:
    """Raised letters wrapped around the base's front (-Y) side."""
    text = bd.Text(LABEL, LABEL_SIZE, font_path=str(FONT), align=(bd.Align.CENTER, bd.Align.CENTER))
    zc = BASE_H / 2
    glyphs = []
    for face in text.faces():
        c = face.center()
        theta = c.X / BASE_R
        radial = (math.sin(theta), -math.cos(theta), 0.0)
        tangent = (math.cos(theta), math.sin(theta), 0.0)
        local = bd.extrude(face.moved(bd.Location((-c.X, -c.Y, 0))), LABEL_RELIEF + 0.4)
        origin = (radial[0] * (BASE_R - 0.4), radial[1] * (BASE_R - 0.4), zc)
        glyphs.append(bd.Plane(origin, x_dir=tangent, z_dir=radial).location * local)
    return bd.Compound(glyphs).fuse()


@step(out="../STEP/captain_stand.step")
@stl(out="../STL/captain_stand.stl")
def captain_stand():
    body = stand_body()
    body.label = "stand"
    body.color = NAVY
    letters = lettering()
    letters.label = "lettering"
    letters.color = SILVER
    return bd.Compound(children=[body, letters], label="captain_stand")


if __name__ == "__main__":
    captain_stand()
