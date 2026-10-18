"""Front layer of the Japan shadow box: pink ground with a rose wave, the map
of Japan, a cherry branch, a torii gate and a five-story pagoda.

The map is held up by the branch (through Kyushu) and the torii's top beam
(under Honshu's Pacific coast); the islands are grown slightly so the narrow
straits close and the map is one piece.
"""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import step, stl, threemf

from japan_common import BLACK, PINK, ROSE, band, blossom, plate_compound, plate_parts, poly, rect, stroke

GROUND = [(-90.0, 50.0), (-75.0, 52.0), (-50.0, 44.0), (-20.0, 34.0), (10.0, 30.0), (40.0, 40.0),
          (70.0, 52.0), (90.0, 50.0)]
WAVE = [(-90.0, 26.0), (-50.0, 22.0), (0.0, 18.0), (50.0, 22.0), (90.0, 26.0)]

# Map of Japan as (longitude, latitude), simplified
HOKKAIDO = [(139.9, 42.0), (140.4, 41.4), (141.2, 41.8), (141.0, 42.4), (141.7, 42.6), (143.3, 41.9),
            (144.5, 42.9), (145.6, 43.3), (145.2, 43.6), (145.3, 44.3), (144.0, 44.1), (142.0, 45.5),
            (141.6, 45.3), (141.7, 44.3), (141.4, 43.3), (140.4, 43.3), (140.5, 42.6)]
HONSHU = [(141.4, 41.4), (141.5, 40.5), (142.0, 39.5), (141.0, 38.3), (141.0, 37.0), (140.6, 36.0),
          (140.9, 35.7), (140.0, 35.0), (139.8, 35.3), (139.1, 35.1), (138.8, 34.6), (138.2, 34.6),
          (137.0, 34.6), (136.8, 34.3), (136.0, 33.5), (135.2, 33.8), (135.0, 34.6), (134.0, 34.6),
          (133.0, 34.4), (132.0, 33.9), (131.0, 34.0), (130.9, 34.4), (131.5, 34.7), (132.6, 35.5),
          (133.5, 35.5), (135.2, 35.7), (136.0, 36.0), (136.8, 37.2), (137.3, 36.8), (138.5, 37.4),
          (139.5, 38.2), (140.0, 39.5), (140.0, 40.5), (140.3, 41.2), (140.8, 41.2)]
SHIKOKU = [(132.5, 33.3), (132.5, 33.9), (133.5, 34.3), (134.6, 34.2), (134.7, 33.8), (134.2, 33.2),
           (133.0, 32.8)]
KYUSHU = [(129.6, 33.3), (130.0, 33.9), (130.9, 33.9), (131.6, 33.6), (131.9, 32.6), (131.4, 31.4),
          (130.7, 31.0), (130.2, 31.3), (130.4, 32.2), (129.8, 32.7)]
MAP_ORIGIN = (129.5, 31.0, -75.0, 52.0)  # lon, lat -> x, y
MAP_SCALE = 9.4  # mm per degree
MAP_GROW = 1.3

BRANCHES = [
    ([(-72.0, 36.0), (-68.0, 60.0), (-60.0, 80.0), (-52.0, 96.0), (-48.0, 110.0)], 3.8),
    ([(-68.0, 60.0), (-80.0, 72.0), (-86.0, 78.0)], 2.6),
    ([(-60.0, 80.0), (-70.0, 96.0), (-74.0, 106.0)], 2.6),
    ([(-52.0, 96.0), (-40.0, 101.0)], 2.2),
]
BLOSSOMS = [(-86, 81, 0), (-74, 109, 20), (-48, 113, 35), (-38, 102, 10), (-80, 69, 45), (-61, 69, 15),
            (-57, 91, 30), (-66, 100, 5)]

TORII_X = 10.0
PAGODA_X = 62.0


def japan_map():
    lon0, lat0, x0, y0 = MAP_ORIGIN
    islands = None
    for pts in (HOKKAIDO, HONSHU, SHIKOKU, KYUSHU):
        face = poly([(x0 + (lon - lon0) * MAP_SCALE, y0 + (lat - lat0) * MAP_SCALE) for lon, lat in pts])
        islands = face if islands is None else islands + face
    return bd.offset(islands, MAP_GROW)


def torii():
    c = TORII_X
    top = poly([(c - 26, 89), (c - 20, 86), (c + 20, 86), (c + 26, 89), (c + 24, 82), (c + 20, 79.5),
                (c - 20, 79.5), (c - 24, 82)])
    gate = top + rect(c - 21, c + 21, 77, 80) + rect(c - 18, c + 18, 69, 73) + rect(c - 2, c + 2, 72, 78)
    for sx in (-1, 1):
        x = c + sx * 13
        gate = gate + poly([(x - 2.6, 26), (x + 2.6, 26), (x + 2.0, 80), (x - 2.0, 80)])
    return gate


def pagoda():
    c = PAGODA_X
    shape = rect(c - 13, c + 13, 40, 46) + rect(c - 8, c + 8, 44, 146)
    for i in range(5):
        z = 56 + i * 18
        w = 36 - i * 3.5
        shape = shape + poly([(c - w / 2 - 2, z + 3), (c - w / 2, z), (c + w / 2, z), (c + w / 2 + 2, z + 3),
                              (c + w / 2 - 5, z + 6), (c - w / 2 + 5, z + 6)])
        shape = shape + rect(c - 7 + i * 0.6, c + 7 - i * 0.6, z + 5, z + 18)
    shape = shape + rect(c - 1.2, c + 1.2, 140, 170)
    for k in range(5):
        z = 148 + k * 4.5
        shape = shape + rect(c - 3, c + 3, z, z + 1.5)
    return shape


def front_parts():
    branch = None
    for pts, w in BRANCHES:
        s = stroke(pts, w)
        branch = s if branch is None else branch + s
    flowers = None
    for x, y, t in BLOSSOMS:
        b = blossom(x, y, turn=t)
        flowers = b if flowers is None else flowers + b
    return plate_parts("front_ground", band(GROUND), PINK, [
        ("wave", band(WAVE), ROSE, 0.8),
        ("japan_map", japan_map(), ROSE, 1.2),
        ("branch", branch, BLACK, 1.6),
        ("blossoms", flowers, PINK, 2.2),
        ("torii", torii(), BLACK, 1.6),
        ("pagoda", pagoda(), BLACK, 1.6),
    ])


@step(out="../STEP/japan_front.step")
@stl(out="../STL/japan_front.stl")
@threemf(out="../3MF/japan_front.3mf")
def japan_front():
    return plate_compound("front", front_parts())


if __name__ == "__main__":
    japan_front()
