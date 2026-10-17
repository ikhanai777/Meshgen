"""Stylized X-wing starfighter, modeled from a reference illustration.

Units are millimeters. +X points toward the nose, +Y to starboard, +Z up.
The model is a display piece: proportions are estimated from the image, not
from a dimensioned drawing.
"""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import glb, srgb, step, stl

# Fuselage cross-sections: (x, width, height, center z)
FUSELAGE_SECTIONS = [
    (10.0, 32.0, 28.0, 0.0),
    (60.0, 26.0, 22.0, -1.0),
    (170.0, 16.0, 14.0, -3.5),
    (205.0, 7.0, 6.0, -4.5),
]
REAR_BLOCK_X = (-30.0, 10.0)

# Wing (S-foil) geometry, in the wing's local frame (root at y=0)
WING_SPAN = 100.0
WING_THICKNESS = 3.0
WING_ROOT_X = (-28.0, 32.0)
WING_TIP_X = (-18.0, 12.0)
WING_ROOT_Y = 12.0  # root attachment, from the centerline
WING_ROOT_Z = 4.0
WING_OPEN_ANGLE = 13.0  # degrees each S-foil is opened from the horizontal

ENGINE_RADIUS = 8.0
ENGINE_X = (-34.0, 22.0)
ENGINE_LOCAL_Y = 8.0
ENGINE_LOCAL_Z = 8.5

CANNON_HOUSING_X = (-22.0, 18.0)
CANNON_BARREL_END_X = 80.0

HULL = srgb("#C9C6BE")
MARKING = srgb("#A8322D")
ENGINE = srgb("#8E8B85")
DARK = srgb("#2A2A2A")
EXHAUST = srgb("#FF7A2E")
CANOPY = srgb("#2E3A45", 0.6)
CANNON = srgb("#B5B2AB")
EMITTER = srgb("#FF2A2A")
DROID = srgb("#E8EAEE")
DROID_BLUE = srgb("#2850A0")


def _section(x: float, w: float, h: float, zc: float) -> bd.Wire:
    return bd.Wire.make_polygon(
        [
            (x, -w / 2, zc - h / 2),
            (x, w / 2, zc - h / 2),
            (x, w / 2, zc + h / 2),
            (x, -w / 2, zc + h / 2),
        ],
        close=True,
    )


def _trapezoid(x: float, base_w: float, top_w: float, z0: float, z1: float) -> bd.Wire:
    return bd.Wire.make_polygon(
        [
            (x, -base_w / 2, z0),
            (x, base_w / 2, z0),
            (x, top_w / 2, z1),
            (x, -top_w / 2, z1),
        ],
        close=True,
    )


def _x_cylinder(radius: float, x0: float, x1: float, y: float = 0.0, z: float = 0.0) -> bd.Solid:
    """Cylinder along +X from x0 to x1, centered on (y, z)."""
    return bd.Solid.make_cylinder(
        radius, x1 - x0, bd.Plane((x0, y, z), z_dir=(1, 0, 0))
    )


def fuselage() -> bd.Solid:
    x0, x1 = REAR_BLOCK_X
    w, h, zc = FUSELAGE_SECTIONS[0][1:]
    rear = bd.Solid.make_box(
        x1 - x0, w, h, bd.Plane((x0, -w / 2, zc - h / 2))
    )
    nose = bd.Solid.make_loft([_section(*s) for s in FUSELAGE_SECTIONS], ruled=True)
    return rear.fuse(nose).clean()


def canopy() -> bd.Solid:
    return bd.Solid.make_loft(
        [
            _trapezoid(72.0, 18.0, 12.0, 8.0, 17.0),
            _trapezoid(105.0, 15.0, 9.0, 6.0, 14.0),
            _trapezoid(135.0, 11.0, 7.0, 4.5, 5.8),
        ],
        ruled=True,
    )


def astromech() -> tuple[bd.Solid, bd.Solid]:
    cx, cz, r = 45.0, 11.0, 6.5
    body = _z_cylinder(r, cx, cz - 3.0, cz)
    dome = bd.Solid.make_sphere(r).moved(bd.Location((cx, 0, cz)))
    keep = bd.Solid.make_box(3 * r, 3 * r, 2 * r, bd.Plane((cx - 1.5 * r, -1.5 * r, cz)))
    head = body.fuse(_common(dome, keep)).clean()
    eye = bd.Solid.make_box(1.0, 3.0, 2.5, bd.Plane((cx + r * 0.78, -1.5, cz + 1.5)))
    return head, eye


def _common(a: bd.Shape, b: bd.Shape) -> bd.Shape:
    """Boolean intersection as a single shape."""
    result = a.intersect(b)
    if isinstance(result, list):
        result = result[0] if len(result) == 1 else bd.Compound(result)
    return result


