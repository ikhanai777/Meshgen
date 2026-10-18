"""Castle tower phone stand.

Units are millimeters, +Z up, and the phone faces the viewer at -Y. A round
brick tower with a spiral stair stands at the back right of a rounded base.
The phone leans back on a sloped ramp in front of the tower, its bottom edge
held by two little turrets. Its charging cable drops through a slot between
the turrets, runs along a groove under the base and comes out of the
tower's arched door.
"""

from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import srgb, step, stl, threemf

STONE = srgb("#5E646D")

BASE = (-56.0, 60.0, -54.0, 78.0)  # x0, x1, y0, y1
BASE_H = 10.0
BASE_CORNER_R = 10.0
BASE_CHAMFER = 2.0

# The phone's back-bottom edge, on the base top, and its lean back
PHONE_TILT = 15.0
PHONE_BACK_Y = -28.0
PHONE_X = -14.0  # phone center
PHONE_DEPTH = 13.0  # max thickness, case included
RAMP_X = (-46.0, 20.0)
RAMP_TOP_Z = 100.0
RAMP_BACK = ((6.0, 100.0), (18.0, BASE_H))  # (y, z) of the back face

TURRET = (12.0, 8.0, 16.0)  # x, y, height of the body
TURRET_X = (-40.0, 12.0)  # centers
TURRET_BACK_Y = -42.0
CABLE_SLOT = (14.0, 11.0)  # x, y
CABLE_GROOVE = (8.0, 6.0)  # width, height under the base

TOWER_C = (26.0, 44.0)
TOWER_R = 24.0
TOWER_TOP = 136.0
CORBEL_H = 6.0
CORBEL_OUT = 4.0
PARAPET_H = 10.0
PARAPET_WALL = 3.5
MERLON_H = 8.0
CRENELS = 12
CRENEL_W = 6.0

BRICK_H = 6.0
BRICKS_PER_ROW = 14
JOINT = 1.0
JOINT_DEPTH = 0.8

DOOR_ANGLE = -40.0
DOOR_W = 14.0
DOOR_H = 22.0  # to the springing of the arch
DOOR_DEPTH = 14.0

STEPS = 24
STEP_START = 150.0  # angle of the lowest step, degrees
STEP_TURN = -12.0  # per step (clockwise going up)
STEP_RISE = 5.0
STEP_OUT = 8.0
STEP_DEPTH = 14.0  # underside below each tread

# Arrow slits: (angle, center z)
SLITS = [(180.0, 60.0), (110.0, 80.0), (60.0, 90.0), (-20.0, 120.0), (150.0, 115.0)]
SLIT = (3.0, 12.0, 5.0)  # width, height, depth


def _polar(r: float, angle: float) -> tuple[float, float]:
    a = math.radians(angle)
    return TOWER_C[0] + r * math.cos(a), TOWER_C[1] + r * math.sin(a)


def _radial_plane(angle: float, r: float, z: float) -> bd.Plane:
    """Plane on the tower wall: local Z points out radially, local Y is up."""
    a = math.radians(angle)
    x, y = _polar(r, angle)
    return bd.Plane((x, y, z), x_dir=(-math.sin(a), math.cos(a), 0), z_dir=(math.cos(a), math.sin(a), 0))


def _box(x0, x1, y0, y1, z0, z1) -> bd.Solid:
    return bd.Solid.make_box(x1 - x0, y1 - y0, z1 - z0, bd.Plane((x0, y0, z0)))


def base() -> bd.Solid:
    x0, x1, y0, y1 = BASE
    outline = bd.RectangleRounded(x1 - x0, y1 - y0, BASE_CORNER_R, align=(bd.Align.MIN, bd.Align.MIN))
    body = bd.extrude(outline.moved(bd.Location((x0, y0, 0))), BASE_H)
    top = body.edges().group_by(bd.Axis.Z)[-1]
    return bd.chamfer(top, BASE_CHAMFER)


