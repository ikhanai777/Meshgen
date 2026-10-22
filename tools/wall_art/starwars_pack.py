# Star Wars fan-art pack (personal use): 50 sturdy one-piece line cutouts with a keyhole tab.
# 3 mm thick; sturdier line work than cutouts.py: every line widened by 0.8 mm, wider bridges, a bigger keyhole
# tab, and ships drawn small inside a scene are filled silhouettes rather than thin outlines.
# Usage: python tools/wall_art/starwars_pack.py OUT_DIR [design ...]
import sys, os, json, math, numpy as np
from shapely.geometry import Polygon, box
from shapely import affinity as A
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wall_art import U, place, circ, ell, ring, stroke, rrect, bez, fn_curve, star, sparkle, scatter
import cutouts
from cutouts import O, W, R, frame, inside, build
from starwars import x_wing_icon, tie_fighter, falcon_icon, destroyer_icon, walker_icon, astromech, t_visor_helmet

cutouts.T, cutouts.THICKEN, cutouts.KEY_DEPTH, cutouts.TAB_R, cutouts.BRIDGE = 3.0, 0.4, 1.0, 13, 3.4

P = place
def sil(g):  # filled silhouette: sturdy at small scale
    return U([Polygon(p.exterior) for p in getattr(g, "geoms", [g])])
def hexframe(r=92, w=3.4): return O(Polygon([(r * math.cos(math.radians(a)), r * math.sin(math.radians(a))) for a in range(30, 390, 60)]), w)
def rectframe(w=76, h=94, t=3.4): return O(rrect(-w, -h, w, h, 10), t)
def stars(seed, n, avoid, area=None):
    rng = np.random.default_rng(seed)
    return [sparkle(x, y, rng.uniform(4, 6.5)) for x, y in scatter(rng, n, area or circ(0, 0, 80), 8, avoid.buffer(8))]
def mirror(g): return U(g, A.scale(g, -1, 1, origin=(0, 0)))
def framed(g, seed=0, n=8, fr=None):
    g = inside(g) if fr is None else g
    return U(fr if fr is not None else frame(), g, stars(seed, n, g) if n else None)

# ---------- icons (unframed, roughly within +-70 mm) ----------
def xwing_top():
    hull = Polygon([(0, 66), (5, 40), (6, -30), (9, -42), (-9, -42), (-6, -30), (-5, 40)])
    wing = Polygon([(6, -12), (58, -20), (58, -32), (6, -30)])
    eng = rrect(9, -48, 19, -4, 4)
    cannon = U(stroke([(58, -32), (58, 30)], 3.0), circ(58, 32, 2.6))
    half = U(O(U(wing, eng), 2.8), cannon)
    return U(mirror(half), O(hull, 2.8), O(ell(0, 10, 4, 9), 2.2))

def tie_interceptor():
    wing = Polygon([(54, 0), (44, 84), (58, 40), (72, 84), (62, 0), (72, -84), (58, -40), (44, -84)])
    side = U(O(wing, 3.0), stroke([(58, -40), (58, 40)], 2.4), box(18, -4, 55, 4))
    ball = U(ring(0, 0, 20, 20, 3.2), ring(0, 0, 10, 10, 2.4), [stroke([(10 * math.cos(a), 10 * math.sin(a)), (20 * math.cos(a), 20 * math.sin(a))], 2.0) for a in np.linspace(0, 2 * math.pi, 9)[:-1]])
    return U(mirror(side), ball)

def tie_advanced():
    wing = Polygon([(26, 0), (38, 44), (72, 58), (56, 0), (72, -58), (38, -44)])
    ribs = [stroke([(32, 0), p], 2.0) for p in ((38, 44), (72, 58), (56, 0), (72, -58), (38, -44))]
    side = U(O(wing, 3.0), ribs, box(16, -4, 30, 4))
    return U(mirror(side), ring(0, 0, 18, 18, 3.2), ring(0, 0, 8, 8, 2.4), O(rrect(-12, -26, 12, -16, 3), 2.4))

