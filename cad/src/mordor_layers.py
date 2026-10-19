"""Layer shapes for the Middle Earth shadow box (sky, mountains, volcano,
bridge, front)."""

from __future__ import annotations

from cadgen import srgb

from japan_common import PLATE_X, band, circle, poly, rect, stroke
from nyc_common import cloud
from scene_common import ellipse, figure, jag_band, title, union

SKY = srgb("#C8632A")
DUSK = srgb("#5A3324")
EMBER = srgb("#E8892F")
FIRE = srgb("#F2A03D")
YELLOW = srgb("#F7D25B")
BLACK = srgb("#1C1C1E")
GOLD = srgb("#D4A537")
ROCK = srgb("#55585E")
ROCK_DARK = srgb("#2E3034")
RUST = srgb("#8E3B2C")
RUST_DARK = srgb("#5E2820")
LAVA = srgb("#F05A1E")
DEAD_RED = srgb("#A33A2E")


def beast(cx, cy, s=1.0):
    """Winged silhouette in flight."""
    pts = [(-14, 4), (-8, 1.5), (-4, 3.5), (-1.5, 1), (0, 3.5), (1.5, 1), (4, 3.5), (8, 1.5), (14, 4),
           (6, -2), (2, -2.5), (0, -6), (-2, -2.5), (-6, -2)]
    return poly([(cx + x * s, cy + y * s) for x, y in pts])


def dark_cloud(cx, cy, s):
    return cloud(cx, cy, s) & rect(cx - 10 * s, cx + 10 * s, cy - 0.6 * s, cy + 0.9 * s)


def peak_shadows(peaks, depth=26.0):
    return union([poly([(x, y), (x + 9, y - depth * 0.7), (x + 1, y - depth)]) for x, y in peaks])


# --- sky (back) ---------------------------------------------------------------

def sky():
    eye = (-14.0, 134.0)
    return SKY, [
        ("dusk", rect(-PLATE_X, PLATE_X, 162, 220), DUSK, 0.4),
        ("horizon_glow", band([(-90, 82), (90, 82)]), EMBER, 0.4),
        ("eye_flame", ellipse(*eye, 26, 18), FIRE, 0.8),
        ("eye_iris", ellipse(*eye, 15, 11), YELLOW, 1.2),
        ("eye_pupil", ellipse(*eye, 2.4, 10.5), BLACK, 1.6),
        ("clouds", union([dark_cloud(-58, 114, 6), dark_cloud(56, 120, 6), dark_cloud(-60, 92, 5),
                          dark_cloud(50, 146, 5)]), DUSK, 0.8),
        ("beasts", beast(-52, 140, 1.0) + beast(-30, 106, 0.7) + beast(64, 100, 0.6), BLACK, 1.4),
        ("title", title("MIDDLE EARTH", 16.0, (0, 190)), GOLD, 1.4),
    ]


# --- jagged mountains and the dark tower ----------------------------------------------

def mountains():
    peaks = [(-80, 120), (-60, 136), (-40, 124), (44, 140), (62, 152), (82, 130)]
    top = [(-90, 92), (-80, 120), (-70, 104), (-60, 136), (-48, 110), (-40, 124), (-30, 100), (-18, 94),
           (10, 94), (30, 108), (44, 140), (52, 118), (62, 152), (72, 122), (82, 130), (90, 110)]
    c = 14.0
    tower = rect(c - 10, c + 10, 80, 116) + rect(c - 8, c + 8, 115, 140) + rect(c - 6, c + 6, 139, 158)
    tower = tower + rect(c - 4.5, c + 4.5, 157, 166)
    tower = tower + poly([(c - 4.5, 165), (c - 0.8, 165), (c - 2.6, 178), (c - 7, 182)])
    tower = tower + poly([(c + 4.5, 165), (c + 0.8, 165), (c + 2.6, 178), (c + 7, 182)])
    for y, w in ((116, 10), (140, 8), (158, 6)):  # buttress spikes at each setback
        tower = tower + poly([(c - w - 3, y - 4), (c - w, y - 8), (c - w, y)]) + poly([(c + w + 3, y - 4), (c + w, y - 8), (c + w, y)])
    windows = rect(c - 1, c + 1, 147, 153) + rect(c - 1, c + 1, 124, 130)
    return ROCK, [
        ("shadows", peak_shadows(peaks), ROCK_DARK, 0.8),
        ("dark_tower", tower, BLACK, 1.2),
        ("tower_windows", windows, EMBER, 1.4),
    ], jag_band(top)


# --- the volcano ----------------------------------------------------------------------

