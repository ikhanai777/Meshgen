"""Shared dimensions for the X-wing phone stand.

Units are millimeters, +Z up, and the phone faces the viewer at -Y. The
base sits on Z=0 centered on X. The phone leans back on a tilted cradle and
an arch rising from the back of the base carries a 1:2 copy of the X-wing
model above the phone. The ship is a separate print keyed onto a square peg.
"""

from __future__ import annotations

import math

from cadgen import build123d as bd
from cadgen import srgb

# Base block
BASE_W = 76.0  # X
BASE_D = 92.0  # Y
BASE_H = 26.0

# Cradle, in a frame tilted back by CRADLE_TILT: u points out of the phone's
# screen (forward), v up along the phone's back. Origin is the phone's
# back-bottom corner.
CRADLE_TILT = 15.0
CRADLE_ORIGIN = (-10.0, 30.0)  # (y, z)
CRADLE_W = 68.0
PHONE_DEPTH = 13.5  # max phone thickness, case included

# Ship mount on top of the arch
SHIP_SCALE = 0.5
SHIP_MOUNT = (0.0, 18.0, 238.0)  # peg base center = ship belly at the socket
PEG = 5.0
PEG_LEN = 5.0
PEG_CLEARANCE = 0.25  # per side, in the ship's socket
SOCKET_DEPTH = 5.6
SOCKET_SHIP_X = 10.0  # socket position along the unscaled ship's X axis
SHIP_BELLY_Z = -14.0  # unscaled ship frame

GREY = srgb("#7A7E83")
DARK = srgb("#3F4348")
ORANGE = srgb("#E07B2A")
RED = srgb("#B8322B")
WHITE = srgb("#E6E6E2")


def cradle_axes() -> tuple[tuple[float, float], tuple[float, float]]:
    """(u, v) as world (y, z) directions: u forward out of the screen, v up."""
    t = math.radians(CRADLE_TILT)
    return (-math.cos(t), math.sin(t)), (math.sin(t), math.cos(t))


def cradle_point(u: float, v: float) -> tuple[float, float]:
    """World (y, z) of a cradle-frame point."""
    (uy, uz), (vy, vz) = cradle_axes()
    y0, z0 = CRADLE_ORIGIN
    return (y0 + u * uy + v * vy, z0 + u * uz + v * vz)


def ship_location() -> bd.Location:
    """Placement of the scaled ship: its nose points at -Y, socket on the peg."""
    x, y, z = SHIP_MOUNT
    s = SHIP_SCALE
    return bd.Location((x, y + SOCKET_SHIP_X * s, z - SHIP_BELLY_Z * s), (0, 0, -90))
