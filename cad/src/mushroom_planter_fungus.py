"""Bracket-fungus wall planter with a cluster of toadstools.

A half bowl with ribbed sides and a cream lower band hangs on a root-like
bracket with two counterbored screw holes. Six toadstools rise from behind
the rim, their stems fused to the back plate. Drain holes sit along the
bottom of the band.
"""

from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import glb, step, stl, threemf

from japan_common import poly, rect, stroke
from mushroom_common import (
    BARK, CREAM, ORANGE, RUST, SPOT, box, ellipse, ellipsoid, mushroom, resolve, screw_hole, wall_side,
)

BOWL_C = (0.0, 0.0, 100.0)
BOWL_R = (95.0, 75.0, 70.0)
WALL = 3.5
BACK_T = 4.0
BAND_TOP = 52.0
RIBS = 44
BRACKET = [(-56, 40), (-50, 24), (-46, 10), (-30, 6), (-14, -6), (-10, -20), (10, -20), (14, -6), (30, 6),
           (46, 10), (50, 24), (56, 40), (40, 70), (-40, 70)]
BRACKET_DEPTH = 16.0
SCREWS = [(-38.0, 18.0), (38.0, 18.0)]
DRAINS = [230.0, 270.0, 310.0]
# base (x, y, z), height, cap radius, cap height, stem radius, lean x, lean forward, cap color
# Stems stand against the back plate (their axis BACK_T + 0.6 * stem radius out
# from the wall) so every mushroom is carried by the plate when printed on its back.
MUSHROOMS = [
    ((-4, 86), 108, 50, 28, 10, 6, RUST),
    ((-46, 86), 90, 28, 17, 6.5, -18, RUST),
    ((-72, 82), 50, 19, 12, 5, -32, ORANGE),
    ((-22, 92), 56, 14, 9, 4, -6, RUST),
    ((54, 86), 80, 26, 16, 6, 16, ORANGE),
    ((74, 86), 44, 15, 10, 4.5, 26, ORANGE),
]


def bowl_shell():
    rx, ry, rz = BOWL_R
    outer = ellipsoid(BOWL_C, rx, ry, rz) & box(-200, 200, -200, 0, -100, BOWL_C[2])
    # vertical ribs: shallow grooves fanning around the front
    skin = outer - ellipsoid(BOWL_C, rx - 1.2, ry - 1.2, rz - 1.2)
    grooves = []
    for k in range(RIBS):
        a = 185 + k * 170 / (RIBS - 1)
        slot = box(0, 200, -0.7, 0.7, BOWL_C[2] - rz - 2, BOWL_C[2] - 6).rotate(bd.Axis.Z, a)
        grooves.append(slot.moved(bd.Location((BOWL_C[0], BOWL_C[1], 0))))
    groove_cut = grooves[0].fuse(*grooves[1:]) & skin
    shell = outer - groove_cut
    # rounded lip around the rim
    lip = (ellipsoid(BOWL_C, rx + 2, ry + 2, rz) - ellipsoid(BOWL_C, rx - WALL, ry - WALL, rz)) & box(
        -200, 200, -200, 0, BOWL_C[2] - 4, BOWL_C[2])
    return shell + lip


def soil_cavity():
    rx, ry, rz = BOWL_R
    inner = ellipsoid(BOWL_C, rx - WALL, ry - WALL, rz - WALL)
    return inner & box(-200, 200, -200, -BACK_T, -100, BOWL_C[2] + 1)


def drains():
    out = None
    for a in DRAINS:
        t = math.radians(a)
        d = (math.cos(t) * 0.6, math.sin(t) * 0.6, -0.8)
        n = math.sqrt(sum(v * v for v in d))
        d = tuple(v / n for v in d)
        rx, ry, rz = BOWL_R
        z = BAND_TOP - 16
        s = math.sqrt(max(0.0, 1 - ((z - BOWL_C[2]) / rz) ** 2))
        p = (rx * s * math.cos(t) * 0.92, ry * s * math.sin(t) * 0.92, z)
        h = bd.Solid.make_cylinder(2.5, 30, bd.Plane((p[0] - d[0] * 15, p[1] - d[1] * 15, p[2] - d[2] * 15), z_dir=d))
        out = h if out is None else out + h
    return out