def at_st():
    head = rrect(-28, 20, 28, 46, 7)
    slit = rrect(-18, 34, 18, 38, 2)
    legs = []
    for sx in (-1, 1):
        hip, knee, ankle = (sx * 12, 22), (sx * 26, -6), (sx * 12, -42)
        legs += [stroke([hip, knee], 5), stroke([knee, ankle], 5), ring(*knee, 4.5, 4.5, 2.2), rrect(sx * 12 - 10, -50, sx * 12 + 10, -42, 3)]
    guns = [stroke([(-26, 26), (-40, 26)], 3), stroke([(26, 26), (40, 26)], 3)]
    return U(O(head, 3.0), O(slit, 2.0), legs, guns, stroke([(-12, 20), (12, 20)], 4))

def y_wing():
    head = ell(0, 42, 11, 18)
    spine = rrect(-6, -24, 6, 30, 3)
    bar = rrect(-40, 0, 40, 10, 3)
    nac = [U(rrect(sx * 28 - 7, -64, sx * 28 + 7, 14, 6), circ(sx * 28, 14, 7)) for sx in (-1, 1)]
    g = U(head, spine, bar, nac)
    details = [stroke([(sx * 28, -50), (sx * 28, 6)], 2.0) for sx in (-1, 1)] + [O(ell(0, 46, 5, 8), 2.0)]
    return U(O(g, 3.0), [d.intersection(g) for d in details])

def a_wing():
    hull = Polygon([(0, 62), (48, -36), (-48, -36)])
    eng = [rrect(sx * 30 - 7, -54, sx * 30 + 7, -30, 3) for sx in (-1, 1)]
    g = U(hull, eng)
    return U(O(g, 3.0), O(ell(0, 12, 6, 12), 2.2), stroke([(0, 50), (0, 26)], 2.0), [stroke([(sx * 48, -36), (sx * 52, -10)], 3) for sx in (-1, 1)])

def b_wing():
    main = rrect(-7, -72, 7, 56, 5)
    cross = rrect(-46, -5, 46, 5, 4)
    g = U(main, cross, circ(0, 64, 11), circ(0, 0, 12))
    return U(O(g, 3.0), ring(0, 64, 5, 5, 2.2), ring(0, 0, 6, 6, 2.2), [circ(sx * 46, 0, 6) for sx in (-1, 1)], circ(0, -72, 5))

def snowspeeder():
    body = Polygon([(-8, 54), (8, 54), (30, 24), (34, -30), (-34, -30), (-30, 24)])
    pods = [rrect(sx * 36 - 7, -40, sx * 36 + 7, 12, 5) for sx in (-1, 1)]
    g = U(body, pods)
    return U(O(g, 3.0), O(ell(0, 4, 10, 12), 2.2), stroke([(0, 54), (0, 22)], 2.2), stroke([(0, -30), (0, -52)], 3), circ(0, -54, 3))

def firespray():
    hull = U(ell(0, 6, 36, 50), Polygon([(-40, -30), (40, -30), (52, -56), (-52, -56)]))
    g = U(hull, circ(0, 56, 10))
    return U(O(g, 3.0), ring(0, 56, 5, 5, 2.2), ring(0, 6, 18, 24, 2.2), stroke([(-46, -46), (46, -46)], 2.2).intersection(hull), [circ(sx * 22, -40, 4) for sx in (-1, 1)])

def speeder_bike():
    body = U(rrect(-34, -8, 24, 10, 6), Polygon([(-34, -2), (-56, 10), (-56, 16), (-30, 8)]))
    stalks = [stroke([(20, 6), (64, 9)], 3), stroke([(20, 0), (64, 0)], 3)]
    vanes = rrect(58, -8, 72, 18, 3)
    seat = rrect(-18, 10, 4, 16, 3)
    bars = stroke([(8, 10), (16, 22), (24, 22)], 3)
    return U(O(U(body, vanes), 3.0), stalks, seat, bars, O(rrect(-26, -20, 10, -12, 3), 2.4))

def lambda_shuttle():
    fin = Polygon([(-7, -2), (7, -2), (5, 72), (-5, 72)])
    wings = [Polygon([(sx * 4, 0), (sx * 64, -54), (sx * 56, -62), (sx * 4, -14)]) for sx in (-1, 1)]
    g = U(fin, wings, circ(0, -12, 14))
    return U(O(g, 3.0), ring(0, -12, 7, 7, 2.2), [stroke([(sx * 10, -10), (sx * 54, -50)], 2.0) for sx in (-1, 1)], stroke([(0, 8), (0, 64)], 2.0))

