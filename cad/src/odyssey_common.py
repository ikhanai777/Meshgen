"""Shared dimensions and colors for the Starship Odyssey wall plaque.

Units are millimeters. The plaque lies in XY with X to the right and Y up,
as seen on the wall; +Z points out of the wall. The plate's back face is
Z=0 and its front face is Z=PLATE_T. Proportions are estimated from a
reference photo at 0.4 mm per image pixel.
"""

from __future__ import annotations

from cadgen import srgb

PLATE_W = 230.0
PLATE_H = 248.0
PLATE_T = 5.0
PLATE_CORNER_R = 5.0

# Ship datums on the plate's front face
SHIP_Y = 138.0  # ship centerline
SAUCER_X = 32.0  # saucer center (saucer overhangs the plate's left edge)
SAUCER_R = 60.0
NACELLE_OFFSET = 55.0  # nacelle centerline offset from SHIP_Y

PLATE_COLOR = srgb("#2A2C2F")
LETTER_COLOR = srgb("#D9DCDF")
SHIP_COLOR = srgb("#80868C")
GLOW_COLOR = srgb("#3F7DFF")
