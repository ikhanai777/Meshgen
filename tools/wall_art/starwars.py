# Star Wars fan-art line cutouts (personal use): same one-piece, keyhole-tab construction as cutouts.py.
# Usage: python tools/wall_art/starwars.py OUT_DIR [design ...]
import sys, os, json, math, numpy as np
from shapely.geometry import Polygon, box
from shapely import affinity as A
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wall_art import U, place, circ, ell, ring, stroke, rrect, bez, fn_curve, star, sparkle, scatter
from cutouts import O, W, R, frame, inside, build

def stars_around(rng, n, avoid, r=80):
    return [sparkle(x, y, rng.uniform(3.5, 6)) for x, y in scatter(rng, n, circ(0, 0, r), 7, avoid.buffer(8))]

def x_wing_icon():
    # Front view, S-foils open in attack position.
    parts = []
    for ang in (14, 166, 194, 346):
        a = math.radians(ang); d = (math.cos(a), math.sin(a)); n = (-d[1], d[0])
        wing = Polygon([(d[0] * 8 + n[0] * 6, d[1] * 8 + n[1] * 6), (d[0] * 74 + n[0] * 3.5, d[1] * 74 + n[1] * 3.5),
                        (d[0] * 74 - n[0] * 3.5, d[1] * 74 - n[1] * 3.5), (d[0] * 8 - n[0] * 6, d[1] * 8 - n[1] * 6)])
        parts += [O(wing), ring(d[0] * 26, d[1] * 26, 8, 8, W), circ(d[0] * 26, d[1] * 26, 3), ring(d[0] * 78, d[1] * 78, 4, 4, 2.2)]
    body = U(ring(0, 0, 13, 13, 3.0), O(rrect(-6, -24, 6, -11, 2)), O(rrect(-8, 9, 8, 22, 3)))
    return U(parts, body)

def x_wing():
    g = x_wing_icon()
    return U(frame(), g, stars_around(np.random.default_rng(1), 10, g))

def tie_fighter():
    parts = []
    for sx in (-1, 1):
        cx = 66 * sx
        outer = Polygon([(cx, 82), (cx + 14 * sx, 44), (cx + 14 * sx, -44), (cx, -82), (cx - 14 * sx, -44), (cx - 14 * sx, 44)])
        inner = Polygon([(cx, 14), (cx + 5 * sx, 6), (cx + 5 * sx, -6), (cx, -14), (cx - 5 * sx, -6), (cx - 5 * sx, 6)])
        parts += [O(outer, 3.2), O(inner, 2.4)]
        parts += [stroke([pi, po], 2.2) for pi, po in zip(inner.exterior.coords[:-1], outer.exterior.coords[:-1])]
        parts += [stroke([(cx, 14), (cx, 82)], 2.2), stroke([(cx, -14), (cx, -82)], 2.2)]
        parts.append(U(box(min(18 * sx, cx - 3 * sx), -4, max(18 * sx, cx - 3 * sx), 4)))
    ball = U(ring(0, 0, 22, 22, 3.2), ring(0, 0, 12, 12, 2.4), [stroke([(12 * math.cos(a), 12 * math.sin(a)), (22 * math.cos(a), 22 * math.sin(a))], 2.0) for a in np.linspace(0, 2 * math.pi, 9)[:-1]])
    return U(parts, ball, circ(0, 0, 4))

def falcon_icon():
    disc = circ(0, -8, 60)
    mandibles = U(box(-24, 20, -5, 74), box(5, 20, 24, 74))
    cockpit_arm = Polygon([(40, 30), (66, 52), (74, 44), (52, 20)])
    hull = U(disc, mandibles, cockpit_arm, circ(70, 48, 9))
    details = [ring(0, -8, 18, 18, 2.4), ring(0, -8, 40, 40, 2.0).difference(box(-26, 0, 26, 60)), circ(0, -8, 5),
               stroke([(-14, 74), (-14, 40)], 2.0), stroke([(14, 74), (14, 40)], 2.0), ring(70, 48, 4, 4, 2.0),
               O(rrect(-40, -66, 40, -56, 4), 2.0)]
    details += [stroke([(18 * math.cos(a), -8 + 18 * math.sin(a)), (40 * math.cos(a), -8 + 40 * math.sin(a))], 2.0) for a in np.radians((20, 160, 200, 240, 300, 340))]
    return U(O(hull, 3.2), [d.intersection(hull) for d in details])

def millennium_falcon():
    g = falcon_icon()
    return U(frame(), g, stars_around(np.random.default_rng(3), 8, g))

