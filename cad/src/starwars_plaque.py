"""Layered space-battle wall plaque: all five layers stacked in place."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import glb, step, threemf

from starwars_back import starwars_back
from starwars_frame import starwars_frame
from starwars_hills import starwars_hills
from starwars_ship import starwars_ship
from starwars_space import starwars_space


@step(out="../STEP/starwars_plaque.step")
@glb(out="../GLB/starwars_plaque.glb")
@threemf(out="../3MF/starwars_plaque.3mf")
def starwars_plaque():
    layers = []
    for name, model in (("back", starwars_back), ("space", starwars_space), ("hills", starwars_hills),
                        ("ship", starwars_ship), ("frame", starwars_frame)):
        part = model()
        part.label = name
        layers.append(part)
    return bd.Compound(children=layers, label="starwars_plaque")


if __name__ == "__main__":
    starwars_plaque()
