"""Ship's-wheel phone stand assembly: the wheel keyed onto the stand's peg."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import glb, step, threemf

from captain_common import WHEEL_BACK_CENTER, wheel_axes
from captain_stand import captain_stand
from captain_wheel import captain_wheel


def wheel_location() -> bd.Location:
    _, normal = wheel_axes()
    return bd.Plane(WHEEL_BACK_CENTER, x_dir=(1, 0, 0), z_dir=normal).location


@step(out="../STEP/captain_phone_stand.step")
@glb(out="../GLB/captain_phone_stand.glb")
@threemf(out="../3MF/captain_phone_stand.3mf")
def captain_phone_stand():
    stand = captain_stand()
    stand.label = "stand"
    wheel = wheel_location() * captain_wheel()
    wheel.label = "wheel"
    return bd.Compound(children=[stand, wheel], label="captain_phone_stand")


if __name__ == "__main__":
    captain_phone_stand()
