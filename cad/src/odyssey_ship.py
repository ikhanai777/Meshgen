"""Flat-backed relief of the Starship Odyssey for the wall plaque.

The ship's back is the plane Z=PLATE_T so it glues flat onto the plate's
front face. The nacelle glow strips are separate inserts, intended for
translucent filament with LEDs or as a contrasting colour.
"""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import step, stl

from odyssey_common import (
    GLOW_COLOR,
    NACELLE_OFFSET,
    PLATE_T,
    SAUCER_R,
    SAUCER_X,
    SHIP_COLOR,
    SHIP_Y,
)

Z0 = PLATE_T  # ship back plane

# Saucer profile: (radius, height above Z0)
SAUCER_PROFILE = [
    (SAUCER_R, 0.0),
    (SAUCER_R, 2.5),
    (SAUCER_R - 4.0, 4.0),
    (22.0, 6.5),
    (12.0, 7.0),
    (8.0, 9.0),
    (0.0, 9.5),
]
SAUCER_GROOVE_R = (24.0, 38.0)
GROOVE_W = 0.8
GROOVE_DEPTH = 0.6

HULL_X = (96.0, 178.0)
HULL_R = 13.0
DEFLECTOR_X = (178.0, 186.0)

NACELLE_X = (104.0, 218.0)
NACELLE_R = 8.0
NACELLE_Z = Z0 + 3.0  # axis height; the nacelle is clipped flat at Z0
GLOW_X = (120.0, 205.0)
GLOW_W = 7.0
GLOW_DEPTH = 2.5


def _x_cylinder(r: float, x0: float, x1: float, y: float, z: float) -> bd.Solid:
    return bd.Solid.make_cylinder(r, x1 - x0, bd.Plane((x0, y, z), z_dir=(1, 0, 0)))


def _x_cone(r0: float, r1: float, x0: float, x1: float, y: float, z: float) -> bd.Solid:
    return bd.Solid.make_cone(r0, r1, x1 - x0, bd.Plane((x0, y, z), z_dir=(1, 0, 0)))


def _clip_to_back(shape: bd.Shape) -> bd.Shape:
    keep = bd.Solid.make_box(600, 600, 100, bd.Plane((-300, -300, Z0)))
    result = shape.intersect(keep)
    if isinstance(result, list):
        result = result[0] if len(result) == 1 else bd.Compound(result)
    return result


def _saucer_height(r: float) -> float:
    for (r0, z0), (r1, z1) in zip(SAUCER_PROFILE[2:], SAUCER_PROFILE[3:]):
        if r1 <= r <= r0:
            return z0 + (z1 - z0) * (r0 - r) / (r0 - r1)
    raise ValueError(r)


def saucer() -> bd.Solid:
    pts = [(0.0, 0.0, Z0)] + [(r, 0.0, Z0 + z) for r, z in SAUCER_PROFILE]
    face = bd.Face(bd.Wire.make_polygon(pts, close=True))
    body = bd.Solid.revolve(face, 360, bd.Axis.Z)
    for r in SAUCER_GROOVE_R:
        z = Z0 + _saucer_height(r) - GROOVE_DEPTH
        ring = bd.Solid.make_cylinder(r + GROOVE_W / 2, 5, bd.Plane((0, 0, z))) - bd.Solid.make_cylinder(
            r - GROOVE_W / 2, 5, bd.Plane((0, 0, z))
        )
        body = body - ring
    return bd.Location((SAUCER_X, SHIP_Y, 0)) * body


def neck() -> bd.Solid:
    outline = bd.Polygon(
        (SAUCER_X, SHIP_Y - 14),
        (80.0, SHIP_Y - 13),
        (HULL_X[0], SHIP_Y - 8),
        (HULL_X[0], SHIP_Y + 8),
        (80.0, SHIP_Y + 13),
        (SAUCER_X, SHIP_Y + 14),
        align=None,
    )
    body = bd.Location((0, 0, Z0)) * bd.extrude(outline, 8.0)
    top = body.edges().group_by(bd.Axis.Z)[-1]
    return bd.chamfer(top, 2.0)


def hull() -> bd.Solid:
    x0, x1 = HULL_X
    body = _x_cylinder(HULL_R, x0, x1, SHIP_Y, Z0)
    body += _x_cone(7.0, HULL_R, x0 - 8.0, x0, SHIP_Y, Z0)
    d0, d1 = DEFLECTOR_X
    body += _x_cylinder(11.0, d0, d1, SHIP_Y, Z0)
    body -= _x_cone(3.0, 8.0, d1 - 3.0, d1 + 0.01, SHIP_Y, Z0)
    return body


def pylon(side: int) -> bd.Solid:
    y_root = SHIP_Y + side * 8.0
    y_tip = SHIP_Y + side * (NACELLE_OFFSET - 4.0)
    pts = [(136.0, y_root), (160.0, y_root), (172.0, y_tip), (152.0, y_tip)]
    if side < 0:
        pts.reverse()  # keep counter-clockwise winding so the extrusion is positive
    outline = bd.Polygon(*pts, align=None)
    return bd.Location((0, 0, Z0)) * bd.extrude(outline, 6.0)


def nacelle(side: int) -> tuple[bd.Solid, bd.Solid]:
    y = SHIP_Y + side * NACELLE_OFFSET
    x0, x1 = NACELLE_X
    body = _x_cylinder(NACELLE_R, x0, x1, y, NACELLE_Z)
    body += bd.Solid.make_sphere(NACELLE_R).moved(bd.Location((x0, y, NACELLE_Z)))
    body += _x_cone(NACELLE_R, 5.0, x1, x1 + 6.0, y, NACELLE_Z)

    g0, g1 = GLOW_X
    slot_z = NACELLE_Z + NACELLE_R - GLOW_DEPTH
    slot = bd.Solid.make_box(g1 - g0, GLOW_W, 10.0, bd.Plane((g0, y - GLOW_W / 2, slot_z)))
    body -= slot
    # The insert fills the slot to just under the nacelle's surface
    glow = slot & _x_cylinder(NACELLE_R - 0.2, g0, g1, y, NACELLE_Z)
    if isinstance(glow, list):
        glow = glow[0]
    return body, glow


@step(out="../STEP/odyssey_ship.step")
@stl(out="../STL/odyssey_ship.stl")
def odyssey_ship():
    body = saucer() + neck() + hull()
    glows = []
    for side, where in ((1, "port"), (-1, "starboard")):
        nac, glow = nacelle(side)
        body = body + pylon(side) + nac
        glow.label = f"glow_strip:{where}"
        glow.color = GLOW_COLOR
        glows.append(glow)
    body = _clip_to_back(body.clean())
    body.label = "ship"
    body.color = SHIP_COLOR
    return bd.Compound(children=[body, *glows], label="odyssey_ship")


if __name__ == "__main__":
    odyssey_ship()