def ramp() -> bd.Solid:
    t = math.tan(math.radians(PHONE_TILT))
    front_top = PHONE_BACK_Y + (RAMP_TOP_Z - BASE_H) * t
    pts = [
        (PHONE_BACK_Y, BASE_H - 1),
        (front_top, RAMP_TOP_Z),
        RAMP_BACK[0],
        (RAMP_BACK[1][0], BASE_H - 1),
    ]
    x0, x1 = RAMP_X
    face = bd.Face(bd.Wire.make_polygon([(x0, y, z) for y, z in pts], close=True))
    return bd.Solid.extrude(face, (x1 - x0, 0, 0))


def turret(cx: float) -> bd.Solid:
    w, d, h = TURRET
    y1 = TURRET_BACK_Y
    body = _box(cx - w / 2, cx + w / 2, y1 - d, y1, BASE_H - 1, BASE_H + h)
    top = BASE_H + h
    body += _box(cx - w / 2 - 1, cx + w / 2 + 1, y1 - d - 1, y1 + 1, top - 2, top)  # cornice
    body += _box(cx - w / 2 - 1, cx + w / 2 + 1, y1 - d - 1, y1 + 1, top, top + 4)
    body -= _box(cx - 2, cx + 2, y1 - d - 2, y1 + 2, top + 1.5, top + 5)
    body -= _box(cx - w, cx + w, y1 - d / 2 - 1.5, y1 - d / 2 + 1.5, top + 1.5, top + 5)
    for z in (BASE_H + 4, BASE_H + 9):  # brick courses
        body -= _box(cx - w, cx + w, y1 - d - 0.5, y1 - d + 0.5, z, z + 0.8)
    return body


def tower_body() -> bd.Solid:
    cx, cy = TOWER_C
    r = TOWER_R
    body = bd.Solid.make_cylinder(r, TOWER_TOP - 1, bd.Plane((cx, cy, 1)))

    # Brick joints: horizontal courses and staggered vertical joints
    cutters = []
    z = BASE_H + BRICK_H
    row = 0
    while z < TOWER_TOP - 2:
        ring = bd.Solid.make_cylinder(r + 1, JOINT, bd.Plane((cx, cy, z)))
        ring -= bd.Solid.make_cylinder(r - JOINT_DEPTH, JOINT, bd.Plane((cx, cy, z)))
        cutters.append(ring)
        z += BRICK_H
        row += 1
    for row_i in range(row + 1):
        z0 = BASE_H + row_i * BRICK_H
        for j in range(BRICKS_PER_ROW):
            a = (j + 0.5 * (row_i % 2)) * 360.0 / BRICKS_PER_ROW
            plane = _radial_plane(a, r - JOINT_DEPTH, z0)
            cutters.append(plane.location * _box(-JOINT / 2, JOINT / 2, 0, BRICK_H, 0, 2))
    body = body.cut(*cutters)

    top = TOWER_TOP
    body += bd.Solid.make_cone(r, r + CORBEL_OUT, CORBEL_H, bd.Plane((cx, cy, top - 0.01)))
    pr = r + CORBEL_OUT
    p0 = top + CORBEL_H
    parapet = bd.Solid.make_cylinder(pr, PARAPET_H + MERLON_H, bd.Plane((cx, cy, p0 - 0.01)))
    parapet -= bd.Solid.make_cylinder(pr - PARAPET_WALL, PARAPET_H + MERLON_H, bd.Plane((cx, cy, p0 + 1.5)))
    for k in range(CRENELS):
        a = (k + 0.5) * 360.0 / CRENELS
        plane = _radial_plane(a, pr - PARAPET_WALL - 1, p0 + PARAPET_H)
        parapet -= plane.location * _box(-CRENEL_W / 2, CRENEL_W / 2, 0, MERLON_H + 1, 0, PARAPET_WALL + 2)
    for k in range(CRENELS * 2):  # machicolation notches under the parapet lip
        a = k * 360.0 / (CRENELS * 2)
        plane = _radial_plane(a, r + 0.5, top + CORBEL_H - 3.0)
        body -= plane.location * _box(-1.2, 1.2, 0, 2.0, 0, CORBEL_OUT + 1)
    body += parapet
    body += _parapet_band(pr, p0)
    return body


def _parapet_band(pr: float, p0: float) -> bd.Solid:
    """Thin string course at the bottom of the parapet."""
    cx, cy = TOWER_C
    band = bd.Solid.make_cylinder(pr + 0.8, 1.5, bd.Plane((cx, cy, p0)))
    return band - bd.Solid.make_cylinder(pr - 1, 1.5, bd.Plane((cx, cy, p0)))


