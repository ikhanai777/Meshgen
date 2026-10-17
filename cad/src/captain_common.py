"""Shared dimensions for the ship's-wheel ("CAPTAIN") phone stand.

Units are millimeters, +Z up, and the phone faces the viewer at -Y. The
stand's base sits on Z=0 centered on the origin. The wheel is a separate
flat-printed part: in its own frame it lies on XY with its back face at Z=0
and its front face at Z=WHEEL_T, and +Y is "up" along the wheel.
"""

from __future__ import annotations

import math

from cadgen import srgb

BASE_R = 55.0
BASE_H = 12.0

WHEEL_T = 8.0
WHEEL_TILT = 22.0  # degrees the wheel leans back from vertical
# World position of the wheel hub's center on its back face
WHEEL_BACK_CENTER = (0.0, 18.0, 96.0)

PEG = 8.0  # square peg side
PEG_LEN = 8.0
PEG_CLEARANCE = 0.3  # per side, in the hub socket
SOCKET_DEPTH = 9.0

NAVY = srgb("#1F4E8C")
SILVER = srgb("#C8CCD2")


def wheel_axes() -> tuple[tuple[float, float, float], tuple[float, float, float]]:
    """World directions of the wheel's local +Y (up) and +Z (front normal)."""
    t = math.radians(WHEEL_TILT)
    up = (0.0, math.sin(t), math.cos(t))
    normal = (0.0, -math.cos(t), math.sin(t))
    return up, normal
