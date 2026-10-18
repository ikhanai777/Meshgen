"""X-wing phone stand with a prop phone in the cradle, for display renders.

The prop is a generic 160 x 76 x 9 mm phone with rounded corners and a
screen, resting on the seat against the back plate.
"""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import glb, srgb, step

from xwing_phone_stand import xwing_phone_stand
from xwing_stand import cradle_plane

PHONE = (76.0, 160.0, 9.0)  # width, height, thickness
CORNER_R = 9.0
BEZEL = 2.5
PHONE_BODY = srgb("#2B2D31")
SCREEN = srgb("#0B0C10")


def prop_phone() -> list[bd.Shape]:
    """Phone parts in the cradle frame: X across, Y up its back, Z out of the screen."""
    w, h, t = PHONE
    body = bd.Solid.make_box(w, h, t, bd.Plane((-w / 2, 0, 0)))
    body = bd.fillet(body.edges().filter_by(bd.Axis.Z), CORNER_R)
    body = bd.fillet(body.edges().group_by(bd.Axis.Z)[-1], 1.0)
    sw, sh = w - 2 * BEZEL, h - 2 * BEZEL
    screen = bd.Solid.make_box(sw, sh, 0.3, bd.Plane((-sw / 2, BEZEL, t)))
    screen = bd.fillet(screen.edges().filter_by(bd.Axis.Z), CORNER_R - BEZEL)
    body.label, body.color = "phone_body", PHONE_BODY
    screen.label, screen.color = "phone_screen", SCREEN
    return [body, screen]


@step(out="../STEP/xwing_phone_stand_with_phone.step")
@glb(out="../GLB/xwing_phone_stand_with_phone.glb")
def xwing_phone_stand_with_phone():
    stand = xwing_phone_stand()
    place = cradle_plane().location * bd.Location((0, 0.1, 0.1))
    phone = place * bd.Compound(children=prop_phone(), label="phone")
    phone.label = "phone"
    return bd.Compound(children=[stand, phone], label="xwing_phone_stand_with_phone")


if __name__ == "__main__":
    xwing_phone_stand_with_phone()