def destroyer_icon():
    hull = Polygon([(-82, 6), (56, 40), (66, 30), (66, -24), (56, -32)])
    tower = U(rrect(30, 34, 58, 44, 2), rrect(38, 44, 52, 52, 2), box(44, 52, 46, 58), circ(40, 56, 2.6), circ(50, 56, 2.6))
    lines = [stroke([(-82, 6), (66, 4)], 2.2), stroke([(-40, 15), (56, 32)], 2.0), stroke([(-40, -3), (56, -22)], 2.0)]
    engines = [ring(70, y, 4, 4, 2.0) for y in (16, 2, -12)]
    return U(O(U(hull, tower), 3.0), [l.intersection(hull) for l in lines], engines)

def star_destroyer():
    g = destroyer_icon()
    planet = ring(-30, -150, 110, 110, 3.0)
    return U(frame(), inside(U(g, planet)), stars_around(np.random.default_rng(4), 8, U(g, circ(-30, -150, 112))))

def walker_icon():
    body = rrect(-42, 14, 34, 42, 8)
    head = U(rrect(48, 16, 72, 32, 4), box(72, 18, 78, 22))
    neck = stroke([(34, 28), (48, 25)], 8)
    legs = []
    for hip_x, knee_dx in ((-34, -6), (-20, 6), (16, -6), (28, 6)):
        knee = (hip_x + knee_dx, -12); foot = (hip_x, -46)
        legs += [stroke([(hip_x, 16), knee], 6), stroke([knee, foot], 5), ring(*knee, 5, 5, 2.2), rrect(foot[0] - 8, -52, foot[0] + 8, -46, 2)]
    details = [stroke([(-42, 30), (34, 30)], 2.0).intersection(body), circ(62, 25, 2.6)]
    return U(O(U(body, head, neck), 3.0), legs, details)

def at_at():
    walker = U(walker_icon(), box(-62, -54, 62, -51))
    peaks = stroke([(-92, -10), (-70, 22), (-56, 6), (-40, 50), (-30, 46)], 2.4)
    snow = [stroke(fn_curve(lambda x, o=o: o + 2 * math.sin(x / 9 + o), -90, 90), 2.2) for o in (-64, -74)]
    return U(frame(), inside(U(walker, peaks, snow)))

def lightsaber_duel():
    parts = []
    for ang, flip in ((52, 1), (128, -1)):
        a = math.radians(ang); d = (math.cos(a), math.sin(a))
        base = (-d[0] * 66, -d[1] * 66)
        blade = Polygon([(base[0] + d[0] * 22 - d[1] * 4, base[1] + d[1] * 22 + d[0] * 4), (base[0] + d[0] * 136 - d[1] * 4, base[1] + d[1] * 136 + d[0] * 4),
                         (base[0] + d[0] * 140, base[1] + d[1] * 140), (base[0] + d[0] * 136 + d[1] * 4, base[1] + d[1] * 136 - d[0] * 4),
                         (base[0] + d[0] * 22 + d[1] * 4, base[1] + d[1] * 22 - d[0] * 4)])
        hilt = place(rrect(-5.5, 0, 5.5, 24, 2), base[0], base[1], rot=ang - 90)
        grips = [place(box(-5.5, y, 5.5, y + 1.6), base[0], base[1], rot=ang - 90) for y in (5, 9, 13)]
        parts += [O(blade, 2.6), stroke([(base[0] + d[0] * 30, base[1] + d[1] * 30), (base[0] + d[0] * 128, base[1] + d[1] * 128)], 1.6),
                  hilt.difference(U(grips)), place(box(-8, 22, 8, 25), base[0], base[1], rot=ang - 90)]
    clash = star(0, 8, 16, 6, 8)
    g = U(parts, O(clash, 2.2))
    return U(frame(), inside(g), stars_around(np.random.default_rng(6), 8, g))

def astromech():
    dome = circ(0, 30, 34).intersection(box(-40, 30, 40, 70))
    body = rrect(-34, -44, 34, 30, 3)
    dome_details = [stroke([(-33, 36), (33, 36)], 2.0), ring(-6, 50, 7, 7, 2.4), circ(-6, 50, 3), O(rrect(8, 42, 20, 52, 2), 2.0), O(rrect(-26, 40, -16, 46, 1), 2.0)]
    body_details = [O(rrect(-22, 8, -4, 22, 2), 2.0), O(rrect(4, 4, 22, 22, 2), 2.0), stroke([(-22, -6), (22, -6)], 2.0), stroke([(-22, -14), (22, -14)], 2.0),
                    O(rrect(-10, -38, 10, -22, 2), 2.0), circ(-16, -30, 3), circ(16, -30, 3)]
    legs = [O(Polygon([(sx * 34, 24), (sx * 46, 24), (sx * 46, -56), (sx * 34, -48)]), 2.8) for sx in (-1, 1)]
    feet = [O(Polygon([(sx * 30, -56), (sx * 52, -56), (sx * 48, -66), (sx * 30, -66)]), 2.8) for sx in (-1, 1)]
    centre = O(Polygon([(-10, -44), (10, -44), (14, -64), (-14, -64)]), 2.6)
    return U(O(U(dome, body), 3.0), [d.intersection(dome) for d in dome_details], body_details, legs, feet, centre, stroke([(-16, 30), (16, 30)], 2.4))

