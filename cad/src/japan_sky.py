"""Back layer of the Japan shadow box: sky, rising sun, distant pink hills
and a cherry branch reaching in from the top-left corner."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import (
    BLACK, PINK, RED, SKY, PLATE_X, band, blossom, circle, plate_compound, plate_parts, plate_rect, stroke,
)

SUN = (8.0, 150.0, 40.0)
HILLS = [(-90.0, 96.0), (-60.0, 104.0), (-25.0, 92.0), (10.0, 99.0), (50.0, 88.0), (90.0, 97.0)]
BRANCHES = [
    [(-PLATE_X + 2.0, 197.0), (-72.0, 191.0), (-48.0, 187.0), (-22.0, 193.0)],
    [(-72.0, 191.0), (-64.0, 177.0), (-55.0, 170.0)],
    [(-48.0, 187.0), (-40.0, 199.0)],
    [(-84.0, 194.0), (-86.0, 181.0)],
]
BLOSSOMS = [(-78, 200, 0), (-60, 196, 20), (-32, 197, 40), (-21, 186, 10), (-55, 168, 30),
            (-86, 178, 15), (-40, 202, 5), (-66, 182, 50), (-47, 179, 25)]


def sky_parts():
    branch = stroke(BRANCHES[0], 4.0)
    for pts in BRANCHES[1:]:
        branch = branch + stroke(pts, 2.6)
    flowers = blossom(*BLOSSOMS[0][:2], turn=BLOSSOMS[0][2])
    for x, y, t in BLOSSOMS[1:]:
        flowers = flowers + blossom(x, y, turn=t)
    return plate_parts("sky", plate_rect(), SKY, [
        ("distant_hills", band(HILLS), PINK, 0.8),
        ("sun", circle(*SUN), RED, 1.2),
        ("branch", branch, BLACK, 1.0),
        ("blossoms", flowers, PINK, 1.6),
    ])


@step(out="../STEP/japan_sky.step")
@stl(out="../STL/japan_sky.stl")
@threemf(out="../3MF/japan_sky.3mf")
def japan_sky():
    return plate_compound("sky", sky_parts())


if __name__ == "__main__":
    japan_sky()
