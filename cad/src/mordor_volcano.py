"""Middle Earth shadow box layer: the volcano with lava."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import plate_compound, plate_parts, plate_rect
import mordor_layers as layers


def mordor_volcano_parts():
    result = layers.volcano()
    base_color, features = result[0], result[1]
    outline = result[2] if len(result) > 2 else plate_rect()
    return plate_parts("mordor_volcano", outline, base_color, features)


@step(out="../STEP/mordor_volcano.step")
@stl(out="../STL/mordor_volcano.stl")
@threemf(out="../3MF/mordor_volcano.3mf")
def mordor_volcano():
    return plate_compound("mordor_volcano", mordor_volcano_parts())


if __name__ == "__main__":
    mordor_volcano()