def death_star(r=70, complete=True, seed=4):
    rng = np.random.default_rng(seed)
    lines = [ring(0, 0, r, r, 3.2) if complete else ring(0, 0, r, r, 3.2).difference(Polygon([(0, 0), (r * 2, r * 1.4), (r * 2, -r * 1.4)]))]
    lines += [stroke([(-math.sqrt(r * r - y * y), y), (math.sqrt(r * r - y * y), y)], 2.4) for y in np.linspace(-r * 0.8, r * 0.8, 7)]
    for s in np.linspace(-0.8, 0.8, 7):
        lines.append(stroke([(s * math.sqrt(max(r * r - y * y, 0)), y) for y in np.linspace(-r, r, 40)], 2.0))
    dish = U(ring(-r * 0.4, r * 0.4, r * 0.24, r * 0.24, 2.6), circ(-r * 0.4, r * 0.4, r * 0.07))
    g = U(lines).intersection(circ(0, 0, r + 2)).difference(circ(-r * 0.4, r * 0.4, r * 0.25))
    if not complete:
        g = g.difference(Polygon([(r * 0.15, -r * 0.2), (r * 1.2, -r * 1.1), (r * 1.2, r * 0.9), (r * 0.5, r * 0.1)]).difference(U([stroke([(r * 0.15, y), (r, y)], 2.0) for y in np.linspace(-r * 0.6, r * 0.6, 5)])))
    return U(g, dish)

def protocol_droid():
    head = U(ring(0, 52, 11, 12, 2.8), circ(-4, 54, 2.6), circ(4, 54, 2.6), stroke([(-4, 46), (4, 46)], 2))
    torso = O(Polygon([(-20, 36), (20, 36), (14, 4), (-14, 4)]), 2.8)
    waist = [stroke([(x, 4), (x * 0.8, -6)], 2) for x in (-8, 0, 8)]
    hips = O(rrect(-14, -12, 14, -4, 3), 2.6)
    arms = [stroke([(sx * 20, 34), (sx * 30, 10), (sx * 28, -12)], 3.4) for sx in (-1, 1)]
    legs = [stroke([(sx * 8, -12), (sx * 10, -40), (sx * 10, -66)], 4) for sx in (-1, 1)]
    feet = [rrect(sx * 10 - 7, -72, sx * 10 + 8, -66, 2) for sx in (-1, 1)]
    return U(head, stroke([(0, 40), (0, 36)], 4), torso, ring(0, 22, 5, 5, 2.2), waist, hips, arms, legs, feet)

def bb_unit():
    body = U(ring(0, -22, 40, 40, 3.2), ring(0, -22, 14, 14, 2.4), ring(-26, -40, 8, 8, 2.2), ring(26, -40, 8, 8, 2.2), ring(-28, 0, 8, 8, 2.2), ring(28, 0, 8, 8, 2.2))
    head = U(O(ell(0, 24, 22, 14).intersection(box(-30, 18, 30, 50)).union(box(-22, 18, 22, 22)), 2.8), ring(0, 30, 5, 5, 2.4), circ(12, 26, 2.6))
    ant = [stroke([(-6, 38), (-6, 56)], 2.2), stroke([(4, 38), (4, 48)], 2.2)]
    return U(body, head, ant, stroke([(0, 18), (0, 16)], 4))

def probe_droid():
    head = U(ring(0, 34, 22, 20, 3.0), [ring(x, y, 4, 4, 2.2) for x, y in ((-10, 36), (0, 40), (10, 36))])
    body = O(rrect(-14, 0, 14, 14, 4), 2.6)
    legs = [stroke(bez((x, 0), (x * 1.8, -20), (x * 2.6, -40), (x * 2.2, -66)), 2.8) for x in (-10, -5, 0, 5, 10)]
    ant = [stroke([(-14, 52), (-24, 70)], 2.2), stroke([(14, 52), (24, 70)], 2.2), stroke([(0, 54), (0, 72)], 2.2)]
    return U(head, body, legs, ant, stroke([(0, 14), (0, 14)], 3))

def stormtrooper_helmet():
    shell = U(circ(0, 12, 44).intersection(box(-50, 4, 50, 60)), Polygon([(-44, 12), (44, 12), (40, -26), (22, -52), (-22, -52), (-40, -26)]))
    eyes = [Polygon([(sx * 6, 4), (sx * 30, 10), (sx * 34, -4), (sx * 14, -10)]) for sx in (-1, 1)]
    slits = [box(x - 0.9, -40, x + 0.9, -26) for x in (-8, -3, 2, 7)]
    brow = stroke(bez((-40, 22), (-14, 30), (14, 30), (40, 22)), 2.4)
    return shell.difference(U(eyes, brow, slits, stroke([(-22, -18), (22, -18)], 2)))

