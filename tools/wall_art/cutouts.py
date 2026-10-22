# Generates single-colour line-art cutouts for the wall (no backing plate) and builds them with SolidCraft.
# Every design is stroked line work joined into one piece: floating parts get a short bridge to the rest. A solid
# tab at the top carries a keyhole hanger on the back. Prints flat on any FDM printer, no supports, no colour swaps.
# Usage: python tools/wall_art/cutouts.py OUT_DIR [design ...]
# Needs: pip install shapely numpy, and solidcraft-cli on PATH (https://github.com/tangency-labs/solidcraft).
import sys, os, json, math, subprocess, numpy as np
from shapely.geometry import Point, Polygon, LineString, box
from shapely.ops import nearest_points
from shapely import affinity as A
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wall_art import U, place, circ, ell, ring, stroke, rrect, bez, fn_curve, star, sparkle, heart, leaf, maple, scatter, clean, sketch_cmds

T = 4.0           # thickness
W = 2.6           # default line width (about 6 extrusion widths)
R = 92            # radius of the round frames
COLOUR = "#c9b3f2"

def O(g, w=W): return g.boundary.buffer(w / 2, quad_segs=8)      # outline of a filled shape
def frame(w=3.4): return ring(0, 0, R, R, w)
def arc(cx, cy, r, a0, a1, w=W, n=60):
    return stroke([(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in np.linspace(a0, a1, n)], w)
def inside(g, r=R - 1): return g.intersection(circ(0, 0, r))
def chord_y(y, r=R): x = math.sqrt(max(r * r - y * y, 0)); return stroke([(-x, y), (x, y)], W)

# ---------- designs ----------
def battle_station():
    rng = np.random.default_rng(4); r = 88
    dish = U(ring(-36, 36, 22, 22, W), ring(-36, 36, 12, 12, W), circ(-36, 36, 4),
             [stroke([(-36 + 4 * math.cos(a), 36 + 4 * math.sin(a)), (-36 + 22 * math.cos(a), 36 + 22 * math.sin(a))], 1.8) for a in np.linspace(0, 2 * math.pi, 13)[:-1]])
    lines = [chord_y(y, r) for y in (-3, 3)]
    bands = [-84, -70, -55, -40, -26, -12, 12, 26, 40, 55, 70, 84]
    lines += [chord_y(y, r) for y in bands]
    edges = [-88, -84, -70, -55, -40, -26, -12, -3, 3, 12, 26, 40, 55, 70, 84, 88]
    for y0, y1 in zip(edges, edges[1:]):
        if (y0, y1) == (-3, 3): continue
        for s in np.sort(rng.uniform(-0.95, 0.95, rng.integers(7, 12))):   # meridian pieces: curved panel seams
            ys = np.linspace(y0, y1, 12)
            lines.append(stroke([(s * math.sqrt(max(r * r - y * y, 0)), y) for y in ys], 2.2))
        for _ in range(rng.integers(1, 4)):                               # greebles: short stepped segments
            y = rng.uniform(y0 + 2, y1 - 2); x = rng.uniform(-0.7, 0.7) * math.sqrt(r * r - y * y)
            lines.append(stroke([(x, y), (x + rng.uniform(6, 14), y), (x + rng.uniform(6, 14), y + (y1 - y0) * 0.3)], 1.8))
    body = U(lines).intersection(circ(0, 0, r)).difference(circ(-36, 36, 23.3))
    return U(ring(0, 0, r, r, 3.2), body, dish)

def galaxy():
    rng = np.random.default_rng(7)
    arms = []
    for off in (0, math.pi):
        pts = [(5 * math.exp(0.3 * t) * math.cos(t + off), 5 * math.exp(0.3 * t) * math.sin(t + off)) for t in np.linspace(0, 8.2, 300)]
        arms += [stroke(pts[i:i + 40], 6.5 - 4 * i / 300) for i in range(0, 260, 30)]
        pts2 = [(4 * math.exp(0.3 * t) * math.cos(t + off + 0.5), 4 * math.exp(0.3 * t) * math.sin(t + off + 0.5)) for t in np.linspace(1, 8.0, 200)]
        arms.append(stroke(pts2, 2.0))
    stars = [sparkle(x, y, rng.uniform(3.5, 6)) for x, y in scatter(rng, 16, circ(0, 0, 80), 6, U(arms).buffer(4))]
    return U(frame(), inside(U(arms)), circ(0, 0, 9), stars)

def tree_of_life():
    rng = np.random.default_rng(2); parts = []
    def branch(x, y, ang, ln, w, d, sign):
        x2, y2 = x + ln * math.cos(ang), y + sign * ln * math.sin(ang)
        parts.append(stroke([(x, y), (x2, y2)], w))
        if d == 0: parts.append(circ(x2, y2, 2.2)); return
        for da in (-0.45, 0.42) if d % 2 else (-0.38, 0.5):
            branch(x2, y2, ang + da + rng.uniform(-0.12, 0.12), ln * 0.74, max(2.0, w * 0.68), d - 1, sign)
    trunk = Polygon([(-9, -38), (9, -38), (5, -12), (-5, -12)])
    for ang in (1.05, 1.57, 2.09): branch(0, -14, ang, 30, 6, 4, 1)
    branch(0, -36, math.pi / 2, 16, 6, 4, -1)
    return U(frame(), inside(U(parts, trunk)), stroke([(-R, -38), (R, -38)], 2.6).intersection(circ(0, 0, R)))

def mountains():
    far = stroke([(-92, 0), (-60, 30), (-42, 14), (-18, 48), (4, 20), (24, 36), (50, 4), (70, 22), (92, 2)], W)
    near = stroke([(-92, -22), (-55, 14), (-35, -6), (-8, 26), (18, -12), (42, 0), (92, -30)], W)
    snow = [stroke([(-26, 36), (-18, 30), (-12, 36)], 2.0), stroke([(-14, 16), (-8, 12), (-2, 16)], 2.0)]
    sun = U(ring(42, 54, 14, 14, W), [stroke([(42 + 18 * math.cos(a), 54 + 18 * math.sin(a)), (42 + 24 * math.cos(a), 54 + 24 * math.sin(a))], 2.0) for a in np.linspace(0, 2 * math.pi, 13)[:-1]])
    pines = [O(Polygon([(x - w, -60), (x + w, -60), (x, -60 + h)])) for x, w, h in ((-60, 9, 34), (-44, 8, 28), (-30, 10, 40), (44, 9, 32), (60, 8, 26))]
    lake = [stroke([(-25 + d, -66 - k * 7), (25 - d, -66 - k * 7)], 2.0) for k, d in enumerate((0, 8, 16))]
    ground = stroke([(-92, -60), (92, -60)], W)
    return U(frame(), inside(U(far, near, snow, sun, pines, lake, ground)))

def moon_stars():
    moon = circ(0, 0, 70).difference(circ(26, 14, 60))
    stars = []
    for x, y_end, r in ((10, -40, 10), (34, -20, 8), (54, -54, 11), (24, -70, 7)):
        stars.append(star(x, y_end, r, r * 0.45, 5))
        stars.append(stroke([(x, y_end + r * 0.9), (x, 66 if x < 30 else 60)], 1.8))
    hang = stroke([(-10, 72), (60, 60)], W)
    return U(O(moon, 3.0), stars, hang, sparkle(-40, 10, 7), sparkle(-30, -30, 5))

def coffee_love():
    mug = rrect(-34, -70, 30, 0, 9)
    handle = ring(36, -34, 13, 16, W)
    steam = O(heart(-2, 34, 30), 3.0)
    wisp = stroke(bez((-2, 2), (-10, 8), (6, 12), (-2, 20)), W)
    beans = []
    for x, y, a in ((-62, -40, 30), (62, 20, -40), (-60, 20, 70), (60, -64, 10)):
        groove = stroke(bez((0, -9), (3, -3), (-3, 3), (0, 9)), 1.8)
        beans.append(place(U(O(ell(0, 0, 7, 10)), groove), x, y, rot=a))
    heart_on_mug = O(heart(-2, -34, 15))
    return U(frame(), mug.boundary.buffer(1.5), handle, steam, wisp, beans, heart_on_mug)

def teapot():
    body = ell(-6, -10, 52, 40)
    lid = U(ell(-6, 26, 26, 9).intersection(box(-40, 26, 30, 50)), circ(-6, 38, 6))
    spout = Polygon(bez((38, -14), (60, -8), (62, 18), (80, 32)) + [(84, 26)] + bez((70, 16), (60, -4), (56, -26), (40, -28))[1:]).buffer(0.5)
    handle = ring(-64, -6, 16, 22, 4)
    foot = rrect(-36, -54, 24, -46, 3)
    silhouette = U(body, lid, spout, foot)
    band = [stroke(fn_curve(lambda x, o=o: o + 4 * math.sin(x / 9), -52, 40), 2.0).intersection(body) for o in (-18, -6)]
    steam = [stroke([(o + 4 * math.sin(y / 6), y) for y in np.linspace(52, 80, 40)], 2.2) for o in (-14, -4, 6)]
    return U(O(silhouette, 3.0), handle, band, steam, ring(-6, 26, 26, 9, 2.0).intersection(box(-40, 26, 30, 50)))

def mandala():
    parts = [ring(0, 0, r, r, 2.2) for r in (12, 34, 62, 90)]
    parts += [O(A.rotate(place(leaf(22, 14), 0, 12), a, origin=(0, 0))) for a in range(0, 360, 30)]
    parts += [O(A.rotate(place(leaf(28, 18), 0, 34), a, origin=(0, 0))) for a in range(15, 375, 30)]
    parts += [O(A.rotate(place(leaf(28, 12), 0, 62), a, origin=(0, 0))) for a in range(0, 360, 20)]
    parts += [circ(48 * math.cos(math.radians(a)), 48 * math.sin(math.radians(a)), 2.5) for a in range(15, 375, 30)]
    return U(parts, circ(0, 0, 5))

def lotus():
    petals = [O(place(leaf(78, 34), 0, -30, rot=a)) for a in (-62, -31, 0, 31, 62)]
    inner = [O(place(leaf(50, 22), 0, -30, rot=a)) for a in (-16, 16)]
    water = [stroke(fn_curve(lambda x, o=o, p=p: o + 3 * math.sin(x / 8 + p), -70 + i * 14, 70 - i * 14), 2.2) for i, (o, p) in enumerate(((-36, 0), (-46, 1.5), (-56, 3)))]
    base = stroke(bez((-46, -30), (-20, -40), (20, -40), (46, -30)), W)
    dots = [circ(x, y, 3) for x, y in ((-60, 30), (60, 30), (0, 62))]
    return U(petals, inner, water, base, dots)

def anchor():
    shank = stroke([(0, 62), (0, -70)], 6)
    eye = ring(0, 72, 10, 10, 4.5)
    stock = rrect(-34, 46, 34, 54, 4)
    arms = stroke([(58 * math.cos(t), -18 + 58 * math.sin(t)) for t in np.linspace(math.radians(200), math.radians(340), 80)], 6)
    flukes = [Polygon([(x, -38), (x + d * 14, -42), (x + d * 2, -26)]) for x, d in ((-54, 1), (54, -1))]
    rope = stroke(bez((0, 72), (40, 60), (-40, 20), (20, -10)) + bez((20, -10), (60, -30), (-30, -50), (8, -64))[1:], 2.4)
    return U(shank, eye, stock, arms, [f.buffer(2) for f in flukes], rope)

def wave():
    outer = bez((-92, -40), (-70, -10), (-55, 30), (-25, 50)) + bez((-25, 50), (0, 62), (24, 50), (30, 30))[1:]
    curl = bez((30, 30), (22, 36), (10, 38), (2, 30)) + bez((2, 30), (-6, 22), (0, 12), (10, 14))[1:] + bez((10, 14), (0, -2), (30, -20), (92, -30))[1:]
    inner = [stroke(bez((-80, -48 + d), (-60, -22 + d), (-48, 12 + d * 0.5), (-24, 30 + d * 0.3)), 2.2) for d in (0, -12, -24)]
    claws = [O(place(leaf(10, 5, 1.2), x, y, rot=r), 2.0) for (x, y), r in zip(outer[22:74:5], np.linspace(-20, -110, 11))]
    swells = [stroke(fn_curve(lambda x, o=o: o + 3 * math.sin(x / 7), -92, 92), 2.2) for o in (-58, -70, -82)]
    sun = ring(50, 50, 14, 14, W)
    fuji = stroke([(20, -8), (36, 14), (46, 14), (66, -12)], W)
    return U(frame(), inside(U(stroke(outer, 3.0), stroke(curl, 3.0), inner, claws, swells, sun, fuji)))

def compass():
    parts = [ring(0, 0, R, R, 3.4), ring(0, 0, 80, 80, 2.2)]
    parts += [stroke([(80 * math.cos(math.radians(a)), 80 * math.sin(math.radians(a))), ((80 - (8 if a % 45 == 0 else 4)) * math.cos(math.radians(a)), (80 - (8 if a % 45 == 0 else 4)) * math.sin(math.radians(a)))], 2.0) for a in range(0, 360, 15)]
    big = star(0, 0, 70, 14, 4); small = star(0, 0, 46, 10, 4, rot=135)
    parts += [O(big), O(small), circ(0, 0, 5)]
    parts += [stroke([(0, 0), (70 * math.cos(math.radians(a)), 70 * math.sin(math.radians(a)))], 1.8) for a in (0, 90, 180, 270)]
    return U(parts)

def solar_system():
    parts = [ring(0, 0, 11.5, 11.5, 5)]
    parts += [ring(0, 0, r, r, 2.0) for r in (26, 40, 54, 68, 82)]
    rng = np.random.default_rng(9)
    for r, pr in zip((26, 40, 54, 68, 82), (3.5, 5, 4.5, 8, 6)):
        a = rng.uniform(0, 2 * math.pi); x, y = r * math.cos(a), r * math.sin(a)
        parts.append(circ(x, y, pr))
        if pr == 8: parts.append(ring(x, y, 14, 4, 2.0, 25))
    rays = [stroke([(18 * math.cos(a), 18 * math.sin(a)), (22 * math.cos(a), 22 * math.sin(a))], 2.0) for a in np.linspace(0, 2 * math.pi, 13)[:-1]]
    return U(frame(), parts, rays)

def rocket():
    body = Polygon(bez((0, 70), (22, 50), (22, 10), (16, -30)) + [(-16, -30)] + bez((-16, -30), (-22, 10), (-22, 50), (0, 70))[1:])
    fins = [Polygon([(16, -6), (34, -40), (14, -30)]), Polygon([(-16, -6), (-34, -40), (-14, -30)])]
    window = ring(0, 26, 9, 9, W)
    flame = stroke([(-10, -32), (-6, -50), (-2, -40), (0, -62), (2, -40), (6, -50), (10, -32)], 2.4)
    rocket_g = U(O(U(body, fins), 3.0), window, flame, stroke([(-19, -6), (19, -6)], 2.0).intersection(body))
    rocket_g = place(rocket_g, 6, -4, rot=-30)
    rng = np.random.default_rng(1)
    stars = [sparkle(x, y, rng.uniform(4, 6.5)) for x, y in scatter(rng, 9, circ(0, 0, 80), 8, rocket_g.buffer(8))]
    return U(frame(), rocket_g, stars)

def spacetime():
    def warp(p):
        r = math.hypot(*p)
        if r < 1e-6: return p
        d = min(30 * math.exp(-r / 26), r * 0.92)
        return (p[0] - p[0] / r * d, p[1] - p[1] / r * d)
    lines = []
    for c in np.arange(-84, 85, 14):
        lines.append(stroke([warp((c, y)) for y in np.linspace(-95, 95, 160)], 2.0))
        lines.append(stroke([warp((x, c)) for x in np.linspace(-95, 95, 160)], 2.0))
    grid = inside(U(lines)).difference(circ(0, 0, 17))
    planet = U(ring(0, 0, 14, 14, W), ring(0, 0, 24, 6, 2.2, -15).difference(circ(0, 0, 14)))
    return U(frame(), grid, planet, ring(0, 0, 60, 20, 2.0, -15).difference(circ(0, 0, 15)))

def honeycomb():
    parts = []
    s = 15
    for q in range(-3, 4):
        for r in range(-3, 4):
            if abs(q + r) > 3: continue
            x = s * 1.5 * q; y = s * math.sqrt(3) * (r + q / 2)
            if math.hypot(x, y) < 70: parts.append(O(Polygon([(x + s * math.cos(math.radians(a)), y + s * math.sin(math.radians(a))) for a in range(0, 360, 60)]), 2.4))
    filled = [Polygon([(x + s * 0.7 * math.cos(math.radians(a)), y + s * 0.7 * math.sin(math.radians(a))) for a in range(0, 360, 60)]) for x, y in ((0, 0), (22.5, 13), (-22.5, -39))]
    drips = []
    for x, y in ((-22.5, -78), (22.5, -70), (45, -52)):
        drips += [stroke([(x, y + 14), (x, y)], 3.0), U(circ(x, y - 6, 5), Polygon([(x - 4.6, y - 4), (x + 4.6, y - 4), (x, y + 6)]))]
    return U(parts, filled, drips)

def snowflake():
    parts = [ring(0, 0, 10, 10, W)]
    arm = U(stroke([(10, 0), (88, 0)], 3.2), [stroke([(d, 0), (d + l * 0.7, l * 0.7)], 2.4) for d, l in ((30, 18), (50, 16), (68, 12))],
            [stroke([(d, 0), (d + l * 0.7, -l * 0.7)], 2.4) for d, l in ((30, 18), (50, 16), (68, 12))], O(star(88, 0, 7, 3, 3, rot=0), 2.0))
    parts += [A.rotate(arm, a, origin=(0, 0)) for a in range(0, 360, 60)]
    parts += [A.rotate(O(Polygon([(18, 0), (24, 6), (30, 0), (24, -6)]), 2.0), a, origin=(0, 0)) for a in range(30, 390, 60)]
    return U(parts)

def butterfly():
    upper = Polygon(bez((2, 4), (20, 60), (70, 80), (84, 50)) + bez((84, 50), (92, 30), (60, 10), (2, 4))[1:])
    lower = Polygon(bez((2, -2), (30, -10), (70, -30), (60, -60)) + bez((60, -60), (50, -80), (20, -50), (2, -2))[1:])
    veins = [stroke(bez((4, 4), (30, 20), (50, 40), (70, 56)), 2.0), stroke(bez((4, 2), (30, 10), (50, 20), (76, 32)), 2.0), stroke(bez((4, -2), (24, -20), (40, -36), (52, -58)), 2.0)]
    spots = [ring(60, 56, 6, 6, 2.0), ring(40, -46, 5, 5, 2.0)]
    half = U(O(upper, 2.8), O(lower, 2.8), [v.intersection(U(upper, lower)) for v in veins], spots)
    body = U(ell(0, 0, 5, 36), circ(0, 40, 5))
    antennae = [stroke(bez((0, 42), (8, 60), (16, 70), (24, 74)), 2.0), circ(24, 74, 3)]
    half = U(half, antennae)
    return U(half, A.scale(half, -1, 1, origin=(0, 0)), body)

def bicycle():
    wheels = []
    for cx in (-52, 52):
        wheels += [ring(cx, -20, 34, 34, 3.2), ring(cx, -20, 6, 6, 2.2)]
        wheels += [stroke([(cx + 6 * math.cos(a), -20 + 6 * math.sin(a)), (cx + 34 * math.cos(a + 0.4), -20 + 34 * math.sin(a + 0.4))], 1.6) for a in np.linspace(0, 2 * math.pi, 17)[:-1]]
    bb = (-6, -20); seat = (-22, 26); head = (36, 22)
    frame_l = [stroke(p, 3.2) for p in ([(-52, -20), bb, head, seat, (-52, -20)], [bb, seat], [head, (52, -20)], [seat, (-24, 34)], [head, (38, 34), (48, 38)])]
    saddle = stroke([(-34, 36), (-14, 36)], 4)
    pedal = U(ring(bb[0], bb[1], 6, 6, 2.2), stroke([(bb[0], bb[1]), (bb[0] + 10, bb[1] - 12)], 2.6))
    basket = O(Polygon([(44, 40), (70, 40), (66, 22), (48, 22)]), 2.2)
    return U(wheels, frame_l, saddle, pedal, basket, stroke([(48, 38), (52, 40)], 2.2))

def skyline():
    rng = np.random.default_rng(6)
    blds = []; x = -92
    while x < 92:
        w = rng.uniform(14, 26); h = rng.uniform(26, 78)
        blds.append(box(x, -60, x + w - 2, -60 + h))
        if rng.random() < 0.3: blds.append(box(x + w * 0.4, -60 + h, x + w * 0.5, -60 + h + 12))
        x += w
    city = U(blds).intersection(circ(0, 0, R - 1))
    windows = []
    for b in blds:
        x0, y0, x1, y1 = b.bounds
        for wx in np.arange(x0 + 3, x1 - 4, 6):
            for wy in np.arange(y0 + 6, y1 - 6, 8):
                if rng.random() < 0.55: windows.append(box(wx, wy, wx + 3, wy + 4))
    city = city.difference(U(windows))
    moon = circ(-40, 52, 16).difference(circ(-32, 58, 14))
    stars = [sparkle(x, y, rng.uniform(3.5, 5.5)) for x, y in scatter(rng, 8, box(-80, 30, 80, 80), 8, moon.buffer(6)) if math.hypot(x, y) < 82]
    water = [stroke(fn_curve(lambda x, o=o: o + 2 * math.sin(x / 6 + o), -90, 90), 2.2) for o in (-68, -76, -84)]
    return U(frame(), city, O(moon, 2.4), stars, inside(U(water)))

DESIGNS = {f.__name__.replace("_", "-"): f for f in (battle_station, galaxy, tree_of_life, mountains, moon_stars, coffee_love, teapot, mandala,
                                                     lotus, anchor, wave, compass, solar_system, rocket, spacetime, honeycomb, snowflake,
                                                     butterfly, bicycle, skyline)}

# ---------- one piece + hanger ----------
def connect(g, w=W):
    # Bridge every floating part to the nearest other material until the design is a single piece.
    parts = sorted(getattr(g, "geoms", [g]), key=lambda p: -p.area)
    while len(parts) > 1:
        main, rest = parts[0], parts[1:]
        i = min(range(len(rest)), key=lambda k: main.distance(rest[k]))
        a, b = nearest_points(main, rest[i])
        main = U(main, rest.pop(i), LineString([a, b]).buffer(w / 2, quad_segs=8) if a.distance(b) > 0 else None)
        parts = [main] + rest
        if main.geom_type == "MultiPolygon":   # a bridge can't make two pieces, but unions can merge others
            parts = sorted(main.geoms, key=lambda p: -p.area) + rest
    return parts[0]

def tab_centre(g):
    # A round tab straddling the topmost line work, so it is visible as a medallion and well anchored.
    minx, miny, maxx, maxy = g.bounds
    xs = [c for c in np.linspace(minx, maxx, 200) if LineString([(c, maxy - 3), (c, maxy + 1)]).intersects(g)]
    cx = min(xs, key=abs) if xs else (minx + maxx) / 2
    return cx, maxy - 9

def build_script(name):
    art = DESIGNS[name]()
    cx, cy = tab_centre(art)
    art = connect(U(art, circ(cx, cy, 12)).buffer(0))
    polys = clean(art)
    assert len(polys) == 1, f"{name}: {len(polys)} pieces"
    cmds, picks = sketch_cmds(polys, 0, "Line art")
    cmds.append({"command": "solid.extrude", "params": {"distance": T, "profiles": picks, "operation": "new", "body_name": "Art"}})
    # Keyhole on the back: head enters the round hole and slides up into the wide channel; 1.2 mm cap on top.
    for g, z0, h in ((U(circ(cx, cy - 3.5, 4.4), stroke([(cx, cy - 3.5), (cx, cy + 3.5)], 8.8)), 1.4, 1.4),
                     (U(circ(cx, cy - 3.5, 4.4), stroke([(cx, cy - 3.5), (cx, cy + 3.5)], 4.4)), 0, 1.4)):
        c, p = sketch_cmds([g], 0, "Keyhole")
        cmds += c + [{"command": "solid.extrude", "params": {"distance": h, "start_offset": z0, "profiles": p, "operation": "cut", "targets": ["Art"]}}]
    cmds.append({"command": "appearance.assign", "params": {"bodies": ["Art"], "color": COLOUR}})
    return cmds, polys[0]

def run(args): return subprocess.run(["solidcraft-cli"] + args, check=True, capture_output=True, text=True)

def build(name, out):
    cmds, poly = build_script(name)
    path = os.path.join(out, f"{name}.script.json")
    json.dump({"commands": cmds}, open(path, "w"))
    run(["run", path, "--quiet", "--out", os.path.join(out, f"{name}.stl"), "--save", os.path.join(out, f"{name}.solidcraft")])
    run(["snapshot", path, "--out", os.path.join(out, f"{name}.png"), "--width", "800", "--height", "800", "--no-grid", "--no-axes",
         "--camera", json.dumps({"target": [0, 0, 2], "yaw": 0, "pitch": 1.35, "distance": 260})])
    os.remove(path)
    minx, miny, maxx, maxy = poly.bounds
    return {"design": name, "size_mm": [round(maxx - minx, 1), round(maxy - miny, 1), T], "volume_cm3": round(poly.area * T / 1000, 1)}

if __name__ == "__main__":
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    for d in sys.argv[2:] or DESIGNS:
        print(json.dumps(build(d, out)), flush=True)
