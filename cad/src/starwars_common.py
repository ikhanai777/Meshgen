"""Shared geometry for the layered space-battle wall plaque.

Units are millimeters. The plaque lies on XY with its back on the wall at
Z=0 and its front facing +Z, so every layer is printed flat exactly as it
sits in the stack. Layers, bottom up:

- back (Z 0-3): the whole outline, black
- space (Z 3-6): the whole outline, with stars, moon, battle station and
  two fighters inlaid
- hills (Z 6-9) and ship (Z 6-12): dropped into the frame's opening; the
  hills plate has a pocket the ship sits in
- frame (Z 6-12): the blue ring and title banner on top

Four bolts through the back, space and frame layers hold the stack and
hang it on the wall. Colored details are separate bodies inlaid through
their plate and standing proud of its face, for multi-color printing.
"""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import srgb

from japan_common import circle, poly, rect

OVAL_C = (0.0, 25.0)
OVAL = (104.0, 110.0)  # outer radii
OPENING = (92.0, 98.0)  # scene opening radii
BANNER_TOP = -36.0
SHIELD = [(-96.0, -10.0), (96.0, -10.0), (90.0, -60.0), (60.0, -112.0), (-60.0, -112.0), (-90.0, -60.0)]
BOLTS = [(-82.0, 100.0), (82.0, 100.0), (-68.0, -86.0), (68.0, -86.0)]
BOLT_TAB_R = 10.0
BOLT_HOLE_D = 4.5  # M4
FIT = 0.3  # clearance for parts dropped into the frame opening

Z_BACK, T_BACK = 0.0, 3.0
Z_SPACE, T_SPACE = 3.0, 3.0
Z_TOP = 6.0  # hills, ship and frame sit on the space layer
T_HILLS, T_SHIP, T_FRAME = 3.0, 6.0, 6.0

BLACK = srgb("#1B1C1F")
SPACE = srgb("#2B2E34")
NAVY = srgb("#2F4A7A")
SILVER = srgb("#C9CDD2")
LIGHT_GREY = srgb("#B9BDC2")
GREY = srgb("#8E939A")
DARK_GREY = srgb("#55595F")
SLATE = srgb("#6B7079")
WHITE = srgb("#F2F3F5")
ORANGE = srgb("#E0782E")


def ellipse(cx: float, cy: float, rx: float, ry: float) -> bd.Face:
    return bd.Face(bd.Wire.make_ellipse(rx, ry, bd.Plane((cx, cy, 0))))


def outline():
    shape = ellipse(*OVAL_C, *OVAL) + poly(SHIELD)
    for x, y in BOLTS:
        shape = shape + circle(x, y, BOLT_TAB_R)
    return shape


def bolt_holes():
    holes = None
    for x, y in BOLTS:
        h = circle(x, y, BOLT_HOLE_D / 2)
        holes = h if holes is None else holes + h
    return holes


def opening(grow: float = 0.0):
    """The scene window in the frame, optionally grown or shrunk."""
    rx, ry = OPENING
    return ellipse(*OVAL_C, rx + grow, ry + grow) & rect(-200, 200, BANNER_TOP - grow, 200)


def layer_parts(name: str, base, base_color, features, thickness: float, z: float) -> list[bd.Solid]:
    """Bodies of one layer: the base and its inlaid, raised details.

    `features` are (label, shape, color, relief), bottom to top: a later
    feature covers an earlier one where they overlap. Details are clipped to
    the base shape.
    """
    bodies = []
    rest = base
    for _, shape, _, _ in features:
        rest = rest - shape
    bodies.append((name, rest, base_color, 0.0))
    for i, (label, shape, color, relief) in enumerate(features):
        visible = shape & base
        for _, later, _, _ in features[i + 1:]:
            visible = visible - later
        bodies.append((label, visible, color, relief))

    out = []
    for label, shape, color, relief in bodies:
        faces = list(shape.faces())
        if not faces:
            continue
        solids = [bd.Solid.extrude(f, (0, 0, thickness + relief)) for f in faces]
        body = solids[0].fuse(*solids[1:]).clean() if len(solids) > 1 else solids[0]
        body = body.moved(bd.Location((0, 0, z)))
        body.label = label
        body.color = color
        out.append(body)
    return out


def star(cx: float, cy: float, r: float):
    """Four-point twinkle star."""
    k = r * 0.22
    return poly([(cx, cy + r), (cx + k, cy + k), (cx + r, cy), (cx + k, cy - k), (cx, cy - r),
                 (cx - k, cy - k), (cx - r, cy), (cx - k, cy + k)])
