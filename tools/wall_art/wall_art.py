# Generates layered, 3D-printable wall art plaques in a flat "vector art" style and builds them with SolidCraft.
# Each scene is drawn as 2D shapes on three relief levels above a base plate; the levels stack (a level-3 shape
# also fills levels 1 and 2 under it) so nothing overhangs. Print face up; swap filament at each level height
# (see LEVELS) or give each level body its own colour in a multi-colour slicer via the 3MF.
# Usage: python tools/wall_art/wall_art.py OUT_DIR [scene ...]
# Needs: pip install shapely numpy scipy trimesh manifold3d, and solidcraft-cli on PATH (https://github.com/tangency-labs/solidcraft).
import sys, os, json, math, subprocess, numpy as np, trimesh, manifold3d
from shapely.geometry import Point, Polygon, LineString, box
from shapely.ops import unary_union
from shapely import affinity as A

BASE = 4.5                     # back plate thickness (holds the keyhole hanger)
H = 1.2                        # height of each relief level (6 layers at 0.2 mm)
LEVELS = [BASE + H * k for k in range(4)]   # 4.5, 5.7, 6.9, 8.1: filament swap heights
RIM = 5.0
OVERLAP = 0.05
MIN_FEATURE = 0.9              # thinner slivers are removed (about two 0.4 mm extrusion widths)

# ---------- shape helpers (mm, y up, canvas centred on the origin) ----------
def U(*gs):
    flat = []
    for g in gs:
        if isinstance(g, (list, tuple)): flat += [x for x in g if x is not None]
        elif g is not None: flat.append(g)
    return unary_union(flat)

def place(g, x=0, y=0, s=1, rot=0, sx=None, sy=None):
    g = A.scale(g, s if sx is None else sx, s if sy is None else sy, origin=(0, 0))
    return A.translate(A.rotate(g, rot, origin=(0, 0)), x, y)

def circ(x, y, r): return Point(x, y).buffer(r, quad_segs=32)
def ell(x, y, rx, ry, rot=0): return place(Point(0, 0).buffer(1, quad_segs=48), x, y, sx=rx, sy=ry, rot=rot)
def ring(x, y, rx, ry, w, rot=0): return ell(x, y, rx + w / 2, ry + w / 2, rot).difference(ell(x, y, rx - w / 2, ry - w / 2, rot))
def stroke(pts, w): return LineString(pts).buffer(w / 2, quad_segs=8)
def rrect(x0, y0, x1, y1, r): return box(x0 + r, y0 + r, x1 - r, y1 - r).buffer(r, quad_segs=12)

def bez(*p, n=40):
    p = np.array(p, float); t = np.linspace(0, 1, n)[:, None]
    return list(map(tuple, (1-t)**3*p[0] + 3*(1-t)**2*t*p[1] + 3*(1-t)*t**2*p[2] + t**3*p[3]))

def fn_curve(f, x0, x1, n=120): return [(x, f(x)) for x in np.linspace(x0, x1, n)]
def below(f, x0=-110, x1=110, floor=-110): return Polygon(fn_curve(f, x0, x1) + [(x1, floor), (x0, floor)])

def star(x, y, ro, ri, n, rot=90):
    pts = [(x + (ro if i % 2 == 0 else ri) * math.cos(math.radians(rot + 180 * i / n)),
            y + (ro if i % 2 == 0 else ri) * math.sin(math.radians(rot + 180 * i / n))) for i in range(2 * n)]
    return Polygon(pts)

def sparkle(x, y, r): return star(x, y, r, r * 0.3, 4)

def heart(x, y, s, rot=0):
    t = np.linspace(0, 2 * math.pi, 120)
    pts = np.c_[16 * np.sin(t)**3, 13 * np.cos(t) - 5 * np.cos(2*t) - 2 * np.cos(3*t) - np.cos(4*t)] / 16
    return place(Polygon(pts).buffer(0), x, y, s, rot)

def leaf(length, width, bend=0.0):
    # Pointed leaf/petal from (0,0) to (0,length); bend curves it sideways.
    t = np.linspace(0, 1, 40); half = width / 2 * np.sin(math.pi * t)**0.85; yy = t * length
    xc = bend * length * np.sin(math.pi * t) * 0.15
    return Polygon(list(zip(xc + half, yy)) + list(zip(xc - half, yy))[::-1]).buffer(0)

