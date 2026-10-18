"""Shared geometry for the Japan layered shadow box.

Units are millimeters. In the assembled box +Z is up and the viewer looks
from -Y. Each layer is a 3 mm plate printed flat: in its own frame the
plate lies on XY with its back face at Z=0, local X is the box's X and local
Y is the box's Z, so the same (x, y) numbers describe the plate in both.

The plates slide down through slots in the frame's top into grooves in the
side walls and the bottom, at the depths in SLOTS. Each plate's colored
details are separate bodies inlaid through the plate and standing RELIEF
proud of its front, so a multi-color printer prints them in one go.
"""

from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import srgb

# Frame
INNER_W = 180.0
INNER_H = 200.0
WALL = 10.0
DEPTH = 66.0
GROOVE = 4.0  # into the side walls and the bottom
SLOT_GAP = 3.4
# Slot centers, measured back from the frame's front face, front to back
SLOTS = {
    "mat": 5.0,
    "front": 16.0,
    "hills": 27.0,
    "city": 38.0,
    "fuji": 49.0,
    "sky": 60.0,
}

# Plates
PLATE_T = 3.0
PLATE_CLEARANCE = 0.3
PLATE_X = INNER_W / 2 + GROOVE - PLATE_CLEARANCE  # half width
PLATE_BOTTOM = WALL - GROOVE + PLATE_CLEARANCE
PLATE_TOP = INNER_H + 2 * WALL  # flush with the frame's top
# Details stand proud only inside the opening, so they never jam a groove
RELIEF_X = INNER_W / 2 - 0.5
RELIEF_Y = (WALL + 0.5, WALL + INNER_H - 0.5)

BLACK = srgb("#1C1C1E")
SKY = srgb("#A9CBE6")
RED = srgb("#D3262B")
PINK = srgb("#F2A7C3")
ROSE = srgb("#9E2B48")
WHITE = srgb("#F4F4F2")


def rect(x0: float, x1: float, y0: float, y1: float) -> bd.Face:
    return bd.Face(bd.Wire.make_polygon([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], close=True))


def poly(pts) -> bd.Face:
    return bd.Face(bd.Wire.make_polygon([(x, y) for x, y in pts], close=True))


def circle(cx: float, cy: float, r: float) -> bd.Face:
    return bd.Face(bd.Wire.make_circle(r, bd.Plane((cx, cy, 0))))


def wave(top, y_bottom: float) -> bd.Face:
    """Face under a smooth curve through `top` points (left to right)."""
    curve = bd.Edge.make_spline([bd.Vector(x, y) for x, y in top])
    (x0, _), (x1, _) = top[0], top[-1]
    a, b = bd.Vector(x1, top[-1][1]), bd.Vector(x0, top[0][1])
    edges = [
        curve,
        bd.Edge.make_line(a, bd.Vector(x1, y_bottom)),
        bd.Edge.make_line(bd.Vector(x1, y_bottom), bd.Vector(x0, y_bottom)),
        bd.Edge.make_line(bd.Vector(x0, y_bottom), b),
    ]
    return bd.Face(bd.Wire(edges))


def band(top, y_bottom: float = PLATE_BOTTOM) -> bd.Face:
    """Full plate-width band whose top edge follows `top` (x inside the plate)."""
    pts = [(-PLATE_X, top[0][1])] + list(top) + [(PLATE_X, top[-1][1])]
    return wave(pts, y_bottom)


def stroke(pts, width: float) -> bd.Shape:
    """A branch-like line of round-ended segments."""
    r = width / 2
    shape = circle(*pts[0], r)
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        length = math.hypot(xb - xa, yb - ya)
        nx, ny = -(yb - ya) / length * r, (xb - xa) / length * r
        shape = shape + poly([(xa + nx, ya + ny), (xb + nx, yb + ny), (xb - nx, yb - ny), (xa - nx, ya - ny)])
        shape = shape + circle(xb, yb, r)
    return shape


def blossom(cx: float, cy: float, size: float = 4.5, turn: float = 0.0) -> bd.Shape:
    """Five-petal cherry blossom about `size` in radius."""
    r = size * 0.48
    shape = circle(cx, cy, size * 0.45)
    for k in range(5):
        a = math.radians(turn + 90 + k * 72)
        shape = shape + circle(cx + size * 0.55 * math.cos(a), cy + size * 0.55 * math.sin(a), r)
    return shape


def _faces(shape) -> list[bd.Face]:
    return list(shape.faces()) if shape is not None else []


def plate_parts(name: str, outline, base_color, features) -> list[bd.Solid]:
    """Bodies of one plate: the base and its inlaid, raised details.

    `features` are (label, shape, color, relief), back to front: a later
    feature covers an earlier one where they overlap.
    """
    full = outline
    for _, shape, _, _ in features:
        full = full + shape
    clip = rect(-RELIEF_X, RELIEF_X, *RELIEF_Y)

    bodies = []
    base = full
    for _, shape, _, _ in features:
        base = base - shape
    bodies.append((name, base, base_color, 0.0))
    for i, (label, shape, color, relief) in enumerate(features):
        visible = shape
        for _, later, _, _ in features[i + 1:]:
            visible = visible - later
        bodies.append((label, visible, color, relief))

    out = []
    for label, shape, color, relief in bodies:
        solids = [bd.Solid.extrude(f, (0, 0, PLATE_T)) for f in _faces(shape)]
        if relief > 0:
            for f in _faces(shape & clip):
                solids.append(bd.Solid.extrude(f, (0, 0, relief)).moved(bd.Location((0, 0, PLATE_T))))
        if not solids:
            continue
        body = solids[0].fuse(*solids[1:]).clean() if len(solids) > 1 else solids[0]
        body.label = label
        body.color = color
        out.append(body)
    return out


def plate_location(slot: str) -> bd.Location:
    """Places a plate (built flat) at its slot in the assembled box."""
    y = SLOTS[slot] + PLATE_T / 2
    return bd.Plane((0, y, 0), x_dir=(1, 0, 0), z_dir=(0, -1, 0)).location


def plate_compound(name: str, parts: list[bd.Solid]) -> bd.Compound:
    return bd.Compound(children=parts, label=name)


def plate_rect() -> bd.Face:
    return rect(-PLATE_X, PLATE_X, PLATE_BOTTOM, PLATE_TOP)