def _z_cylinder(radius: float, x: float, z0: float, z1: float) -> bd.Solid:
    return bd.Solid.make_cylinder(radius, z1 - z0, bd.Plane((x, 0, z0)))


def _wing_outline(z0: float, z1: float) -> bd.Solid:
    profile = bd.Face(
        bd.Wire.make_polygon(
            [
                (WING_ROOT_X[0], -4.0, z0),
                (WING_ROOT_X[1], -4.0, z0),
                (WING_TIP_X[1], WING_SPAN, z0),
                (WING_TIP_X[0], WING_SPAN, z0),
            ],
            close=True,
        )
    )
    return bd.Solid.extrude(profile, (0, 0, z1 - z0))


def wing_parts() -> dict[str, tuple[bd.Shape, bd.Color]]:
    """Upper-starboard S-foil parts, in the wing's local frame."""
    t = WING_THICKNESS / 2
    wing = _wing_outline(-t, t)

    # Red markings near the tip, clipped to the wing outline
    markings = {}
    for where, y0 in (("inner", WING_SPAN - 32.0), ("outer", WING_SPAN - 20.0)):
        band = bd.Solid.make_box(80.0, 6.0, 0.4, bd.Plane((-40.0, y0, t)))
        markings[f"marking_{where}"] = (_common(band, _wing_outline(t, t + 0.4)), MARKING)

    ey, ez = ENGINE_LOCAL_Y, ENGINE_LOCAL_Z
    ex0, ex1 = ENGINE_X
    engine = _x_cylinder(ENGINE_RADIUS, ex0, ex1, ey, ez)
    engine = engine.fuse(_x_cylinder(ENGINE_RADIUS * 0.8, ex0 - 6.0, ex0, ey, ez))
    engine = engine.cut(_x_cylinder(ENGINE_RADIUS * 0.8, ex1 - 8.0, ex1 + 1.0, ey, ez))
    engine = engine.clean()
    intake_hub = bd.Solid.make_cone(
        3.0, 0.5, 7.0, bd.Plane((ex1 - 8.0, ey, ez), z_dir=(1, 0, 0))
    )
    exhaust = _x_cylinder(ENGINE_RADIUS * 0.65, ex0 - 6.6, ex0 - 6.0, ey, ez)

    cy = WING_SPAN + 3.0
    hx0, hx1 = CANNON_HOUSING_X
    cannon = _x_cylinder(3.5, hx0, hx1, cy)
    cannon = cannon.fuse(_x_cylinder(1.5, hx1, CANNON_BARREL_END_X, cy))
    cannon = cannon.fuse(
        _x_cylinder(2.6, CANNON_BARREL_END_X, CANNON_BARREL_END_X + 8.0, cy)
    ).clean()
    emitter = _x_cylinder(1.2, CANNON_BARREL_END_X + 8.0, CANNON_BARREL_END_X + 10.0, cy)

    return {
        "wing": (wing, HULL),
        **markings,
        "engine": (engine, ENGINE),
        "intake": (intake_hub, DARK),
        "exhaust": (exhaust, EXHAUST),
        "cannon": (cannon, CANNON),
        "emitter": (emitter, EMITTER),
    }


def _labeled(shape: bd.Shape, label: str, color: bd.Color) -> bd.Shape:
    shape.label = label
    shape.color = color
    return shape


@step(out="../STEP/xwing.step")
@glb(out="../GLB/xwing.glb")
@stl(out="../STL/xwing.stl")
def xwing():
    children = [
        _labeled(fuselage(), "fuselage", HULL),
        _labeled(canopy(), "canopy", CANOPY),
    ]
    head, eye = astromech()
    children += [_labeled(head, "astromech", DROID), _labeled(eye, "astromech_eye", DROID_BLUE)]

    place = bd.Location((0, WING_ROOT_Y, WING_ROOT_Z))
    for name, (shape, color) in wing_parts().items():
        upper_right = place * shape.rotate(bd.Axis.X, WING_OPEN_ANGLE)
        variants = {
            "upper_right": upper_right,
            "lower_right": bd.mirror(upper_right, about=bd.Plane.XY),
        }
        variants["upper_left"] = bd.mirror(variants["upper_right"], about=bd.Plane.XZ)
        variants["lower_left"] = bd.mirror(variants["lower_right"], about=bd.Plane.XZ)
        for where, placed in variants.items():
            children.append(_labeled(placed, f"{name}:{where}", color))

    return bd.Compound(children=children, label="xwing")


if __name__ == "__main__":
    xwing()