def maple(x, y, s, rot=0):
    th = np.linspace(0, 2 * math.pi, 400)
    peaks = [(90, .62), (40, .55), (140, .55), (-15, .36), (195, .36)]
    r = 0.34 + sum(a * np.exp(-((((th - math.radians(p)) + math.pi) % (2*math.pi) - math.pi) / 0.3)**2) for p, a in peaks)
    r += 0.05 * np.abs(np.sin(9 * th))
    g = U(Polygon(np.c_[r * np.cos(th), r * np.sin(th)]).buffer(0), stroke([(0, 0), (0.05, -0.55)], 0.07))
    return place(g, x, y, s, rot)

def pine(x, y, h, w):
    tiers = [Polygon([(x - w/2 * k, y + h * (0.18 + 0.27 * i)), (x + w/2 * k, y + h * (0.18 + 0.27 * i)),
                      (x, y + h * (0.55 + 0.22 * i))]) for i, k in enumerate((1.0, 0.8, 0.6))]
    return U(tiers, box(x - w * 0.07, y, x + w * 0.07, y + h * 0.25))

def scatter(rng, n, inside, rmin=0, keep_out=None):
    pts = []
    minx, miny, maxx, maxy = inside.bounds
    while len(pts) < n:
        p = (rng.uniform(minx, maxx), rng.uniform(miny, maxy))
        if not inside.contains(Point(p)) or (keep_out is not None and keep_out.buffer(rmin).contains(Point(p))): continue
        if all(math.dist(p, q) > rmin * 2 for q in pts): pts.append(p)
    return pts

def disc_canvas(): return circ(0, 0, 90)
def portrait(): return rrect(-75, -100, 75, 100, 10)
def landscape(): return rrect(-100, -60, 100, 60, 10)

# ---------- scenes: canvas, keyhole y, level shapes (lowest first), colours (base, l1, l2, l3) ----------
def autumn():
    rng = np.random.default_rng(3)
    s1 = U(circ(38, 40, 22), below(lambda x: -22 + 10 * math.sin(x / 28 + 0.6)))
    trunk = Polygon([(-7, -62), (7, -62), (3.5, 4), (-3.5, 4)])
    branches = [stroke(bez((0, -8), (-10, 4), (-22, 14), (-34, 26)), 3.2), stroke(bez((1, -2), (10, 10), (20, 18), (30, 30)), 3.0),
                stroke(bez((0, 2), (-2, 16), (-4, 28), (-6, 42)), 2.6), stroke(bez((-2, -16), (-14, -10), (-26, -6), (-40, 2)), 2.4)]
    s2 = U(below(lambda x: -48 + 7 * math.sin(x / 20 + 1.2)), trunk, branches)
    canopy = ell(-2, 30, 50, 30)
    leaves = [maple(x, y, rng.uniform(10, 13), rng.uniform(-40, 40)) for x, y in scatter(rng, 44, canopy, 4.2)]
    falling = [maple(x, y, 8, r) for x, y, r in ((-62, -12, 30), (58, -4, -40), (44, -32, 15), (-38, -38, -25), (66, 22, 60), (-70, 22, -10))]
    return disc_canvas(), 55, [s1, s2, U(leaves, falling)], ("#f6e7cb", "#e9a03b", "#8c4a2f", "#c8441d")

def galaxy():
    rng = np.random.default_rng(7)
    def arm(off, k, rot=0.0):
        out = []
        for t in np.linspace(0, 8.0, 480):
            r = 6 * math.exp(0.3 * t); th = t + off + rot
            out.append(circ(r * math.cos(th), r * math.sin(th), max(1.6, k * (1 - t / 8.0 * 0.7))))
        return U(out)
    s1 = U(arm(0, 8), arm(math.pi, 8), circ(0, 0, 18))
    s2 = U(arm(0, 3.4, 0.22), arm(math.pi, 3.4, 0.22), circ(0, 0, 12))
    stars = [sparkle(x, y, rng.uniform(2.2, 4.8)) for x, y in scatter(rng, 22, circ(0, 0, 82), 4, s1)]
    dots = [circ(x, y, rng.uniform(0.9, 1.4)) for x, y in scatter(rng, 26, circ(0, 0, 84), 2.5, s1)]
    return disc_canvas(), 55, [s1, s2, U(circ(0, 0, 6.5), stars, dots)], ("#141b3a", "#3d3b8e", "#8e6cc8", "#f2e9c9")

