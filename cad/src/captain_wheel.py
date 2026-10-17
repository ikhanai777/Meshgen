"""Ship's wheel with the phone ledge, printed flat on its back face.

The phone's bottom edge sits on the ledge and its back leans on the raised
hub boss, so the ledge's seat starts at the boss's front face. A notch
through the ledge clears a charging plug and cable.
"""

from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import step, stl

from captain_common import NAVY, PEG, PEG_CLEARANCE, SOCKET_DEPTH, WHEEL_T

RIM_OUTER_R = 53.0
RIM_INNER_R = 44.0
HUB_R = 15.0
HUB_BOSS_R = 10.0
HUB_BOSS_H = 4.0
HUB_RING_R = (20.0, 24.0)  # decorative inner ring
SPOKES = 8
SPOKE_R = 4.5  # clipped flat to the wheel thickness
HANDLE_NECK_R = 3.5
HANDLE_BULB = (66.0, 5.0)  # (radius from center, bulb radius)
HANDLE_KNOB = (78.0, 5.5)

# Phone ledge (wheel frame): the seat surface is at y=LEDGE_Y
LEDGE_Y = -46.0
LEDGE_W = 64.0
LEDGE_PLATE_T = 5.0
PHONE_DEPTH = 13.0  # max phone thickness, case included
LIP_T = 3.0
LIP_H = 7.0
CABLE_NOTCH_W = 14.0


def _slab() -> bd.Solid:
    return bd.Solid.make_box(400, 400, WHEEL_T, bd.Plane((-200, -200, 0)))


def _radial_cylinder(r: float, r0: float, r1: float, angle: float) -> bd.Solid:
    a = math.radians(angle)
    d = (math.cos(a), math.sin(a), 0.0)
    origin = (d[0] * r0, d[1] * r0, WHEEL_T / 2)
    return bd.Solid.make_cylinder(r, r1 - r0, bd.Plane(origin, z_dir=d))


def _ball(r: float, dist: float, angle: float) -> bd.Solid:
    a = math.radians(angle)
    return bd.Solid.make_sphere(r).moved(
        bd.Location((dist * math.cos(a), dist * math.sin(a), WHEEL_T / 2))
    )


def _ring(r_out: float, r_in: float, z0: float, h: float) -> bd.Solid:
    return bd.Solid.make_cylinder(r_out, h, bd.Plane((0, 0, z0))) - bd.Solid.make_cylinder(
        r_in, h, bd.Plane((0, 0, z0))
    )


def wheel_body() -> bd.Solid:
    body = _ring(RIM_OUTER_R, RIM_INNER_R, 0, WHEEL_T)
    body += _ring(HUB_RING_R[1], HUB_RING_R[0], 0, WHEEL_T)
    body += bd.Solid.make_cylinder(HUB_R, WHEEL_T)
    body += bd.Solid.make_cylinder(HUB_BOSS_R, HUB_BOSS_H, bd.Plane((0, 0, WHEEL_T)))

    for i in range(SPOKES):
        angle = 90.0 + i * 360.0 / SPOKES
        spoke = _radial_cylinder(SPOKE_R, HUB_R - 1.0, RIM_OUTER_R, angle)
        handle = _radial_cylinder(HANDLE_NECK_R, RIM_OUTER_R - 1.0, HANDLE_KNOB[0], angle)
        handle += _ball(HANDLE_BULB[1], HANDLE_BULB[0], angle)
        handle += _ball(HANDLE_KNOB[1], HANDLE_KNOB[0], angle)
        body += (spoke + handle) & _slab()

    # Square socket for the stand's peg, blind from the back face
    s = PEG + 2 * PEG_CLEARANCE
    body -= bd.Solid.make_box(s, s, SOCKET_DEPTH, bd.Plane((-s / 2, -s / 2, -0.01)))
    return body


def ledge() -> bd.Solid:
    x0 = -LEDGE_W / 2
    z0 = WHEEL_T
    seat_back = z0 + HUB_BOSS_H  # the phone's back rests on the hub boss
    plate = bd.Solid.make_box(
        LEDGE_W, LEDGE_PLATE_T, HUB_BOSS_H + PHONE_DEPTH + LIP_T + 1.0,
        bd.Plane((x0, LEDGE_Y - LEDGE_PLATE_T, z0 - 1.0)),
    )
    lip = bd.Solid.make_box(
        LEDGE_W, LIP_H + LEDGE_PLATE_T, LIP_T,
        bd.Plane((x0, LEDGE_Y - LEDGE_PLATE_T, seat_back + PHONE_DEPTH)),
    )
    notch = bd.Solid.make_box(
        CABLE_NOTCH_W, 40.0, 40.0, bd.Plane((-CABLE_NOTCH_W / 2, LEDGE_Y - 20.0, z0 - 0.01))
    )
    return (plate + lip) - notch


def wheel_part() -> bd.Solid:
    part = (wheel_body() + ledge()).clean()
    solids = part.solids()
    if len(solids) != 1:
        raise ValueError(f"wheel should be one solid, got {len(solids)}")
    return solids[0]


@step(out="../STEP/captain_wheel.step")
@stl(out="../STL/captain_wheel.stl")
def captain_wheel():
    wheel = wheel_part()
    wheel.label = "wheel"
    wheel.color = NAVY
    return wheel


if __name__ == "__main__":
    captain_wheel()
