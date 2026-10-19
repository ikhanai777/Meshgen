"""Shared shapes for the shadow-box scenes that use the japan_common frame
and plate system (The Shire and Middle Earth)."""

from __future__ import annotations

from pathlib import Path

from cadgen import build123d as bd

from japan_common import PLATE_BOTTOM, PLATE_X, circle, poly, rect

SERIF = Path(__file__).resolve().parent.parent / "fonts" / "DejaVuSerif-Bold.ttf"


def jag_band(top, y_bottom: float = PLATE_BOTTOM):
    """Full plate-width band under a straight-segment (rocky) top edge."""
    pts = [(-PLATE_X, y_bottom), (-PLATE_X, top[0][1])] + list(top) + [(PLATE_X, top[-1][1]), (PLATE_X, y_bottom)]
    return poly(pts)


def ellipse(cx: float, cy: float, rx: float, ry: float) -> bd.Face:
    return bd.Face(bd.Wire.make_ellipse(rx, ry, bd.Plane((cx, cy, 0))))


def title(text: str, size: float, at):
    sketch = bd.Text(text, size, font_path=str(SERIF), align=(bd.Align.CENTER, bd.Align.CENTER))
    return sketch.moved(bd.Location((at[0], at[1], 0)))


def union(shapes):
    out = None
    for s in shapes:
        out = s if out is None else out + s
    return out


def figure(x: float, y: float, h: float = 12.0, staff: bool = False, hat: bool = False):
    """Walking cloaked figure standing on (x, y), about h tall."""
    k = h / 12.0
    shape = circle(x, y + 10.5 * k, 1.6 * k)
    shape = shape + poly([(x - 2.8 * k, y + 2.5 * k), (x + 2.8 * k, y + 2.5 * k), (x + 1.4 * k, y + 9.5 * k),
                          (x - 1.4 * k, y + 9.5 * k)])
    shape = shape + rect(x - 1.8 * k, x - 0.6 * k, y - 1, y + 3 * k) + rect(x + 0.6 * k, x + 1.8 * k, y - 1, y + 3 * k)
    if staff:
        shape = shape + rect(x + 3.0 * k, x + 3.8 * k, y - 1, y + 13 * k) + rect(x + 1.5 * k, x + 3.2 * k, y + 7 * k, y + 8 * k)
    if hat:
        shape = shape + poly([(x - 2.6 * k, y + 11.2 * k), (x + 2.6 * k, y + 11.2 * k), (x + 0.6 * k, y + 16 * k)])
    return shape


def wheel(cx: float, cy: float, r: float):
    shape = circle(cx, cy, r) - circle(cx, cy, r - 1.3)
    shape = shape + rect(cx - r + 0.5, cx + r - 0.5, cy - 0.5, cy + 0.5) + rect(cx - 0.5, cx + 0.5, cy - r + 0.5, cy + r - 0.5)
    return shape + circle(cx, cy, 1.4)