def vader_helmet():
    dome = circ(0, 14, 40).intersection(box(-44, 14, 44, 60))
    flare = Polygon([(-40, 14), (40, 14), (58, -50), (30, -44), (16, -58), (-16, -58), (-30, -44), (-58, -50)])
    shell = U(dome, flare)
    eyes = [Polygon([(sx * 4, 6), (sx * 28, 8), (sx * 30, -6), (sx * 10, -6)]) for sx in (-1, 1)]
    mouth = Polygon([(0, -14), (16, -40), (-16, -40)])
    slits = [stroke([(x, -36), (x * 0.4, -20)], 1.6) for x in (-10, -5, 0, 5, 10)]
    return shell.difference(U(eyes, mouth.difference(U(slits)), stroke([(-38, 20), (38, 20)], 2.2)))

def pilot_helmet():
    shell = U(circ(0, 10, 46).intersection(box(-50, -10, 50, 60)), rrect(-40, -46, 40, -4, 12))
    visor = rrect(-30, -10, 30, 14, 8)
    return U(shell.difference(U(visor.buffer(2.6), stroke([(-46, 26), (46, 26)], 2.2), stroke([(-20, -34), (20, -34)], 2.2))), O(visor, 2.4))

def saber(length=120, w=4.5):
    hilt = rrect(-6, 0, 6, 30, 2).difference(U([box(-6, y, 6, y + 2) for y in (6, 11, 16)]))
    guard = rrect(-9, 28, 9, 32, 1.5)
    blade = Polygon([(-w, 32), (w, 32), (w, 32 + length), (0, 36 + length), (-w, 32 + length)])
    return U(hilt, guard, O(blade, 2.6), stroke([(0, 40), (0, 26 + length)], 1.8), rrect(-4, -4, 4, 0, 1))

def blaster():
    g = U(rrect(-50, 6, 34, 20, 4), rrect(30, 8, 62, 16, 3), Polygon([(-34, 6), (-14, 6), (-24, -40), (-44, -36)]), rrect(-30, 22, 16, 28, 2), circ(-36, 25, 4), circ(22, 25, 4))
    guard = ring(-6, -2, 9, 8, 2.8).difference(box(-20, 2, 10, 20))
    holes = U(rrect(-30, -28, -24, -6, 2), [circ(x, 13, 2) for x in (0, 8, 16)], [box(36, 9.5, 58, 10.8)])
    return U(g.difference(holes), guard)

def holocron(r=60):
    hexa = Polygon([(r * math.cos(math.radians(a)), r * math.sin(math.radians(a))) for a in range(30, 390, 60)])
    inner = place(hexa, 0, 0, 0.5)
    y = [stroke([(0, 0), (r * math.cos(math.radians(a)), r * math.sin(math.radians(a)))], 2.6) for a in (90, 210, 330)]
    return U(O(hexa, 3.2), O(inner, 2.4), y, circ(0, 0, 6), [ring(r * 0.62 * math.cos(math.radians(a)), r * 0.62 * math.sin(math.radians(a)), 4, 4, 2.2) for a in (30, 150, 270)])

def kyber():
    c = Polygon([(0, 70), (18, 40), (18, -40), (0, -70), (-18, -40), (-18, 40)])
    facets = [stroke([(0, 70), (0, -70)], 2.2), stroke([(-18, 40), (0, 26), (18, 40)], 2.0), stroke([(-18, -40), (0, -26), (18, -40)], 2.0)]
    return U(O(c, 3.2), facets)

def pine(x, y, h, w):
    return O(Polygon([(x - w, y), (x + w, y), (x, y + h)]), 2.6)

# ---------- designs ----------
def d(f): DESIGNS[f.__name__.replace("_", "-")] = f; return f
DESIGNS = {}

