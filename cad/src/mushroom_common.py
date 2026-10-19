"""Shared shapes for the mushroom wall planters.

Units are millimeters. The wall is the XZ plane at Y=0, the planter stands
out toward -Y and +Z is up. Every part is cut flat at Y=0 so the planter
lies on its back for printing and sits flat on the wall.
"""

from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import srgb

CREAM = srgb("#EBD9BC")
SPOT = srgb("#F4EEDC")
RUST = srgb("#A0503A")
ORANGE = srgb("#E08A3C")
RED = srgb("#C8302B")
BARK = srgb("#8E5A3C")
STEM = srgb("#E3D2B0")
WHITE = srgb("#EEEBE3")
GREEN = srgb("#4E8A3A")


def ellipse(cx: float, cy: float, rx: float, ry: float) -> bd.Face:
    """Flat ellipse in a sketch's XY plane."""
    return bd.Face(bd.Wire.make_ellipse(rx, ry, bd.Plane((cx, cy, 0))))


def ellipsoid(c, rx: float, ry: float, rz: float) -> bd.Solid:
    return bd.scale(bd.Solid.make_sphere(1.0), by=(rx, ry, rz)).moved(bd.Location(c))


def box(x0, x1, y0, y1, z0, z1) -> bd.Solid:
    return bd.Solid.make_box(x1 - x0, y1 - y0, z1 - z0, bd.Plane((x0, y0, z0)))


def wall_side() -> bd.Solid:
    """Everything in front of the wall."""
    return box(-400, 400, -400, 0, -400, 400)


def mushroom(base, height: float, cap_r: float, cap_h: float, stem_r: float,
             lean_x: float = 0.0, lean_fwd: float = 0.0, spots: int = 10, gill_slots: int = 28):
    """A toadstool as (stem, gills, cap, spots) solids.

    Built upright with the stem foot at the origin, then leaned `lean_x`
    degrees toward +X and `lean_fwd` degrees toward -Y (away from the wall)
    and moved to `base`.
    """
    stem = bd.Solid.make_loft([
        bd.Wire.make_circle(stem_r * 1.35, bd.Plane((0, 0, 0))),
        bd.Wire.make_circle(stem_r, bd.Plane((0, 0, height * 0.35))),
        bd.Wire.make_circle(stem_r * 0.8, bd.Plane((0, 0, height + 1))),
    ])
    top = height
    dome = ellipsoid((0, 0, top), cap_r, cap_r, cap_h) & box(-cap_r - 1, cap_r + 1, -cap_r - 1, cap_r + 1, top, top + cap_h + 1)
    hollow = ellipsoid((0, 0, top), cap_r * 0.9, cap_r * 0.9, cap_h * 0.38)
    cap = dome - hollow
    gills = (ellipsoid((0, 0, top), cap_r * 0.9, cap_r * 0.9, cap_h * 0.38)
             - ellipsoid((0, 0, top), cap_r * 0.86, cap_r * 0.86, cap_h * 0.2))
    gills = gills & box(-cap_r, cap_r, -cap_r, cap_r, top - 1, top + cap_h)
    for k in range(gill_slots):
        slot = box(stem_r * 0.9, cap_r, -0.45, 0.45, top - 1, top + cap_h * 0.32).rotate(bd.Axis.Z, k * 360 / gill_slots)
        gills = gills - slot

    dots = []
    golden = math.radians(137.5)
    for i in range(spots):
        phi = math.radians(18 + 52 * (i + 0.5) / spots)
        theta = i * golden
        r_dot = cap_r * (0.12 if i % 3 else 0.09)
        p = (cap_r * math.sin(phi) * math.cos(theta), cap_r * math.sin(phi) * math.sin(theta), top + cap_h * math.cos(phi))
        dots.append(bd.scale(bd.Solid.make_sphere(1.0), by=(r_dot, r_dot, r_dot * 0.6)).moved(bd.Location(p)))
    spot_body = dots[0].fuse(*dots[1:]).clean() if len(dots) > 1 else dots[0]
    cap = cap - spot_body
    spot_body = spot_body - ellipsoid((0, 0, top), cap_r * 0.97, cap_r * 0.97, cap_h * 0.97)
    stem = stem - ellipsoid((0, 0, top), cap_r * 0.9, cap_r * 0.9, cap_h * 0.38)

    def place(s):
        s = s.rotate(bd.Axis.X, lean_fwd).rotate(bd.Axis.Y, lean_x)
        return s.moved(bd.Location(base))

    return [place(s) for s in (stem, gills, cap, spot_body)]


def resolve(parts):
    """Subtract each part from those after it so no two bodies overlap.

    `parts` are (label, solid, color) in priority order; empty results drop out.
    """
    out = []
    for label, solid, color in parts:
        for _, prev, _ in out:
            if solid.bounding_box().overlaps(prev.bounding_box(), 0.0) if hasattr(solid.bounding_box(), "overlaps") else True:
                solid = solid - prev
        solids = solid.solids() if hasattr(solid, "solids") else [solid]
        solids = [s for s in solids if s.volume > 1.0]
        if not solids:
            continue
        body = solids[0] if len(solids) == 1 else bd.Compound(solids)
        out.append((label, body, color))
    result = []
    for label, body, color in out:
        body.label = label
        body.color = color
        result.append(body)
    return result


def screw_hole(x: float, z: float, depth: float, head_depth: float, d: float = 4.5, head_d: float = 9.0):
    """Hole along Y from the wall face out, with a counterbore from the front."""
    hole = bd.Solid.make_cylinder(d / 2, depth + 2, bd.Plane((x, 1, z), z_dir=(0, -1, 0)))
    bore_start = -(depth - head_depth)
    return hole + bd.Solid.make_cylinder(head_d / 2, head_depth + 2, bd.Plane((x, bore_start, z), z_dir=(0, -1, 0)))
