"""Toadstool plaque wall planter.

A flat white back plaque with two screw tabs carries relief toadstools and
a rounded pot. The pot's front is decorated with more toadstools, fern
fronds and curling tendrils that follow its curved surface. Three small
drain holes sit at the bottom of the pot.
"""

from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import glb, step, stl, threemf

from japan_common import circle, poly, rect, stroke
from mushroom_common import (
    CREAM, GREEN, RED, SPOT, STEM, WHITE, box, ellipse, ellipsoid, mushroom, resolve, wall_side,
)

PLATE_T = 5.0
POT_C = (0.0, 0.0, 95.0)
POT_R = (90.0, 62.0, 70.0)
WALL = 3.5
TABS = [(-98.0, 172.0), (98.0, 172.0)]
SCREW_D = 4.5
RELIEF = 0.45  # depth squash for relief toadstools
# (x, z) of the stem foot, height, cap radius, cap height, stem radius, lean
PLAQUE_SHROOMS = [((0, 112), 76, 46, 24, 8, 0), ((-56, 100), 52, 27, 15, 5.5, -6), ((56, 106), 56, 28, 15, 5.5, 6),
                  ((36, 97), 28, 13, 8, 3, 4), ((66, 96), 22, 10, 6, 2.5, 10)]
# Flat toadstools wrapped onto the pot: (x, z) of the stem foot, height, cap radius, cap height
POT_SHROOMS = [((-52, 44), 30, 20, 12), ((52, 46), 30, 20, 12), ((4, 34), 20, 13, 8), ((-24, 36), 14, 9, 6),
               ((74, 58), 12, 8, 5), ((-74, 58), 12, 8, 5)]
FERNS = [((-36, 26), (-30, 80)), ((28, 26), (22, 78))]
TENDRILS = [((-12, 40), (-20, 66)), ((76, 52), (84, 80)), ((-80, 60), (-84, 84))]


def plaque_outline():
    shape = ellipse(0, 165, 90, 72) + rect(-90, 90, 30, 165)
    for x, z in TABS:
        shape = shape + circle(x, z, 11) + rect(min(x, x * 0.85), max(x, x * 0.85), z - 11, z + 11)
    for x, z in TABS:
        shape = shape - circle(x, z, SCREW_D / 2)
    return shape


def to_wall(sketch_solid):
    """Sketch XY extruded +Z -> wall XZ, thickness toward -Y."""
    return sketch_solid.rotate(bd.Axis.X, 90)


def pot_outer():
    return ellipsoid(POT_C, *POT_R) & box(-200, 200, -200, 0, -100, POT_C[2])


def pot_cavity():
    rx, ry, rz = POT_R
    return ellipsoid(POT_C, rx - WALL, ry - WALL, rz - WALL) & box(-200, 200, -200, -PLATE_T, -100, POT_C[2] + 1)


def relief_mushroom(x, z, h, cr, ch, sr, lean, back_y, spots):
    parts = mushroom((0, 0, 0), h, cr, ch, sr, lean, 0.0, spots=spots, gill_slots=20)
    return [bd.scale(p, by=(1, RELIEF, 1)).moved(bd.Location((x, back_y, z))) for p in parts]


def fern(start, end, width=1.4, leaflets=9):
    (xa, za), (xb, zb) = start, end
    shape = stroke([start, end], width)
    length = math.hypot(xb - xa, zb - za)
    ux, uz = (xb - xa) / length, (zb - za) / length
    for i in range(1, leaflets + 1):
        t = i / (leaflets + 1)
        px, pz = xa + ux * length * t, za + uz * length * t
        span = 9 * (1 - t * 0.7)
        for side in (-1, 1):
            nx, nz = -uz * side, ux * side
            tip = (px + (nx * 0.9 + ux * 0.5) * span, pz + (nz * 0.9 + uz * 0.5) * span)
            shape = shape + stroke([(px, pz), tip], 1.6)
    return shape


def tendril(start, end):
    (xa, za), (xb, zb) = start, end
    shape = stroke([start, ((xa + xb) / 2 + 4, (za + zb) / 2), end], 1.6)
    curl = circle(xb, zb, 3.4) - circle(xb, zb, 2.0) - rect(xb - 4, xb, zb - 4, zb)
    return shape + curl + circle(xa, za, 0.1)


