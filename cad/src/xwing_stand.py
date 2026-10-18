"""X-wing phone stand: greebled base, tilted phone cradle and the ship's arch.

The phone's bottom edge sits on a seat square to the tilted back plate, held
by a front lip. Under the phone's charging port a channel runs down through
the base and out the back along the underside, so the cable is hidden. The
arch rises behind the phone and curves forward, ending in a pedestal with a
square peg that keys the ship.
"""

from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import step, stl

from xwing_stand_common import (
    BASE_D,
    BASE_H,
    BASE_W,
    CRADLE_W,
    GREY,
    ORANGE,
    PEG,
    PEG_LEN,
    PHONE_DEPTH,
    RED,
    SHIP_MOUNT,
    WHITE,
    cradle_axes,
    cradle_point,
)

BASE_EDGE_CHAMFER = 3.0
BASE_TOP_CHAMFER = 1.5

# Cradle frame dimensions (u forward, v up; see xwing_stand_common)
BACK_PLATE_T = 6.0
BACK_PLATE_LEN = 72.0
SEAT_T = 8.0
LIP_T = 4.0
LIP_H = 9.0
WINDOW = (16.0, 30.0, 44.0)  # oval window in the back plate: width, length, center v
CABLE_W = 14.0
CABLE_U = (1.0, 12.0)
CABLE_GROOVE_H = 8.0

# Arch: (y, z) points of its centerline in the X=0 plane
ARCH_POINTS = [(36.0, 22.0), (48.0, 110.0), (48.0, 190.0), (30.0, 224.0), (10.0, 228.0)]
ARCH_SECTION = (16.0, 12.0)  # X width, in-plane thickness
STRIPE = (0.6, 5.0)
STRIPE_Z = (34.0, 196.0)
PEDESTAL = (8.0, 10.0)  # X, Y


def _box(x0, x1, y0, y1, z0, z1) -> bd.Solid:
    return bd.Solid.make_box(x1 - x0, y1 - y0, z1 - z0, bd.Plane((x0, y0, z0)))


def cradle_plane() -> bd.Plane:
    """Local X = world X, local Y = v (up the phone), local Z = u (forward)."""
    (uy, uz), _ = cradle_axes()
    y, z = cradle_point(0, 0)
    return bd.Plane((0, y, z), x_dir=(1, 0, 0), z_dir=(0, uy, uz))


def base() -> bd.Solid:
    hw, hd = BASE_W / 2, BASE_D / 2
    body = _box(-hw, hw, -hd, hd, 0, BASE_H)
    body = bd.chamfer(body.edges().filter_by(bd.Axis.Z), BASE_EDGE_CHAMFER)
    body = bd.chamfer(body.edges().group_by(bd.Axis.Z)[-1], BASE_TOP_CHAMFER)

    cuts = [
        _box(-hw + 4, hw - 4, -hd - 1, -hd + 1, 18, 19.5),  # front groove band
        _box(-hw + 4, -12, -hd - 1, -hd + 1.2, 4, 15),  # front left panel
        _box(12, hw - 4, -hd - 1, -hd + 1.2, 4, 15),  # front right panel
    ]
    for i in range(5):  # rear vents
        x = -16 + i * 8
        cuts.append(_box(x - 1.5, x + 1.5, hd - 1.5, hd + 1, 12, 22))
    for sx in (-1, 1):
        face_x = sx * hw
        cuts.append(_box(*sorted((face_x - sx * 1.2, face_x + sx)), -hd + 8, -6, 4, 20))
        for i in range(4):  # side vents inside the panel
            z = 6 + i * 3.5
            cuts.append(_box(*sorted((face_x - sx * 2.5, face_x)), -hd + 10, -8, z, z + 1.8))
        port = bd.Solid.make_cylinder(
            7.0, 4.0, bd.Plane((face_x + sx * 1.0, 24.0, 13.0), z_dir=(-sx, 0, 0))
        )
        cuts.append(port)
    for c in cuts:
        body -= c

    adds = [
        bd.Solid.make_cylinder(10.0, 1.5, bd.Plane((0, -hd, 10.0), z_dir=(0, -1, 0))),
    ]
    for i in range(3):  # ribs in the front left panel
        z = 6 + i * 3
        adds.append(_box(-hw + 6, -14, -hd - 0.2, -hd + 1.2, z, z + 1.4))
    for sx in (-1, 1):
        face_x = sx * hw
        adds.append(  # boss inside the round port
            bd.Solid.make_cylinder(
                3.5, 3.0, bd.Plane((face_x - sx * 3.0, 24.0, 13.0), z_dir=(sx, 0, 0))
            )
        )
    for a in adds:
        body += a
    return body


