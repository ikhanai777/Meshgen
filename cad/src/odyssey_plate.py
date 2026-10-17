"""Back plate for the Starship Odyssey wall plaque, with raised lettering."""

from __future__ import annotations

from pathlib import Path

from cadgen import build123d as bd
from cadgen import step, stl

from odyssey_common import (
    LETTER_COLOR,
    PLATE_COLOR,
    PLATE_CORNER_R,
    PLATE_H,
    PLATE_T,
    PLATE_W,
)

FONT = Path(__file__).resolve().parent.parent / "fonts" / "Inter-SemiBold.otf"

HOLE_INSET = 8.0
HOLE_D = 4.5  # for #8 / M4 countersunk wood screws
COUNTERSINK_D = 9.0
EDGE_CHAMFER = 0.8
LETTER_HEIGHT = 0.8

# (text, font size, center y)
LINES = [
    ("STARSHIP ODYSSEY", 14.0, 53.0),
    ("EXPLORATION CRUISER", 8.0, 39.0),
    ("NCC-1701-OD", 8.0, 29.5),
    ("WALL DECOR | 3D PRINTABLE MODEL", 6.0, 17.0),
]


def plate_body() -> bd.Solid:
    outline = bd.RectangleRounded(
        PLATE_W, PLATE_H, PLATE_CORNER_R, align=(bd.Align.MIN, bd.Align.MIN)
    )
    body = bd.extrude(outline, PLATE_T)
    front_edges = body.edges().filter_by(bd.GeomType.LINE).group_by(bd.Axis.Z)[-1]
    front_edges += body.edges().filter_by(bd.GeomType.CIRCLE).group_by(bd.Axis.Z)[-1]
    body = bd.chamfer(front_edges, EDGE_CHAMFER)

    sink_depth = (COUNTERSINK_D - HOLE_D) / 2
    for x in (HOLE_INSET, PLATE_W - HOLE_INSET):
        for y in (HOLE_INSET, PLATE_H - HOLE_INSET):
            hole = bd.Solid.make_cylinder(HOLE_D / 2, PLATE_T + 2, bd.Plane((x, y, -1)))
            sink = bd.Solid.make_cone(
                HOLE_D / 2,
                COUNTERSINK_D / 2 + 0.1,
                sink_depth + 0.1,
                bd.Plane((x, y, PLATE_T - sink_depth)),
            )
            body = body - hole - sink
    return body


def lettering() -> bd.Shape:
    parts = []
    for text, size, y in LINES:
        sketch = bd.Text(
            text, size, font_path=str(FONT), align=(bd.Align.CENTER, bd.Align.CENTER)
        )
        parts.append(bd.Location((PLATE_W / 2, y, PLATE_T)) * bd.extrude(sketch, LETTER_HEIGHT))
    return bd.Compound(parts).fuse() if len(parts) > 1 else parts[0]


@step(out="../STEP/odyssey_plate.step")
@stl(out="../STL/odyssey_plate.stl")
def odyssey_plate():
    plate = plate_body()
    plate.label = "plate"
    plate.color = PLATE_COLOR
    letters = lettering()
    letters.label = "lettering"
    letters.color = LETTER_COLOR
    return bd.Compound(children=[plate, letters], label="odyssey_plate")


if __name__ == "__main__":
    odyssey_plate()
