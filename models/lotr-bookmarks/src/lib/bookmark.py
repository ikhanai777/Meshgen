"""Shared bookmark geometry: blank outline, raised-relief helpers and the final two-level body.

Every bookmark is a flat base (BASE thick, on the bed at Z=0) with decoration raised RELIEF above it.
Swap filament at Z=BASE for a two-colour print. Units: mm, the bookmark lies in XY with +Y up the page.
"""
from __future__ import annotations

import math

from cadgen import build123d as bd

BASE = 1.4  # base plate thickness: 7 layers at 0.2 mm
RELIEF = 0.8  # raised decoration height: 4 layers at 0.2 mm
MIN_FEATURE = 0.9  # narrowest raised line; above two 0.4 mm nozzle widths

LENGTH = 150.0
WIDTH = 40.0
CORNER = 5.0

TASSEL_R = 2.5  # 5 mm hole for a tassel or ribbon
TASSEL_Y = LENGTH / 2 - 8


def blank() -> bd.Sketch:
    """The standard rounded-rectangle bookmark outline, centred on the origin."""
    return bd.RectangleRounded(WIDTH, LENGTH, CORNER)


def frame(outline, inset: float = 1.6, width: float = 1.4):
    """A raised border following the outline, `inset` in from its edge."""
    return bd.offset(outline, -inset) - bd.offset(outline, -(inset + width))


def polygon(points) -> bd.Sketch:
    """A filled outline; points are reordered counter-clockwise so the face always adds material."""
    points = list(points)
    area = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(points, points[1:] + points[:1]))
    if area < 0:
        points.reverse()
    return bd.Polygon(*points, align=None)


def stroke(p, q, w0: float, w1: float | None = None):
    """A straight line from p to q, w0 wide at p tapering to w1 at q, with round ends."""
    w1 = w0 if w1 is None else w1
    w0, w1 = max(w0, MIN_FEATURE), max(w1, MIN_FEATURE)
    dx, dy = q[0] - p[0], q[1] - p[1]
    n = math.hypot(dx, dy)
    nx, ny = -dy / n, dx / n
    quad = polygon([
        (p[0] + nx * w0 / 2, p[1] + ny * w0 / 2),
        (q[0] + nx * w1 / 2, q[1] + ny * w1 / 2),
        (q[0] - nx * w1 / 2, q[1] - ny * w1 / 2),
        (p[0] - nx * w0 / 2, p[1] - ny * w0 / 2),
    ])
    return quad + bd.Pos(*p) * bd.Circle(w0 / 2) + bd.Pos(*q) * bd.Circle(w1 / 2)


def polyline(points, w0: float, w1: float | None = None):
    """A stroke through several points, tapering linearly from w0 to w1 along its length."""
    w1 = w0 if w1 is None else w1
    seg = [math.dist(a, b) for a, b in zip(points, points[1:])]
    total, run, out = sum(seg), 0.0, None
    for (a, b), s in zip(zip(points, points[1:]), seg):
        wa = w0 + (w1 - w0) * run / total
        run += s
        wb = w0 + (w1 - w0) * run / total
        piece = stroke(a, b, wa, wb)
        out = piece if out is None else out + piece
    return out


def star(center, points: int, r_out: float, r_in: float, rotation: float = 90.0):
    """A star polygon with `points` tips, the first tip at `rotation` degrees."""
    pts = []
    for i in range(points * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(rotation + i * 180.0 / points)
        pts.append((center[0] + r * math.cos(a), center[1] + r * math.sin(a)))
    return polygon(pts)


def ring(center, r_out: float, r_in: float):
    return bd.Pos(*center) * (bd.Circle(r_out) - bd.Circle(r_in))


def tassel_hole(center=(0.0, TASSEL_Y), r: float = TASSEL_R):
    return bd.Pos(*center) * bd.Circle(r)


def body(outline, relief, holes=None) -> bd.Part:
    """Base plate from `outline` plus `relief` raised on top, clipped to the outline; `holes` go through both."""
    relief = relief & bd.offset(outline, -0.6)
    if holes is not None:
        outline = outline - holes
        relief = relief - holes
    plate = bd.extrude(outline, amount=BASE)
    raised = bd.Pos(0, 0, BASE) * bd.extrude(relief, amount=RELIEF)
    return plate + raised
