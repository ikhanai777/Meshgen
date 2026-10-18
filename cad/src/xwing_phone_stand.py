"""X-wing phone stand assembly: the stand with the 1:2 ship on its peg.

The ship here is the xwing model's colored parts for display; the printable
ship is the fused xwing_stand_ship model.
"""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import glb, step

from xwing_stand import xwing_stand
from xwing_stand_common import ship_location
from xwing_stand_ship import scaled_parts


@step(out="../STEP/xwing_phone_stand.step")
@glb(out="../GLB/xwing_phone_stand.glb")
def xwing_phone_stand():
    stand = xwing_stand()
    stand.label = "stand"
    ship = bd.Compound(children=scaled_parts(), label="ship")
    ship = ship_location() * ship
    ship.label = "ship"
    return bd.Compound(children=[stand, ship], label="xwing_phone_stand")


if __name__ == "__main__":
    xwing_phone_stand()