def twin_suns():
    s1 = U(circ(-20, 8, 17), circ(24, 26, 11), Polygon([(-75, -12), (-62, -12), (-58, -2), (-40, -2), (-36, -12), (-20, -12), (-20, -30), (-75, -30)]))
    s2 = below(lambda x: -14 + 6 * math.sin(x / 17 + 0.4))
    dome = U(circ(-36, -46, 15).intersection(box(-60, -46, -10, -20)), box(-51, -50, -21, -46), box(-55, -52, -14, -49))
    door = rrect(-41, -50, -32, -38, 3.5)
    vapor = U(box(27, -46, 30, -6), box(22, -30, 35, -28), box(23, -20, 34, -18), box(25, -10, 32, -8), circ(28.5, -5, 2.8), box(24, -48, 33, -45))
    figure = U(circ(8, -26, 2.4), Polygon([(4.2, -46), (11.8, -46), (10, -29), (6, -29)]))
    s3 = U(below(lambda x: -48 + 9 * math.sin(x / 28 + 0.8)), dome.difference(door), vapor, figure)
    return portrait(), 70, [s1, s2, s3], ("#f3c98b", "#f08a3c", "#c7643a", "#5a2e2a")

def floral():
    petals1 = [place(leaf(52, 26), 0, 0, rot=a).difference(circ(0, 0, 20)) for a in range(0, 360, 30)]
    greens = [A.rotate(place(leaf(22, 10), 0, 56), a, origin=(0, 0)) for a in range(15, 375, 30)]
    s1 = U(petals1, greens)
    petals2 = [place(leaf(34, 20), 0, 0, rot=a + 22.5) for a in range(0, 360, 45)]
    dots = [circ(70 * math.cos(math.radians(a)), 70 * math.sin(math.radians(a)), 2.4) for a in range(0, 360, 30)]
    s2 = U(petals2, dots)
    center = U(circ(0, 0, 11).difference(ring(0, 0, 7.5, 7.5, 1.6)), [circ(17 * math.cos(math.radians(a)), 17 * math.sin(math.radians(a)), 2.2) for a in range(0, 360, 45)])
    return disc_canvas(), 55, [s1, s2, center], ("#fbf3ea", "#e8a5b4", "#c4416a", "#f2b33d")

def tea():
    s1 = U(circ(0, 30, 56), [place(leaf(24, 10, 0.6), -46, -58, rot=r) for r in (35, 80)], [place(leaf(24, 10, -0.6), 46, -58, rot=r) for r in (-35, -80)])
    body = ell(-4, 30, 34, 25)
    lid = U(ell(-4, 52, 17, 6).intersection(box(-30, 52, 30, 70)), circ(-4, 61, 4), box(-22, 50, 14, 54))
    spout = Polygon(bez((24, 18), (38, 22), (40, 38), (52, 48)) + [(54, 44)] + bez((46, 40), (40, 26), (36, 16), (26, 10))[1:]).buffer(0.5)
    handle = ring(-41, 32, 11, 13, 5)
    foot = rrect(-26, 3, 18, 8, 2)
    s2 = U(body, lid, spout, handle, foot)
    cup = circ(0, -48, 21).intersection(box(-30, -80, 30, -48))
    saucer = ell(0, -70, 34, 5)
    cup_handle = ring(22, -56, 6, 6.5, 3)
    steam = [stroke([(o + 3.5 * math.sin(y / 6), y) for y in np.linspace(-40, -14, 60)], 2.4) for o in (-9, 0, 9)]
    s3 = U(cup, saucer, cup_handle, steam)
    return portrait(), 72, [s1, s2, s3], ("#eef3e2", "#b9d8a8", "#4f7f5a", "#2f4a3a")