# Ships, solo in a round frame
@d
def xwing_topdown(): return framed(P(xwing_top(), 0, -4, 1.15, 0), 11, 9)
@d
def tie_interceptor_front(): return U(P(tie_interceptor(), 0, 0, 1.0), stroke([(-58, 0), (58, 0)], 3))
@d
def tie_advanced_front(): return framed(P(tie_advanced(), 0, 0, 1.15), 12, 8)
@d
def at_st_walker(): return framed(U(P(at_st(), 0, 4, 1.35), stroke([(-90, -64), (90, -64)], 3)), 13, 6)
@d
def y_wing_topdown(): return framed(P(y_wing(), 0, 4, 1.2), 14, 8)
@d
def a_wing_topdown(): return framed(P(a_wing(), 0, 0, 1.25), 15, 8)
@d
def b_wing_attack(): return framed(P(b_wing(), 0, -2, 1.15, 20), 16, 8)
@d
def snowspeeder_topdown(): return framed(P(snowspeeder(), 0, 0, 1.3), 17, 8)
@d
def firespray_ship(): return framed(P(firespray(), 0, 0, 1.25), 18, 8)
@d
def speeder_bike_side(): return framed(U(P(speeder_bike(), -6, 6, 1.15), stroke([(-90, -30), (90, -30)], 2.6)), 19, 6)
@d
def lambda_shuttle_front(): return framed(P(lambda_shuttle(), 0, 0, 1.2), 20, 8)
@d
def death_star_classic(): return framed(death_star(78), 21, 0)
@d
def falcon_solo(): return framed(P(falcon_icon(), -4, -4, 1.05), 23, 8)
@d
def destroyer_solo(): return framed(P(destroyer_icon(), 4, 0, 1.05, 10), 24, 10)
@d
def x_wing_front_big(): return framed(P(x_wing_icon(), 0, 0, 1.12), 25, 6)

# Droids and helmets in hex frames
@d
def protocol_droid_hex(): return U(hexframe(), P(protocol_droid(), 0, -2, 1.15))
@d
def bb_unit_hex(): return U(hexframe(), P(bb_unit(), 0, 8, 1.15))
@d
def probe_droid_hex(): return U(hexframe(), P(probe_droid(), 0, 0, 1.15), stars(26, 5, P(probe_droid(), 0, 0, 1.15), circ(0, 0, 70)))
@d
def astromech_hex(): return U(hexframe(), P(astromech(), 0, 4, 1.05))
@d
def stormtrooper_hex(): return U(hexframe(), P(stormtrooper_helmet(), 0, 6, 1.3))
@d
def vader_hex(): return U(hexframe(), P(vader_helmet(), 0, 4, 1.2))
@d
def pilot_helmet_hex(): return U(hexframe(), P(pilot_helmet(), 0, 2, 1.3))
@d
def mando_helmet_hex(): return U(hexframe(), P(t_visor_helmet().intersection(circ(0, 0, 64)), 0, 0, 1.2))

# Weapons and artefacts
@d
def lightsaber_tall(): return U(rectframe(50, 94), P(saber(118), 0, -84))
@d
def saber_trio(): return framed(U([P(saber(96, 4), 0, -72, 1, a) for a in (-28, 0, 28)], circ(0, -72, 10)), 27, 6)
@d
def saberstaff(): return framed(U(P(saber(66, 4), 0, 0, 1, -45), P(saber(66, 4), 0, 0, 1, 135), circ(0, 0, 6)), 28, 8)
@d
def blaster_pistol(): return U(rectframe(92, 56), P(blaster(), 0, 4, 1.2), stars(29, 6, P(blaster(), 0, 4, 1.2), box(-80, -48, 80, 48)))
@d
def holocron_hex(): return U(hexframe(), holocron(62))
@d
def kyber_crystal(): return framed(U(P(kyber(), 0, 0, 1.05), [stroke([(0, 0), (80 * math.cos(math.radians(a)), 80 * math.sin(math.radians(a)))], 2.4).difference(P(sil(kyber()), 0, 0, 1.18)) for a in range(0, 360, 30)]), 30, 0)

# Worlds
@d
def hoth_battle():
    g = U(P(sil(walker_icon()), 18, -20, 0.8), P(sil(snowspeeder()), -46, 44, 0.45, -60), stroke([(-92, -64), (92, -64)], 3),
          stroke([(-92, 0), (-66, 30), (-50, 12), (-30, 40), (-6, 14), (20, 34), (50, 8), (70, 26), (92, 6)], 2.6),
          [stroke(fn_curve(lambda x, o=o: o + 2 * math.sin(x / 9 + o), -90, 90), 2.4) for o in (-74, -84)])
    return framed(g, 31, 0)