def stairs() -> bd.Solid:
    out = None
    for i in range(STEPS):
        a = STEP_START + i * STEP_TURN
        top = BASE_H + STEP_RISE * (i + 1)
        bottom = max(top - STEP_DEPTH, BASE_H - 1)
        start = a - abs(STEP_TURN) / 2
        sector = bd.Solid.make_cylinder(
            TOWER_R + STEP_OUT, top - bottom, bd.Plane((*TOWER_C, bottom)), angle=abs(STEP_TURN)
        )
        sector -= bd.Solid.make_cylinder(TOWER_R - 1, top - bottom, bd.Plane((*TOWER_C, bottom)))
        sector = sector.rotate(bd.Axis((*TOWER_C, 0), (0, 0, 1)), start)
        out = sector if out is None else out + sector
    return out


def door_cut() -> bd.Solid:
    w, h = DOOR_W, DOOR_H
    plane = _radial_plane(DOOR_ANGLE, TOWER_R + 3, -1)
    length = DOOR_DEPTH + 3
    shape = _box(-w / 2, w / 2, 0, h + 1, -length, 0)
    shape += bd.Solid.make_cylinder(w / 2, length, bd.Plane((0, h + 1, -length)))
    return plane.location * shape


def door_frame() -> bd.Solid:
    """Raised stone arch around the doorway."""
    w, h = DOOR_W, DOOR_H
    plane = _radial_plane(DOOR_ANGLE, TOWER_R - 2, BASE_H - 1)
    rim = _box(-w / 2 - 3, w / 2 + 3, 0, h - BASE_H + 2, 0, 3.0)
    rim += bd.Solid.make_cylinder(w / 2 + 3, 3.0, bd.Plane((0, h - BASE_H + 2, 0)))
    return plane.location * rim


def slit_cuts() -> list[bd.Solid]:
    w, h, d = SLIT
    cuts = []
    for a, z in SLITS:
        plane = _radial_plane(a, TOWER_R - d, z - h / 2)
        slit = _box(-w / 2, w / 2, 0, h - w / 2, 0, d + 2)
        slit += bd.Solid.make_cylinder(w / 2, d + 2, bd.Plane((0, h - w / 2, 0)))
        cuts.append(plane.location * slit)
    return cuts


def cable_path() -> bd.Solid:
    """Slot under the phone's port, then a groove under the base to the door."""
    sw, sd = CABLE_SLOT
    y_slot = PHONE_BACK_Y - PHONE_DEPTH / 2
    slot = _box(PHONE_X - sw / 2, PHONE_X + sw / 2, y_slot - sd / 2, y_slot + sd / 2, -1, BASE_H + 1)
    gw, gh = CABLE_GROOVE
    door = _polar(TOWER_R - DOOR_DEPTH / 2, DOOR_ANGLE)
    pts = [(PHONE_X, y_slot), (PHONE_X, 12.0), door]
    groove = slot
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        length = math.hypot(xb - xa, yb - ya)
        plane = bd.Plane((xa, ya, -1), x_dir=((xb - xa) / length, (yb - ya) / length, 0))
        groove += plane.location * _box(0, length, -gw / 2, gw / 2, 0, gh + 1)
        groove += bd.Solid.make_cylinder(gw / 2, gh + 1, bd.Plane((xb, yb, -1)))
    return groove


def stand_body() -> bd.Solid:
    body = base() + ramp() + tower_body() + stairs() + door_frame()
    for cx in TURRET_X:
        body += turret(cx)
    body = body.cut(door_cut(), cable_path(), *slit_cuts()).clean()
    solids = body.solids()
    if len(solids) != 1:
        raise ValueError(f"castle stand should be one solid, got {len(solids)}")
    return solids[0]


@step(out="../STEP/castle_stand.step")
@stl(out="../STL/castle_stand.stl")
@threemf(out="../3MF/castle_stand.3mf")
def castle_stand():
    body = stand_body()
    body.label = "castle_stand"
    body.color = STONE
    return body


if __name__ == "__main__":
    castle_stand()