def coffee_love():
    rng = np.random.default_rng(11)
    def bean(x, y, rot):
        groove = stroke(bez((0, -8.5), (2.5, -3), (-2.5, 3), (0, 8.5)), 1.4)
        return place(ell(0, 0, 6.2, 9).difference(groove), x, y, rot=rot)
    beans = [bean(x, y, rng.uniform(0, 180)) for x, y in ((-62, -30), (-50, -56), (-24, -72), (60, -28), (50, -54), (26, -72), (-66, 6), (66, 4), (-56, 40), (58, 42))]
    mug = U(rrect(-30, -58, 30, 4, 8), box(-30, -4, 30, 4))
    s2 = U(mug, ring(32, -26, 11, 14, 6), ell(0, 4, 30, 3.2))
    steam = stroke(list(heart(0, 36, 26).exterior.coords), 3.2)
    return disc_canvas(), 55, [U(beans), s2, U(heart(0, -26, 17), steam)], ("#f5e6d3", "#a0522d", "#6f4e37", "#d64545")

def spacetime():
    def warp(p):
        r = math.hypot(*p)
        if r < 1e-6: return p
        d = min(30 * math.exp(-r / 26), r * 0.92)
        return (p[0] - p[0] / r * d, p[1] - p[1] / r * d)
    lines = []
    for c in np.arange(-90, 91, 12):
        lines.append(stroke([warp((c, y)) for y in np.linspace(-95, 95, 160)], 1.5))
        lines.append(stroke([warp((x, c)) for x in np.linspace(-95, 95, 160)], 1.5))
    s1 = U(lines).difference(circ(0, 0, 16))
    s2 = U(ring(0, 0, 62, 22, 1.8, -15), ring(0, 0, 40, 14, 1.8, -15), circ(0, 0, 17))
    planet = U(circ(0, 0, 12.5), ring(0, 0, 21, 5.5, 2.4, -15).difference(ell(0, 0, 12.5, 12.5).intersection(box(-30, 0, 30, 30)).difference(circ(0, 0, 12.5))))
    moon_at = (62 * math.cos(math.radians(40)), 22 * math.sin(math.radians(40)))
    moon = A.rotate(circ(*moon_at, 4.5), -15, origin=(0, 0))
    stars = [sparkle(x, y, 3.2) for x, y in ((-55, 52), (48, 60), (-68, -30), (64, -44), (-20, -70), (22, 70))]
    return disc_canvas(), 55, [s1, s2, U(planet, moon, stars)], ("#0e1726", "#2c6e8f", "#7fc8d6", "#f4d35e")

def wilderness():
    far = Polygon([(-80, -5), (-55, 22), (-40, 10), (-18, 38), (2, 16), (22, 30), (45, 6), (62, 20), (80, 4), (80, -100), (-80, -100)])
    s1 = U(circ(28, 52, 20), far)
    mid = Polygon([(-80, -20), (-48, 18), (-30, 2), (-6, 26), (20, -6), (40, 8), (80, -24), (80, -100), (-80, -100)])
    pines = [pine(x, -100, h, w) for x, h, w in ((-62, 74, 26), (-44, 58, 22), (-26, 84, 30), (-6, 54, 20), (14, 70, 26), (36, 90, 32), (58, 62, 24))]
    birds = [stroke([(x - 5, y + 2), (x - 2, y), (x, y - 1), (x + 2, y), (x + 5, y + 2)], 1.4) for x, y in ((-30, 66), (-20, 72), (-40, 74))]
    ground = box(-80, -100, 80, -86)
    return portrait(), 72, [s1, mid, U(pines, birds, ground)], ("#f6efe0", "#f2b880", "#6a8caf", "#1f3b2d")