@d
def endor_forest():
    g = U([pine(x, -60, h, w) for x, h, w in ((-70, 70, 14), (-48, 96, 16), (-24, 80, 15), (40, 90, 16), (64, 72, 14))], P(sil(at_st()), 8, -36, 0.7), stroke([(-92, -60), (92, -60)], 3),
          ring(-50, 58, 12, 12, 2.6))
    return framed(g, 32, 0)
@d
def dagobah_swamp():
    trunk = U(stroke(bez((-40, -60), (-30, -20), (-46, 10), (-30, 40)), 7), stroke(bez((-30, 40), (-10, 50), (10, 40), (30, 56)), 4),
              stroke(bez((-30, 40), (-50, 56), (-70, 50), (-80, 64)), 4), stroke(bez((-40, -60), (-60, -66), (-70, -60), (-86, -66)), 4), stroke(bez((-40, -60), (-20, -70), (0, -62), (10, -72)), 4))
    water = [stroke(fn_curve(lambda x, o=o: o + 2.5 * math.sin(x / 8 + o), -90, 90), 2.4) for o in (-54, -66, -78)]
    return framed(U(trunk, water, P(sil(xwing_top()), 40, -38, 0.55, 75)), 33, 0)
@d
def mustafar_volcano():
    volcano = O(Polygon([(-80, -60), (-14, 34), (14, 34), (80, -60)]), 3)
    lava = [stroke(bez((x0, 34), (x0 * 1.6, 0), (x0 * 2.6, -30), (x0 * 3.6, -60)), 2.6) for x0 in (-6, 6)]
    blasts = [circ(x, y, r) for x, y, r in ((0, 52, 7), (-14, 66, 5), (14, 70, 6), (2, 80, 4))]
    tower = O(U(rrect(40, -60, 58, -10, 3), rrect(34, -14, 64, -6, 2)), 2.6)
    return framed(U(volcano, lava, blasts, tower, stroke([(-92, -60), (92, -60)], 3)), 34, 0)
@d
def cloud_city():
    disc = ell(0, 10, 60, 12)
    towers = U([rrect(x - 5, 10, x + 5, 10 + h, 3) for x, h in ((-30, 22), (-14, 34), (0, 42), (14, 30), (30, 18))])
    stalk = Polygon([(-8, 0), (8, 0), (3, -50), (-3, -50)])
    clouds = U([circ(x, -58 + 4 * math.sin(x), 12) for x in range(-80, 90, 16)]).intersection(box(-100, -100, 100, -50))
    return framed(U(O(U(disc, towers, stalk), 3), O(clouds, 2.6), stroke([(-56, 10), (56, 10)], 2)), 35, 6)
@d
def tatooine_homestead():
    huts = U(O(circ(-30, -50, 22).intersection(box(-60, -50, 0, -20)), 3), O(circ(26, -50, 14).intersection(box(0, -50, 50, -30)), 3), O(rrect(-36, -50, -24, -34, 5), 2.4))
    vapor = [U(stroke([(x, -50), (x, -8)], 3), stroke([(x - 6, -26), (x + 6, -26)], 2.6), circ(x, -6, 3)) for x in (-70, 62)]
    suns = [ring(-12, 36, 14, 14, 2.8), ring(18, 50, 9, 9, 2.8)]
    return framed(U(huts, vapor, suns, stroke(fn_curve(lambda x: -50 + 3 * math.sin(x / 14), -92, 92), 3)), 36, 0)
@d
def hyperspace_jump():
    rng = np.random.default_rng(37)
    streaks = [stroke([(r0 * math.cos(a), r0 * math.sin(a)), ((r0 + ln) * math.cos(a), (r0 + ln) * math.sin(a))], 2.6) for a, r0, ln in
               zip(np.linspace(0, 2 * math.pi, 40, endpoint=False), rng.uniform(24, 50, 40), rng.uniform(16, 40, 40))]
    return framed(U(streaks, P(sil(falcon_icon()), 0, 0, 0.32)), 37, 0)
