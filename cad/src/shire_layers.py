"""Layer shapes for The Shire shadow box (sky, hill, smials, bridge, front).

Each layer is a list of (label, shape, color, relief) details on a base
outline, laid out for the japan_common plate system.
"""

from __future__ import annotations

from cadgen import srgb

from japan_common import band, circle, poly, rect, stroke
from nyc_common import cloud
from nyc_front import tree
from scene_common import figure, title, union, wheel

SKY = srgb("#9FD0EE")
GLOW = srgb("#F6E7A0")
CLOUD = srgb("#F7F7F5")
GOLD = srgb("#D4A537")
GRASS_LIGHT = srgb("#9BCB5B")
GRASS = srgb("#5FA83A")
GRASS_DARK = srgb("#3E7D2C")
TREE_DARK = srgb("#2F5E25")
BARK = srgb("#6B4A2B")
WOOD = srgb("#7A5533")
SOIL = srgb("#5A3E2A")
PATH = srgb("#8B6A44")
STONE = srgb("#9A9A94")
WATER = srgb("#4F95C9")
DOOR_GREEN = srgb("#2E7D3A")
DOOR_RED = srgb("#C0392B")
DOOR_BLUE = srgb("#2F6FB5")
YELLOW = srgb("#E8C547")
HORSE = srgb("#4A3426")
CLOAK = srgb("#3B5B3A")


def round_door(cx, cy, r, color):
    frame = circle(cx, cy, r + 2.2) - circle(cx, cy, r)
    planks = circle(cx, cy, r) - union([rect(cx + dx - 0.4, cx + dx + 0.4, cy - r, cy + r) for dx in (-r / 2, 0, r / 2)])
    return [("door_frame", frame, WOOD, 1.2), ("door", planks, color, 0.8),
            ("door_knob", circle(cx, cy, 1.8), YELLOW, 1.6)]


def round_window(cx, cy, r):
    frame = circle(cx, cy, r + 1.6) - circle(cx, cy, r)
    glass = circle(cx, cy, r) - rect(cx - 0.4, cx + 0.4, cy - r, cy + r) - rect(cx - r, cx + r, cy - 0.4, cy + 0.4)
    return [("window_frame", frame, WOOD, 1.2), ("window", glass, YELLOW, 0.6)]


def bush(cx, cy, r):
    return circle(cx, cy, r) + circle(cx - r * 0.8, cy - r * 0.3, r * 0.7) + circle(cx + r * 0.8, cy - r * 0.25, r * 0.72)


