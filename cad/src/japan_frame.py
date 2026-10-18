"""Frame of the Japan shadow box: an open box with a slot per layer.

Each slot cuts through the top wall and runs down both side walls into the
bottom wall, so a plate slides in from above and stands in its groove.
Modeled in the assembled position; print it lying on its back.
"""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import step, stl, threemf

from japan_common import BLACK, DEPTH, GROOVE, INNER_H, INNER_W, SLOT_GAP, SLOTS, WALL

EDGE_CHAMFER = 1.0


def _box(x0, x1, y0, y1, z0, z1) -> bd.Solid:
    return bd.Solid.make_box(x1 - x0, y1 - y0, z1 - z0, bd.Plane((x0, y0, z0)))


def frame_body() -> bd.Solid:
    ox = INNER_W / 2 + WALL
    oz = INNER_H + 2 * WALL
    body = _box(-ox, ox, 0, DEPTH, 0, oz)
    body = bd.chamfer(body.edges(), EDGE_CHAMFER)
    body -= _box(-INNER_W / 2, INNER_W / 2, -1, DEPTH + 1, WALL, WALL + INNER_H)
    gx = INNER_W / 2 + GROOVE
    for y in SLOTS.values():
        body -= _box(-gx, gx, y - SLOT_GAP / 2, y + SLOT_GAP / 2, WALL - GROOVE, oz + 1)
    return body


@step(out="../STEP/japan_frame.step")
@stl(out="../STL/japan_frame.stl")
@threemf(out="../3MF/japan_frame.3mf")
def japan_frame():
    body = frame_body()
    body.label = "frame"
    body.color = BLACK
    return body


if __name__ == "__main__":
    japan_frame()
