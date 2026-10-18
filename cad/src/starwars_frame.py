"""Frame of the plaque: the blue ring with a silver trim and the title
banner with its two emblems and tagline."""

from __future__ import annotations

import math
from pathlib import Path

from cadgen import build123d as bd
from cadgen import step, stl, threemf

from japan_common import circle, poly, rect
from starwars_common import (
    BOLTS, DARK_GREY, NAVY, SILVER, SPACE, T_FRAME, Z_TOP, bolt_holes, layer_parts, opening, outline,
)
from starwars_space import star

FONT = Path(__file__).resolve().parent.parent / "fonts" / "Inter-SemiBold.otf"
PANEL = [(-66.0, -42.0), (66.0, -42.0), (72.0, -52.0), (56.0, -98.0), (-56.0, -98.0), (-72.0, -52.0)]
TITLE = [("STAR", 19.0, (0.0, -58.0)), ("WARS", 19.0, (0.0, -80.0))]
TAGLINE = ("MAY THE FORCE BE WITH YOU", 5.6, (0.0, -105.5))
EMBLEMS = (48.0, -68.0, 9.5)


def _text(text: str, size: float, at):
    sketch = bd.Text(text, size, font_path=str(FONT), align=(bd.Align.CENTER, bd.Align.CENTER))
    return sketch.moved(bd.Location((at[0], at[1], 0)))


def trim():
    return opening(3.0) - opening(1.4)


def bolt_rings():
    shape = None
    for x, y in BOLTS:
        r = circle(x, y, 5.5) - circle(x, y, 2.25)
        shape = r if shape is None else shape + r
    return shape


def emblems():
    x, y, r = EMBLEMS
    left = circle(-x, y, r) - circle(-x, y, r - 1.6) + star(-x, y, r - 3)
    gear = circle(x, y, r - 3)
    for k in range(6):
        a = math.radians(k * 60)
        cx, cy = x + (r - 2.5) * math.cos(a), y + (r - 2.5) * math.sin(a)
        gear = gear + rect(cx - 1.8, cx + 1.8, cy - 1.8, cy + 1.8).rotate(bd.Axis((cx, cy, 0), (0, 0, 1)), k * 60)
    right = gear - circle(x, y, 2.5)
    return left + right


def frame_parts():
    base = outline() - opening() - bolt_holes()
    title = _text(*TITLE[0]) + _text(*TITLE[1])
    return layer_parts("frame", base, NAVY, [
        ("trim", trim(), SILVER, 0.8),
        ("panel", poly(PANEL), SPACE, 0.4),
        ("title", title, SILVER, 1.6),
        ("tagline", _text(*TAGLINE), SILVER, 0.8),
        ("emblems", emblems(), SILVER, 1.2),
        ("bolt_rings", bolt_rings(), SILVER, 0.6),
    ], T_FRAME, Z_TOP)


@step(out="../STEP/starwars_frame.step")
@stl(out="../STL/starwars_frame.stl")
@threemf(out="../3MF/starwars_frame.3mf")
def starwars_frame():
    return bd.Compound(children=frame_parts(), label="frame")


if __name__ == "__main__":
    starwars_frame()
