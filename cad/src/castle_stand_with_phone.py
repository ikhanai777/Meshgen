"""Castle tower phone stand with a prop phone leaning on the ramp, for display."""

from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import glb, step

from castle_stand import BASE_H, PHONE_BACK_Y, PHONE_TILT, PHONE_X, castle_stand
from xwing_phone_stand_with_phone import prop_phone


def phone_plane() -> bd.Plane:
    """Local X across the phone, Y up its back, Z out of the screen."""
    t = math.radians(PHONE_TILT)
    return bd.Plane(
        (PHONE_X, PHONE_BACK_Y - 0.1, BASE_H + 0.1), x_dir=(1, 0, 0), z_dir=(0, -math.cos(t), math.sin(t))
    )


@step(out="../STEP/castle_stand_with_phone.step")
@glb(out="../GLB/castle_stand_with_phone.glb")
def castle_stand_with_phone():
    stand = castle_stand()
    phone = phone_plane().location * bd.Compound(children=prop_phone(), label="phone")
    phone.label = "phone"
    return bd.Compound(children=[stand, phone], label="castle_stand_with_phone")


if __name__ == "__main__":
    castle_stand_with_phone()