def back_plate():
    """Flat plate on the wall behind the bowl and every mushroom."""
    rx, _, rz = BOWL_R
    shape = ellipse(BOWL_C[0], BOWL_C[2], rx, rz) & rect(-200, 200, -100, BOWL_C[2] + 10)
    for (x, z), h, cr, ch, sr, lean, _ in MUSHROOMS:
        a = math.radians(lean)
        top = (x + h * math.sin(a), z + h * math.cos(a))
        shape = shape + stroke([(x, z), top], sr * 2.2)
        cap = ellipse(0, 0, cr * 0.92, ch * 0.75).rotate(bd.Axis.Z, -lean)
        shape = shape + cap.moved(bd.Location((top[0] + ch * 0.35 * math.sin(a), top[1] + ch * 0.35 * math.cos(a), 0)))
    faces = [bd.Solid.extrude(f, (0, 0, BACK_T)) for f in shape.faces()]
    plate = faces[0].fuse(*faces[1:]).clean() if len(faces) > 1 else faces[0]
    return plate.rotate(bd.Axis.X, 90)  # sketch XY -> wall XZ, thickness toward -Y


def bracket():
    face = poly(BRACKET)
    body = bd.Solid.extrude(face, (0, 0, BRACKET_DEPTH))
    body = body.rotate(bd.Axis.X, 90)  # XY profile -> XZ, extruded toward -Y
    return body


def fungus_parts():
    main = bowl_shell() + back_plate() + bracket()
    main = main - soil_cavity() - drains()
    for x, z in SCREWS:
        main = main - screw_hole(x, z, BRACKET_DEPTH, BRACKET_DEPTH - 5)
    band = main & box(-200, 200, -200, 0, -100, BAND_TOP)
    upper = main - band
    bracket_part = band & box(-200, 200, -200, 0, -100, BOWL_C[2] - BOWL_R[2] + 2)
    band = band - bracket_part

    parts = [("bowl", upper, RUST), ("bowl_band", band, CREAM), ("bracket", bracket_part, BARK)]
    stems, gills, caps_rust, caps_orange, spots = [], [], [], [], []
    for (x, z), h, cr, ch, sr, lx, color in MUSHROOMS:
        base = (x, -(BACK_T + 0.6 * sr), z)
        stem, gill, cap, dots = mushroom(base, h, cr, ch, sr, lx, 0.0, spots=12 if cr > 30 else 7)
        stems.append(stem)
        gills.append(gill)
        (caps_rust if color == RUST else caps_orange).append(cap)
        spots.append(dots)
    cut = wall_side()
    for label, group, color in (("caps_rust", caps_rust, RUST), ("caps_orange", caps_orange, ORANGE),
                                ("spots", spots, SPOT), ("gills", gills, CREAM), ("stems", stems, BARK)):
        body = group[0].fuse(*group[1:]).clean() if len(group) > 1 else group[0]
        parts.append((label, body & cut, color))
    # soil cavity stays clear of stems
    parts = [(l, (s - soil_cavity()) if l == "stems" else s, c) for l, s, c in parts]
    return resolve(parts)


@step(out="../STEP/mushroom_planter_fungus.step")
@glb(out="../GLB/mushroom_planter_fungus.glb")
@stl(out="../STL/mushroom_planter_fungus.stl")
@threemf(out="../3MF/mushroom_planter_fungus.3mf")
def mushroom_planter_fungus():
    return bd.Compound(children=fungus_parts(), label="mushroom_planter_fungus")


if __name__ == "__main__":
    mushroom_planter_fungus()