def twin_suns():
    suns = [ring(-22, 22, 16, 16, 2.6), ring(10, 36, 10, 10, 2.6)]
    rays = [stroke([(-22 + 20 * math.cos(a), 22 + 20 * math.sin(a)), (-22 + 25 * math.cos(a), 22 + 25 * math.sin(a))], 2.0) for a in np.linspace(0, 2 * math.pi, 13)[:-1]]
    dunes = [stroke(fn_curve(lambda x, o=o, p=p: o + 6 * math.sin(x / 18 + p), -92, 92), 2.6) for o, p in ((-4, 0.4), (-26, 2.0))]
    hut = U(O(circ(-40, -54, 16).intersection(box(-60, -54, -20, -30)), 2.6), O(rrect(-45, -54, -35, -40, 4), 2.2))
    figure = U(circ(18, -38, 3), Polygon([(13, -62), (23, -62), (21, -42), (15, -42)]))
    vapor = U(stroke([(48, -62), (48, -24)], 3), stroke([(42, -40), (54, -40)], 2.4), stroke([(43, -32), (53, -32)], 2.4), circ(48, -22, 3))
    ground = stroke(fn_curve(lambda x: -62 + 3 * math.sin(x / 20), -92, 92), 2.6)
    return U(frame(), inside(U(suns, rays, dunes, hut, figure, vapor, ground)))

def t_visor_helmet():
    shell = U(circ(0, 18, 46).intersection(box(-50, 0, 50, 70)), Polygon([(-46, 18), (46, 18), (42, -40), (24, -58), (-24, -58), (-42, -40)]))
    visor = U(rrect(-34, 14, 34, 26, 4), rrect(-7, -40, 7, 22, 3))
    grooves = U(stroke([(-46, 34), (46, 34)], 2.0), stroke([(-24, -48), (-14, -30)], 2.0), stroke([(24, -48), (14, -30)], 2.0))
    helmet = shell.difference(U(visor.buffer(2.6), grooves))
    return U(frame(), helmet, O(visor, 2.2), ring(0, 0, 70, 70, 2.2).difference(shell.buffer(6)))

def trench_run():
    vx, vy = 0, 12
    lines = []
    for x0 in (-92, -50, 50, 92):
        lines.append(stroke([(x0, -92), (vx + x0 * 0.08, vy)], 2.4))
    for t in (0.25, 0.45, 0.6, 0.72, 0.81, 0.88):
        y = -92 + (vy + 92) * t; xl = -50 + 50 * t; xr = 50 - 50 * t
        lines += [stroke([(xl, y), (xr, y)], 2.0), stroke([(-92 + 92 * t * 0.92, y), (xl, y)], 1.8), stroke([(xr, y), (92 - 92 * t * 0.92, y)], 1.8)]
    lines.append(stroke([(-92, vy), (92, vy)], 2.4))
    station = ring(0, 120, 100, 100, 2.6)
    xw = []
    for ang in (20, 160, 200, 340):
        a = math.radians(ang); xw.append(stroke([(0, -36), (16 * math.cos(a), -36 + 16 * math.sin(a))], 2.6)); xw.append(circ(16 * math.cos(a), -36 + 16 * math.sin(a), 2.2))
    xw.append(ring(0, -36, 4, 4, 2.2))
    lasers = [stroke([(-6, -30), (-2, -8)], 1.8), stroke([(6, -30), (2, -8)], 1.8)]
    return U(frame(), inside(U(lines, station, xw, lasers)))

DESIGNS = {f.__name__.replace("_", "-"): f for f in (x_wing, tie_fighter, millennium_falcon, star_destroyer, at_at, lightsaber_duel,
                                                     astromech, twin_suns, t_visor_helmet, trench_run)}

if __name__ == "__main__":
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    for d in sys.argv[2:] or DESIGNS:
        print(json.dumps(build(d, out, DESIGNS)), flush=True)
