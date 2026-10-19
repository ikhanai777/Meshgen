"""Middle Earth shadow box layer: jagged mountains and the dark tower."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import plate_compound, plate_parts, plate_rect
import mordor_layers as layers


def mordor_mountains_parts():
    result = layers.mountains()
    base_color, features = result[0], result[1]
    outline = result[2] if len(result) > 2 else plate_rect()
    return plate_parts("mordor_mountains", outline, base_color, features)


@step(out="../STEP/mordor_mountains.step")
@stl(out="../STL/mordor_mountains.stl")
@threemf(out="../3MF/mordor_mountains.3mf")
def mordor_mountains():
    return plate_compound("mordor_mountains", mordor_mountains_parts())


if __name__ == "__main__":
    mordor_mountains()
