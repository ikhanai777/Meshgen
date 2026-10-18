"""X-wing phone stand as a print set: the stand and the printable ship,
seated on its peg, in one multi-color 3MF."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import threemf

from xwing_stand import xwing_stand
from xwing_stand_common import ship_location
from xwing_stand_ship import xwing_stand_ship


@threemf(out="../3MF/xwing_phone_stand.3mf")
def xwing_stand_print():
    stand = xwing_stand()
    stand.label = "stand"
    ship = ship_location() * xwing_stand_ship()
    ship.label = "ship"
    return bd.Compound(children=[stand, ship], label="xwing_phone_stand")


if __name__ == "__main__":
    xwing_stand_print()