@d
def star_map():
    rng = np.random.default_rng(38)
    pts = [(-60, 40), (-36, 52), (-10, 34), (14, 50), (40, 24), (-50, -10), (-20, -30), (10, -16), (44, -40), (66, -6)]
    lines = [stroke([pts[i], pts[j]], 2.2) for i, j in ((0, 1), (1, 2), (2, 3), (3, 4), (5, 6), (6, 7), (7, 8), (8, 9), (2, 7))]
    return framed(U(lines, [circ(x, y, 3.2) for x, y in pts], ring(-60, -54, 10, 10, 2.6), ring(54, 56, 7, 7, 2.6), ring(54, 56, 13, 3.5, 2.2, 20)), 38, 0)

# Battles and groups
@d
def dogfight(): return framed(U(P(sil(xwing_top()), -30, -28, 0.6, -35), P(sil(tie_fighter()), 36, 36, 0.34, -20),
                                stroke([(-14, 2), (18, 26)], 2.4), stroke([(-6, -6), (26, 18)], 2.4)), 40, 8)
@d
def falcon_escape(): return framed(U(P(sil(destroyer_icon()), 18, 30, 0.75, -10), P(sil(falcon_icon()), -36, -40, 0.36, 30)), 41, 8)
@d
def death_star_attack(): return framed(U(P(death_star(46), -20, 22, 1),
                                         [P(sil(xwing_top()), x, y, 0.32, a) for x, y, a in ((40, -36, 20), (60, -12, 30), (24, -60, 10))]), 42, 6)
@d
def tie_squadron(): return framed(U([P(sil(tie_fighter()), x, y, 0.3) for x, y in ((-36, 26), (36, 26), (0, -30))]), 43, 8)
@d
def xwing_squadron(): return framed(U([P(sil(xwing_top()), x, y, 0.5) for x, y in ((0, 26), (-46, -26), (46, -26))]), 44, 8)
@d
def asteroid_chase():
    rng = np.random.default_rng(45)
    rocks = [P(U(circ(0, 0, r), circ(r * 0.6, r * 0.3, r * 0.7)), x, y, 1, rng.uniform(0, 360)) for x, y, r in ((-56, 40, 10), (50, 50, 8), (54, -40, 12), (-50, -50, 9), (10, 64, 6), (-10, -66, 7), (70, 8, 6))]
    return framed(U(rocks, P(sil(falcon_icon()), -6, 0, 0.42, -25), P(sil(tie_fighter()), 34, -10, 0.2), P(sil(tie_fighter()), 30, 24, 0.2)), 45, 4)
@d
def destroyer_fleet(): return framed(U(P(sil(destroyer_icon()), -10, 34, 0.7, -8), P(sil(destroyer_icon()), 20, -26, 0.55, -8), ring(-40, -180, 130, 130, 3)), 46, 6)
@d
def droid_duo(): return U(hexframe(), P(astromech(), -34, -8, 0.7), P(protocol_droid(), 36, 0, 0.95))
@d
def helmet_trio(): return framed(U(P(stormtrooper_helmet(), -48, 0, 0.55), P(vader_helmet(), 0, 6, 0.6), P(pilot_helmet(), 48, 0, 0.55)), 47, 0)
@d
def starfighter_row(): return U(rectframe(92, 56), P(sil(xwing_top()), -56, -2, 0.55), P(sil(tie_fighter()), 0, 0, 0.32), P(sil(a_wing()), 56, -2, 0.55))
@d
def endor_moon_rising(): return framed(U(death_star(36, False), P(sil(circ(0, 0, 20)), 46, -46), P(sil(lambda_shuttle()), -44, -42, 0.36)), 48, 6)
@d
def hyperspace_xwing():
    lines = [stroke([(x, -92), (x * 0.1, 30)], 2.4) for x in (-90, -60, -30, 30, 60, 90)]
    return framed(U(lines, P(sil(xwing_top()), 0, -10, 0.55)), 49, 0)
@d
def y_wing_bombing_run():
    trench = [stroke([(x0, -92), (x0 * 0.1, 20)], 2.6) for x0 in (-60, 60)] + [stroke([(-60 + 54 * t, -92 + 112 * t), (60 - 54 * t, -92 + 112 * t)], 2.2) for t in (0.3, 0.55, 0.75)]
    return framed(U(trench, P(sil(y_wing()), 0, -40, 0.45), ring(0, 120, 100, 100, 2.6)), 50, 0)

if __name__ == "__main__":
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    for name in sys.argv[2:] or DESIGNS:
        print(json.dumps(build(name, out, DESIGNS)), flush=True)
