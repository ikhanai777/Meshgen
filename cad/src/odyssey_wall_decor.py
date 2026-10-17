"""Starship Odyssey wall plaque: back plate with the ship relief mounted on it."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import glb, step, threemf

from odyssey_plate import odyssey_plate
from odyssey_ship import odyssey_ship


@step(out="../STEP/odyssey_wall_decor.step")
@glb(out="../GLB/odyssey_wall_decor.glb")
@threemf(out="../3MF/odyssey_wall_decor.3mf")
def odyssey_wall_decor():
    plate = odyssey_plate()
    plate.label = "plate"
    ship = odyssey_ship()
    ship.label = "ship"
    return bd.Compound(children=[plate, ship], label="odyssey_wall_decor")


if __name__ == "__main__":
    odyssey_wall_decor()