def great_wave():
    s1 = U(circ(48, 42, 15), Polygon([(16, -18), (34, 6), (40, 8), (46, 6), (66, -18)]))
    outer = bez((-95, -40), (-70, -10), (-55, 30), (-25, 50)) + bez((-25, 50), (0, 62), (24, 50), (30, 30))[1:]
    curl = bez((30, 30), (22, 36), (10, 38), (2, 30)) + bez((2, 30), (-6, 22), (0, 12), (10, 14))[1:]
    back = bez((10, 14), (0, -2), (30, -20), (95, -30))
    wave = Polygon(outer + curl[1:] + back[1:] + [(95, -95), (-95, -95)]).buffer(0)
    s2 = U(wave, below(lambda x: -48 + 6 * math.sin(x / 9), floor=-95))
    claws = [place(leaf(9, 4.2, 1.2), x, y, rot=r) for (x, y), r in zip(outer[22:74:4], np.linspace(-20, -110, 13))]
    crest = U(stroke(outer[8:75], 2.2), stroke(curl, 2.2), claws)
    lines = [stroke(bez((-80, -40 + d), (-60, -12 + d), (-48, 20 + d * 0.5), (-28, 38 + d * 0.3)), 1.6) for d in (-10, -20)]
    small = [stroke(fn_curve(lambda x, o=o: o + 3 * math.sin(x / 7), -85, 85), 1.8) for o in (-62, -74)]
    return disc_canvas(), 55, [s1, s2, U(crest, lines, small)], ("#f1e6cf", "#e07a5f", "#1d3557", "#f8f4ea")

def lunar_phases():
    rng = np.random.default_rng(5)
    r = 11; xs = [-78 + 26 * i for i in range(7)]; y = -26
    def lit(x, f):   # f in (0,1): fraction through the cycle; 0.5 = full
        right = f < 0.5
        k = abs(math.cos(2 * math.pi * f))        # terminator ellipse half-width / r
        half = circ(x, y, r).intersection(box(x, y - r, x + r, y + r) if right else box(x - r, y - r, x, y + r))
        term = ell(x, y, max(k * r, 0.01), r)
        crescent = (f < 0.25 or f > 0.75)
        return half.difference(term) if crescent else U(half, term)
    phases = [0.12, 0.25, 0.37, 0.5, 0.63, 0.75, 0.88]
    s1 = U([circ(x, y, r) for x in xs], circ(0, 22, 24))
    big = circ(0, 22, 22).difference(U(circ(-7, 28, 4), circ(6, 14, 3), circ(9, 30, 2.5), circ(-6, 12, 2)))
    stars = [sparkle(x, yy, rng.uniform(2.2, 3.6)) for x, yy in scatter(rng, 12, box(-92, 6, 92, 54), 5, circ(0, 22, 30))]
    s2 = U(stars, stroke([(-86, -44), (86, -44)], 1.6))
    s3 = U([lit(x, f) for x, f in zip(xs, phases)], big)
    return landscape(), 35, [s1, s2, s3], ("#0b1020", "#2b3550", "#9fb3d9", "#f3efe0")

SCENES = {f.__name__.replace("_", "-"): f for f in (autumn, galaxy, twin_suns, floral, tea, coffee_love, spacetime, wilderness, great_wave, lunar_phases)}

# ---------- solidcraft script ----------
def clean(g):
    g = g.buffer(-MIN_FEATURE / 2, quad_segs=8).buffer(MIN_FEATURE / 2, quad_segs=8)
    polys = [p for p in getattr(g, "geoms", [g]) if p.geom_type == "Polygon" and p.area > 2.0]
    return [p.simplify(0.03) for p in polys]

def ring_pts(coords):
    pts = []
    for x, y in list(coords)[:-1]:
        q = [round(x, 3), round(y, 3)]
        if not pts or q != pts[-1]: pts.append(q)
    if len(pts) > 1 and pts[0] == pts[-1]: pts.pop()
    return pts

def sketch_cmds(polys, z, name):
    cmds = [{"command": "sketch.create", "params": {"plane": "XY", "offset": z, "name": name}}]
    for p in polys:
        for r in [p.exterior] + list(p.interiors):
            cmds.append({"command": "sketch.line", "params": {"points": ring_pts(r.coords), "closed": True, "infer": False, "connect": False}})
    cmds.append({"command": "sketch.finish", "params": {}})
    picks = [{"point": [round(c, 3) for c in p.representative_point().coords[0]]} for p in polys]
    return cmds, picks

