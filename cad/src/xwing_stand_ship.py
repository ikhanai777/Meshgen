"""1:2 X-wing for the phone stand, fused into one printable solid.

The ship comes from the xwing model, scaled down, with a square socket in
its belly for the stand's peg. Its frame is the xwing model's: +X to the
nose, +Z up, belly at Z = SHIP_BELLY_Z * SHIP_SCALE.
"""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import step, stl

from xwing import HULL, xwing
from xwing_stand_common import (
    PEG,
    PEG_CLEARANCE,
    SHIP_BELLY_Z,
    SHIP_SCALE,
    SOCKET_DEPTH,
    SOCKET_SHIP_X,
)


def scaled_parts() -> list[bd.Shape]:
    """The xwing model's colored leaf parts at stand scale."""
    # bd.scale leaves a part's own placement unscaled, so scale through a matrix
    s = SHIP_SCALE
    matrix = bd.Matrix([[s, 0, 0, 0], [0, s, 0, 0], [0, 0, s, 0]])
    parts = []
    for child in xwing().children:
        scaled = child.transform_geometry(matrix)
        scaled.label = child.label
        scaled.color = child.color
        parts.append(scaled)
    return parts


def socket() -> bd.Solid:
    s = PEG + 2 * PEG_CLEARANCE
    x = SOCKET_SHIP_X * SHIP_SCALE
    z = SHIP_BELLY_Z * SHIP_SCALE
    return bd.Solid.make_box(s, s, SOCKET_DEPTH + 0.01, bd.Plane((x - s / 2, -s / 2, z - 0.01)))


@step(out="../STEP/xwing_stand_ship.step")
@stl(out="../STL/xwing_stand_ship.stl")
def xwing_stand_ship():
    parts = scaled_parts()
    body = parts[0].fuse(*parts[1:]).clean() - socket()
    solids = body.solids()
    if len(solids) != 1:
        raise ValueError(f"ship should be one solid, got {len(solids)}")
    ship = solids[0]
    ship.label = "ship"
    ship.color = HULL
    return ship


if __name__ == "__main__":
    xwing_stand_ship()