def volcano():
    top = [(-90, 58), (-80, 66), (-60, 62), (-40, 88), (-22, 118), (-12, 124), (-2, 121), (8, 116), (30, 86),
           (48, 70), (54, 96), (60, 112), (66, 92), (74, 106), (80, 82), (90, 74)]
    spire = rect(-76, -66, 56, 112) + poly([(-78, 111), (-64, 111), (-68, 124), (-71, 132), (-74, 124)])
    spire = spire + poly([(-80, 98), (-76, 92), (-76, 104)]) + poly([(-62, 98), (-66, 92), (-66, 104)])
    lava = union([
        stroke([(-8, 121), (-14, 105), (-10, 92), (-20, 78), (-16, 60)], 2.6),
        stroke([(-5, 120), (2, 104), (-2, 90), (8, 76), (4, 58)], 2.6),
        stroke([(-12, 108), (-28, 96), (-34, 78)], 2.0),
        stroke([(-1, 100), (16, 90), (22, 72)], 2.0),
    ])
    return RUST, [
        ("rock_shadows", peak_shadows([(-22, 118), (8, 116), (60, 112), (74, 106)], 30), RUST_DARK, 0.8),
        ("spire", spire, ROCK_DARK, 1.2),
        ("lava", lava, LAVA, 1.2),
        ("crater_glow", ellipse(-7, 121, 6, 2.2), FIRE, 1.6),
    ], jag_band(top)


# --- three-arch stone bridge ----------------------------------------------------------

def bridge():
    arches = [-45.0, 0.0, 45.0]
    ground = jag_band([(-90, 40), (-60, 36), (0, 38), (60, 35), (90, 40)])
    deck = rect(-80, 80, 30, 78)
    for k in range(16):  # broken battlements along the top
        x = -78 + k * 10
        deck = deck + rect(x, x + 5, 77, 81 + (k % 3))
    ends = jag_band([(-90, 88), (-82, 80), (-78, 70)], 30) + poly([(78, 30), (78, 72), (84, 84), (90, 86), (90, 30)])
    openings = union([rect(x - 15, x + 15, 34, 50) + circle(x, 50, 15) for x in arches])
    body = deck + ends + ground - openings
    rings = union([(circle(x, 50, 18) - circle(x, 50, 15)) & rect(x - 20, x + 20, 50, 70) for x in arches])
    stones = union([rect(-80, 80, y, y + 0.8) for y in (40, 48, 56, 64, 72)])
    stones = stones + union([rect(-80 + i * 9 + (row % 2) * 4.5, -79.2 + i * 9 + (row % 2) * 4.5, 40 + row * 8, 48 + row * 8)
                             for row in range(5) for i in range(18)])
    stones = (stones & (deck - openings)) - rings
    return ROCK_DARK, [
        ("mortar", stones, ROCK, 0.6),
        ("arch_rings", rings, ROCK, 1.0),
    ], body


# --- front: dead trees, a ruined arch and travelers ---------------------------------

def front():
    base = jag_band([(-90, 36), (-70, 40), (-40, 30), (0, 34), (30, 30), (60, 40), (90, 44)])
    red_tree = union([stroke([(-64, 30), (-60, 60), (-64, 92)], 4.0), stroke([(-60, 60), (-46, 78), (-40, 96)], 2.6),
                      stroke([(-61, 70), (-76, 86), (-80, 100)], 2.4), stroke([(-63, 82), (-56, 102)], 2.0),
                      stroke([(-46, 78), (-34, 84)], 1.6), stroke([(-76, 86), (-70, 98)], 1.6)])
    black_tree = union([stroke([(72, 40), (74, 80), (70, 112)], 4.0), stroke([(74, 70), (86, 92)], 2.4),
                        stroke([(72, 86), (58, 104)], 2.4), stroke([(70, 100), (78, 120)], 2.0),
                        stroke([(86, 92), (88, 104)], 1.6), stroke([(58, 104), (54, 112)], 1.6)])
    small_red = union([stroke([(56, 36), (58, 58)], 2.4), stroke([(58, 50), (66, 64)], 1.6), stroke([(57, 54), (48, 66)], 1.6)])
    outer = rect(-30, -10, 30, 74) + poly([(-30, 73), (-10, 73), (-20, 92)])
    inner = rect(-26, -14, 30, 72) + poly([(-26, 71), (-14, 71), (-20, 86)])
    ruin = outer - inner - poly([(-17, 78), (-8, 96), (-8, 66)]) + rect(-4, 2, 30, 52) - poly([(-4, 50), (-4, 53), (2, 53), (2, 44)])
    walkers = union([figure(12, 32, 13, staff=True, hat=True), figure(22, 31, 10), figure(30, 30, 10),
                     figure(38, 31, 12), figure(46, 33, 9)])
    return ROCK_DARK, [
        ("ruin", ruin, ROCK, 1.4),
        ("red_tree", red_tree + small_red, DEAD_RED, 1.4),
        ("black_tree", black_tree, BLACK, 1.4),
        ("travelers", walkers, BLACK, 1.6),
    ], base
