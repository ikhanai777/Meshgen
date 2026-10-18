"""Back layer of the New York shadow box: sky with the "NEW YORK" title, a
golden sun, clouds and a little biplane."""

from __future__ import annotations

from pathlib import Path

from cadgen import build123d as bd
from cadgen import step, stl, threemf

from japan_common import circle, plate_compound, plate_parts, plate_rect, poly, rect, stroke
from nyc_common import BRONZE, CLOUD, GOLD, SKY, cloud

FONT = Path(__file__).resolve().parent.parent / "fonts" / "DejaVuSerif-Bold.ttf"
TITLE = ("NEW YORK", 23.0, (0.0, 193.0))
SUN = (14.0, 130.0, 32.0)
CLOUDS = [(-62.0, 160.0, 7.0), (64.0, 162.0, 7.0), (44.0, 122.0, 6.0), (-64.0, 112.0, 6.5), (-8.0, 104.0, 4.5)]
PLANE = (-58.0, 138.0)


def title():
    text, size, (x, y) = TITLE
    sketch = bd.Text(text, size, font_path=str(FONT), align=(bd.Align.CENTER, bd.Align.CENTER))
    return sketch.moved(bd.Location((x, y, 0)))


def biplane():
    x, y = PLANE
    body = stroke([(x - 12, y + 1.0), (x + 10, y)], 3.2)  # fuselage
    body = body + poly([(x + 10, y - 1.6), (x + 14, y - 1.0), (x + 14, y + 1.0), (x + 10, y + 1.6)])  # nose
    body = body + rect(x - 1.5, x + 4.5, y + 3.5, y + 5.0) + rect(x - 1.5, x + 4.5, y - 4.0, y - 2.5)  # wings
    body = body + rect(x + 0.0, x + 1.0, y - 3.0, y + 4.0) + rect(x + 3.0, x + 4.0, y - 3.0, y + 4.0)  # struts
    body = body + poly([(x - 13, y + 0.5), (x - 15, y + 5.5), (x - 12.5, y + 5.5), (x - 10, y + 1.5)])  # tail fin
    body = body + rect(x + 14, x + 15, y - 4.0, y + 4.0)  # propeller
    return body + circle(x + 2.0, y - 6.0, 1.1)  # wheel


def sky_parts():
    clouds = None
    for c in CLOUDS:
        clouds = cloud(*c) if clouds is None else clouds + cloud(*c)
    return plate_parts("nyc_sky", plate_rect(), SKY, [
        ("sun", circle(*SUN), GOLD, 0.8),
        ("clouds", clouds, CLOUD, 1.2),
        ("biplane", biplane(), BRONZE, 1.6),
        ("title", title(), GOLD, 1.2),
    ])


@step(out="../STEP/nyc_sky.step")
@stl(out="../STL/nyc_sky.stl")
@threemf(out="../3MF/nyc_sky.3mf")
def nyc_sky():
    return plate_compound("nyc_sky", sky_parts())


if __name__ == "__main__":
    nyc_sky()
