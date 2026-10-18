"""Front mat of the Japan shadow box: a rose border inside the frame."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import INNER_H, INNER_W, ROSE, WALL, plate_compound, plate_parts, plate_rect, rect

BORDER = 6.0


def mat_parts():
    x = INNER_W / 2 - BORDER
    opening = rect(-x, x, WALL + BORDER, WALL + INNER_H - BORDER)
    return plate_parts("mat", plate_rect() - opening, ROSE, [])


@step(out="../STEP/japan_mat.step")
@stl(out="../STL/japan_mat.stl")
@threemf(out="../3MF/japan_mat.3mf")
def japan_mat():
    return plate_compound("mat", mat_parts())


if __name__ == "__main__":
    japan_mat()
