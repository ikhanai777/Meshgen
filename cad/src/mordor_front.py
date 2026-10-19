"""Middle Earth shadow box layer: dead trees, a ruined arch and travelers."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import plate_compound, plate_parts, plate_rect
import mordor_layers as layers


def mordor_front_parts():
    result = layers.front()
    base_color, features = result[0], result[1]
    outline = result[2] if len(result) > 2 else plate_rect()
    return plate_parts("mordor_front", outline, base_color, features)


@step(out="../STEP/mordor_front.step")
@stl(out="../STL/mordor_front.stl")
@threemf(out="../3MF/mordor_front.3mf")
def mordor_front():
    return plate_compound("mordor_front", mordor_front_parts())


if __name__ == "__main__":
    mordor_front()
