"""Back plate of the space-battle plaque: the full outline with bolt holes."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import step, stl, threemf

from starwars_common import BLACK, T_BACK, Z_BACK, bolt_holes, layer_parts, outline


@step(out="../STEP/starwars_back.step")
@stl(out="../STL/starwars_back.stl")
@threemf(out="../3MF/starwars_back.3mf")
def starwars_back():
    parts = layer_parts("back", outline() - bolt_holes(), BLACK, [], T_BACK, Z_BACK)
    return bd.Compound(children=parts, label="back")


if __name__ == "__main__":
    starwars_back()