def build_script(scene):
    canvas, keyhole_y, shapes, _ = SCENES[scene]()
    cmds, picks = sketch_cmds(clean(canvas), 0, "Plate")
    cmds += picks and [{"command": "solid.extrude", "params": {"distance": BASE, "profiles": picks, "operation": "new", "body_name": "Base"}}]
    # Keyhole hanger on the back: a nail head enters the round hole, slides up into the wide channel.
    for polys, z0, h in ((U(circ(0, keyhole_y, 4.6), stroke([(0, keyhole_y), (0, keyhole_y + 10)], 9.2)), 1.6, 1.6),
                         (U(circ(0, keyhole_y, 4.6), stroke([(0, keyhole_y), (0, keyhole_y + 10)], 4.6)), 0, 1.6)):
        c, p = sketch_cmds([polys], 0, "Keyhole")
        cmds += c + [{"command": "solid.extrude", "params": {"distance": h, "start_offset": z0, "profiles": p, "operation": "cut", "targets": ["Base"]}}]
    rim = canvas.difference(canvas.buffer(-RIM))
    shapes = shapes[:2] + [U(shapes[2], rim)]
    support = canvas
    for k in range(3):
        # Each level only where the level below has material, so nothing overhangs.
        polys = clean(U(shapes[k:]).intersection(support))
        if not polys: continue
        support = U(polys)
        # Sink each level OVERLAP into the one below so the bodies can be joined into one solid.
        c, p = sketch_cmds(polys, LEVELS[k] - OVERLAP, f"Level {k + 1}")
        cmds += c + [{"command": "solid.extrude", "params": {"distance": H + OVERLAP, "profiles": p, "operation": "new", "body_name": f"Level{k + 1}"}}]
    return cmds

def run(args, **kw): return subprocess.run(["solidcraft-cli"] + args, check=True, capture_output=True, text=True, **kw)

def build(scene, out):
    colours = SCENES[scene]()[3]
    cmds = build_script(scene)
    base_path = os.path.join(out, f"{scene}.script.json")
    json.dump({"commands": cmds}, open(base_path, "w"))
    bodies = json.loads(run(["eval", base_path]).stdout)["bodies"]
    names = {k: [b["name"] for b in bodies if b["name"].startswith(k)] for k in ("Base", "Level1", "Level2", "Level3")}
    colour_cmds = [{"command": "appearance.assign", "params": {"bodies": names[k], "color": c}}
                   for k, c in zip(("Base", "Level1", "Level2", "Level3"), colours) if names[k]]
    json.dump({"commands": cmds + colour_cmds}, open(base_path, "w"))
    parts = os.path.join(out, f"{scene}.parts.stl")
    run(["run", base_path, "--out", os.path.join(out, f"{scene}.3mf"), "--out", os.path.join(out, f"{scene}.step"),
         "--out", parts, "--save", os.path.join(out, f"{scene}.solidcraft"), "--quiet"])
    # One watertight solid for single-body slicing: union the level bodies as meshes (the kernel refuses to join
    # bodies whose side walls coincide, as the rim's do on every level).
    mesh = trimesh.load(parts)
    solid = manifold3d.Manifold()
    for piece in mesh.split(only_watertight=False):
        solid += manifold3d.Manifold(manifold3d.Mesh(vert_properties=np.asarray(piece.vertices, np.float32), tri_verts=np.asarray(piece.faces, np.uint32)))
    m = solid.to_mesh()
    trimesh.Trimesh(m.vert_properties[:, :3], m.tri_verts).export(os.path.join(out, f"{scene}.stl"))
    os.remove(parts)
    w, h = 1000, 1000
    cam = {"target": [0, 0, 4], "yaw": 0, "pitch": 1.2, "distance": 270}
    run(["snapshot", base_path, "--out", os.path.join(out, f"{scene}.png"), "--width", str(w), "--height", str(h),
         "--camera", json.dumps(cam), "--no-grid", "--no-axes"])
    total = sum(b["volume_mm3"] for b in bodies)
    bb = [min(b["bbox"]["min"][i] for b in bodies) for i in range(3)], [max(b["bbox"]["max"][i] for b in bodies) for i in range(3)]
    return {"scene": scene, "bodies": len(bodies), "volume_cm3": round(total / 1000, 1),
            "size_mm": [round(bb[1][i] - bb[0][i], 1) for i in range(3)], "colours": colours}

if __name__ == "__main__":
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    for s in sys.argv[2:] or SCENES:
        print(json.dumps(build(s, out)), flush=True)