def on_pot(sketch, depth: float = 1.2):
    """2D decoration wrapped onto the pot's outer surface, `depth` proud."""
    rx, ry, rz = POT_R
    skin = (ellipsoid(POT_C, rx + depth, ry + depth, rz + depth) - ellipsoid(POT_C, rx - 0.6, ry - 0.6, rz - 0.6))
    skin = skin & box(-200, 200, -200, -PLATE_T, -100, POT_C[2] - 3)
    solids = [bd.Solid.extrude(f, (0, 0, 120)) for f in sketch.faces()]
    prism = to_wall(solids[0].fuse(*solids[1:]).clean() if len(solids) > 1 else solids[0])
    return prism.moved(bd.Location((0, 1, 0))) & skin


def flat_toadstool(x, z, h, cr, ch):
    """Front-view toadstool as (stem, gills, cap, spots) sketches."""
    stem = poly([(x - cr * 0.22, z), (x + cr * 0.22, z), (x + cr * 0.15, z + h), (x - cr * 0.15, z + h)])
    dome = ellipse(x, z + h, cr, ch) & rect(x - cr - 1, x + cr + 1, z + h, z + h + ch + 1)
    gills = ellipse(x, z + h, cr * 0.92, ch * 0.28) & rect(x - cr, x + cr, z + h - ch, z + h)
    dots = None
    for dx, dz, r in ((-0.5, 0.35, 0.13), (0.1, 0.62, 0.15), (0.55, 0.3, 0.12), (-0.15, 0.2, 0.1), (0.35, 0.72, 0.09)):
        c = circle(x + dx * cr, z + h + dz * ch, r * cr)
        dots = c if dots is None else dots + c
    return stem, gills, dome - dots, dots & dome


def plaque_parts():
    faces = [bd.Solid.extrude(f, (0, 0, PLATE_T)) for f in plaque_outline().faces()]
    plate = to_wall(faces[0].fuse(*faces[1:]).clean() if len(faces) > 1 else faces[0])
    main = plate + pot_outer()
    rim = (ellipsoid(POT_C, POT_R[0] + 2.5, POT_R[1] + 2.5, POT_R[2]) - ellipsoid(POT_C, POT_R[0] - WALL, POT_R[1] - WALL, POT_R[2])) & box(
        -200, 200, -200, 0, -100, POT_C[2]) & box(-200, 200, -200, 0, POT_C[2] - 5, POT_C[2])
    main = main + rim
    for x in (-14.0, 0.0, 14.0):  # drain holes through the bottom
        z = POT_C[2] - POT_R[2] + 8
        main = main - bd.Solid.make_cylinder(2.5, 20, bd.Plane((x, -12, z - 12)))
    main = main - pot_cavity()

    stems, gills, caps, spots = [], [], [], []
    for (x, z), h, cr, ch, sr, lean in PLAQUE_SHROOMS:
        s, g, c, d = relief_mushroom(x, z, h, cr, ch, sr, lean, -PLATE_T + 1, 14 if cr > 30 else 8)
        stems.append(s); gills.append(g); caps.append(c); spots.append(d)
    for (x, z), h, cr, ch in POT_SHROOMS:
        s, g, c, d = flat_toadstool(x, z, h, cr, ch)
        stems.append(on_pot(s, 1.4)); gills.append(on_pot(g, 1.6)); caps.append(on_pot(c, 2.0)); spots.append(on_pot(d, 2.3))

    leaves = None
    for a, b in FERNS:
        f = fern(a, b)
        leaves = f if leaves is None else leaves + f
    for a, b in TENDRILS:
        leaves = leaves + tendril(a, b)
    moss = rect(-90, 90, 20, 36)
    decor = on_pot(leaves, 1.2)
    moss_band = on_pot(moss, 0.8)

    cut = wall_side()
    fuse = lambda group: (group[0].fuse(*group[1:]).clean() if len(group) > 1 else group[0]) & cut
    parts = [("plaque_and_pot", main, WHITE),
             ("spots", fuse(spots), SPOT), ("caps", fuse(caps), RED), ("gills", fuse(gills), CREAM),
             ("stems", fuse(stems) - pot_cavity(), STEM),
             ("ferns", decor - pot_cavity(), GREEN), ("moss", moss_band - pot_cavity(), GREEN)]
    return resolve(parts)


@step(out="../STEP/mushroom_planter_plaque.step")
@glb(out="../GLB/mushroom_planter_plaque.glb")
@stl(out="../STL/mushroom_planter_plaque.stl")
@threemf(out="../3MF/mushroom_planter_plaque.3mf")
def mushroom_planter_plaque():
    return bd.Compound(children=plaque_parts(), label="mushroom_planter_plaque")


if __name__ == "__main__":
    mushroom_planter_plaque()