def fence(points, h=9.0, step=11.0):
    """Two-rail fence along a polyline of rail heights."""
    shape = stroke(points, 1.4) + stroke([(x, y + h * 0.55) for x, y in points], 1.4)
    for (xa, ya), (xb, yb) in zip(points, points[1:]):
        n = max(1, int(abs(xb - xa) // step))
        for i in range(n + 1):
            x = xa + (xb - xa) * i / n
            y = ya + (yb - ya) * i / n
            shape = shape + rect(x - 0.9, x + 0.9, y - 3, y + h)
    return shape


# --- sky (back) ---------------------------------------------------------------

def sky():
    far = band([(-90, 98), (-60, 112), (-30, 102), (0, 114), (30, 104), (60, 116), (90, 106)])
    mid = band([(-90, 72), (-50, 88), (-10, 78), (30, 90), (70, 80), (90, 84)])
    near = band([(-90, 46), (-40, 60), (10, 52), (50, 62), (90, 54)])
    lone_tree = tree(44, 124, 7.0, 104.0)
    return SKY, [
        ("sun_glow", circle(30, 150, 30), GLOW, 0.6),
        ("clouds", union([cloud(-62, 152, 7), cloud(58, 168, 6), cloud(-26, 130, 5)]), CLOUD, 1.0),
        ("far_hills", far, GRASS_LIGHT, 0.6),
        ("lone_tree", lone_tree, TREE_DARK, 1.0),
        ("mid_hills", mid, GRASS, 1.0),
        ("road", stroke([(-84, 64), (-50, 76), (-20, 82), (8, 92)], 2.6), PATH, 1.2),
        ("near_hills", near, GRASS_DARK, 1.4),
        ("title", title("THE SHIRE", 24.0, (0, 190)), GOLD, 1.4),
    ]


# --- the Hill with the big tree --------------------------------------------------

def hill():
    base = band([(-90, 60), (-60, 80), (-30, 100), (0, 112), (30, 116), (60, 108), (90, 96)])
    trunk = poly([(18, 108), (30, 108), (28, 126), (34, 136), (30, 138), (24, 130), (18, 138), (14, 136), (20, 126)])
    canopy = union([circle(24, 150, 17), circle(8, 143, 11), circle(40, 143, 11), circle(14, 160, 10),
                    circle(36, 160, 10), circle(24, 166, 9)])
    rail = [(-82, 58), (-50, 70), (-15, 76)]
    return GRASS, [
        ("tree_trunk", trunk, BARK, 1.0),
        ("tree_canopy", canopy, TREE_DARK, 1.6),
        ("fence", fence(rail), WOOD, 1.4),
        *round_door(10, 82, 12, DOOR_GREEN),
        *round_window(50, 92, 6),
        ("flowers_red", union([circle(-8, 70, 2.4), circle(-4, 66, 2.0)]), DOOR_RED, 1.4),
        ("flowers_yellow", union([circle(28, 72, 2.4), circle(32, 68, 2.0)]), YELLOW, 1.4),
    ], base


# --- lower hill with two smials ----------------------------------------------------

def smials():
    base = band([(-90, 72), (-60, 84), (-20, 78), (20, 86), (60, 92), (90, 82)])
    wall = rect(-88, -24, 26, 34) + rect(52, 88, 30, 38)
    joints = union([rect(x, x + 0.6, 26, 34) for x in range(-84, -24, 7)] + [rect(-88, -24, 29.7, 30.3)])
    return GRASS, [
        ("soil", band([(-90, 24), (90, 24)]), SOIL, 0.6),
        ("chimney", rect(-20, -15, 76, 90), STONE, 1.0),
        ("stone_wall", wall, STONE, 1.2),
        ("wall_joints", joints & wall, SOIL, 1.4),
        *[(f"red_{l}", s, c, r) for l, s, c, r in round_door(-55, 48, 11, DOOR_RED)],
        *[(f"blue_{l}", s, c, r) for l, s, c, r in round_door(40, 60, 11, DOOR_BLUE)],
        *round_window(-28, 54, 5),
        ("bushes", union([bush(-80, 62, 8), bush(-12, 70, 7), bush(72, 86, 8), bush(14, 78, 6)]), GRASS_DARK, 1.8),
    ], base


# --- stone bridge and mill ----------------------------------------------------------

def bridge():
    base = band([(-90, 44), (-50, 50), (-10, 42), (30, 48), (60, 58), (90, 52)])
    water = poly([(-48, 12), (48, 12), (42, 38), (-42, 38)])
    deck = poly([(-46, 22), (-22, 46), (22, 46), (46, 22), (46, 18), (-46, 18)])
    span = deck - rect(-14, 14, 10, 28) - circle(0, 28, 14)
    stones = union([rect(-40 + i * 8, -39.4 + i * 8, 18, 46) for i in range(11)]) & span
    mill = rect(50, 72, 54, 76)
    roof = poly([(47, 75), (75, 75), (61, 90)])
    return GRASS_DARK, [
        ("river", water, WATER, 0.6),
        ("lane", stroke([(-46, 22), (-70, 34), (-88, 40)], 5.0), PATH, 0.8),
        ("bridge", span, STONE, 1.4),
        ("bridge_joints", stones, SOIL, 1.6),
        ("mill", mill, WOOD, 1.2),
        ("mill_roof", roof, SOIL, 1.4),
        ("mill_window", rect(56, 62, 62, 68), YELLOW, 1.4),
        ("water_wheel", wheel(44, 58, 10), HORSE, 1.8),
        ("hedges", union([bush(-72, 52, 8), bush(-56, 54, 6), bush(82, 62, 7)]), GRASS, 1.6),
    ], base


# --- front: lane with a cart, a signpost and trees ------------------------------------

def front():
    base = band([(-90, 30), (-40, 34), (0, 28), (40, 32), (90, 30)])
    grass = base - band([(-90, 22), (90, 22)])
    left_tree = tree(-64, 78, 14.0, 26.0)
    right_tree = tree(72, 86, 13.0, 26.0)
    sign = rect(-12, -9.5, 24, 64) + poly([(-26, 54), (-2, 54), (2, 56.5), (-2, 59), (-26, 59)])
    sign = sign + poly([(-14, 45), (10, 45), (14, 47.5), (10, 50), (-14, 50)])
    cart = rect(24, 58, 42, 54) + rect(26, 30, 54, 60) + rect(52, 56, 54, 60) + rect(10, 26, 44, 46)
    horse = (poly([(-2, 46), (14, 46), (16, 54), (0, 55)]) + poly([(0, 52), (-6, 62), (-3, 64), (4, 55)])
             + poly([(-6, 60), (-11, 58), (-10, 61), (-4, 66)]))
    horse = horse + union([rect(x, x + 1.8, 26, 47) for x in (0, 3.5, 10, 13)]) + poly([(15, 53), (19, 46), (18, 53)])
    return SOIL, [
        ("grass", grass, GRASS, 0.8),
        ("trees", left_tree + right_tree, TREE_DARK, 1.4),
        ("trunks", rect(-66, -62, 26, 70) + rect(70, 74, 26, 78), BARK, 1.6),
        ("fence", fence([(-46, 34), (-24, 34)], 8.0, 7.0), WOOD, 1.4),
        ("signpost", sign, WOOD, 1.6),
        ("cart", cart, WOOD, 1.4),
        ("cart_wheels", wheel(30, 36, 7) + wheel(50, 36, 7), HORSE, 1.8),
        ("horse", horse, HORSE, 1.6),
        ("driver", figure(40, 54, 10), CLOAK, 1.8),
        ("walker", figure(-4, 28, 11), CLOAK, 1.8),
    ], base