def cradle() -> bd.Solid:
    """Back plate, tilted seat and front lip, filled down into the base."""
    p = cradle_point
    t = BACK_PLATE_T
    seat_front = PHONE_DEPTH + LIP_T
    pts = [
        p(seat_front, -SEAT_T),
        p(seat_front, LIP_H),
        p(PHONE_DEPTH, LIP_H),
        p(PHONE_DEPTH, 0),
        p(0, 0),
        p(0, BACK_PLATE_LEN),
        p(-t, BACK_PLATE_LEN),
        p(-t, -SEAT_T),
    ]
    fill_z = BASE_H - 2.0
    pts.insert(0, (pts[0][0], fill_z))
    pts.append((pts[-1][0], min(pts[-1][1], fill_z)))
    x0 = -CRADLE_W / 2
    face = bd.Face(bd.Wire.make_polygon([(x0, y, z) for y, z in pts], close=True))
    body = bd.Solid.extrude(face, (CRADLE_W, 0, 0))

    plane = cradle_plane()
    w, length, vc = WINDOW
    straight = length - w
    window = bd.Solid.make_box(w, straight, 3 * t, bd.Plane((-w / 2, vc - straight / 2, -2 * t)))
    for dv in (-straight / 2, straight / 2):
        window += bd.Solid.make_cylinder(w / 2, 3 * t, bd.Plane((0, vc + dv, -2 * t)))
    body -= plane.location * window
    return body


def cable_channel() -> bd.Solid:
    """Slot below the phone's port, straight down the cradle's v axis, then
    a groove along the underside out to the back."""
    u0, u1 = CABLE_U
    chute = bd.Solid.make_box(CABLE_W, 80.0, u1 - u0, bd.Plane((-CABLE_W / 2, -79.0, u0)))
    chute = cradle_plane().location * chute
    hw = CABLE_W / 2
    groove = _box(-hw, hw, -40.0, BASE_D / 2 + 1, -1.0, CABLE_GROOVE_H)
    return chute + groove


def arch_path() -> bd.Edge:
    return bd.Edge.make_spline(
        [bd.Vector(0, y, z) for y, z in ARCH_POINTS],
        tangents=[bd.Vector(0, 0, 1), bd.Vector(0, -1, 0)],
    )


def _arch_sweep(width: float, thick: float, x_offset: float = 0.0) -> bd.Solid:
    path = arch_path()
    y, z = ARCH_POINTS[0]
    plane = bd.Plane((0, y, z), x_dir=(1, 0, 0), z_dir=(0, 0, 1))
    section = plane.location * bd.Face(
        bd.Wire.make_rect(width, thick).moved(bd.Location((x_offset, 0, 0)))
    )
    return bd.Solid.sweep(section, bd.Wire([path]))


def arch() -> bd.Solid:
    body = _arch_sweep(*ARCH_SECTION)
    x, y, z = SHIP_MOUNT
    px, py = PEDESTAL
    body += _box(x - px / 2, x + px / 2, y - py / 2, y + py / 2, z - 16.0, z)
    peg = bd.Solid.make_box(PEG, PEG, PEG_LEN, bd.Plane((x - PEG / 2, y - PEG / 2, z)))
    peg = bd.chamfer(peg.edges().group_by(bd.Axis.Z)[-1], 0.8)
    return body + peg


def stripes() -> list[bd.Solid]:
    w, _ = ARCH_SECTION
    sw, sh = STRIPE
    z0, z1 = STRIPE_Z
    keep = _box(-50, 50, -100, 100, z0, z1)
    out = []
    for sx in (-1, 1):
        stripe = _arch_sweep(sw, sh, sx * (w / 2 + sw / 2))
        out.append(stripe.intersect(keep))
    return [s if isinstance(s, bd.Shape) else s[0] for s in out]


def accents() -> list[tuple[str, bd.Shape, bd.Color]]:
    hw, hd = BASE_W / 2, BASE_D / 2
    out = [
        ("emblem", bd.Solid.make_cylinder(7.5, 1.0, bd.Plane((0, -hd - 1.5, 10.0), z_dir=(0, -1, 0))), RED),
        ("accent_front_orange", _box(-hw + 6, -14, -hd - 0.8, -hd, 21, 24), ORANGE),
        ("accent_front_white", _box(25, 31, -hd + 1.2 - 2.5, -hd + 1.2, 5, 11), WHITE),
        ("accent_front_bar", _box(15, 23, -hd + 1.2 - 1.6, -hd + 1.2, 11.5, 13.5), ORANGE),
    ]
    for sx, side in ((-1, "left"), (1, "right")):
        face_x = sx * hw
        out.append(
            (f"accent_{side}_orange", _box(*sorted((face_x, face_x + sx * 0.8)), -2, 14, 20.5, 23), ORANGE)
        )
        out.append(
            (f"accent_{side}_red", _box(*sorted((face_x, face_x + sx * 0.8)), 6, 12, 4, 10), RED)
        )
    for i, stripe in enumerate(stripes()):
        out.append((f"arch_stripe_{'left' if i == 0 else 'right'}", stripe, ORANGE))
    return out


def stand_body() -> bd.Solid:
    part = (base() + cradle() + arch() - cable_channel()).clean()
    solids = part.solids()
    if len(solids) != 1:
        raise ValueError(f"stand should be one solid, got {len(solids)}")
    return solids[0]


@step(out="../STEP/xwing_stand.step")
@stl(out="../STL/xwing_stand.stl")
def xwing_stand():
    body = stand_body()
    body.label = "stand"
    body.color = GREY
    children = [body]
    for label, shape, color in accents():
        shape.label = label
        shape.color = color
        children.append(shape)
    return bd.Compound(children=children, label="xwing_stand")


if __name__ == "__main__":
    xwing_stand()
