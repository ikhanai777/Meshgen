"""Middle Earth shadow box layer: burning sky, the eye and title."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import plate_compound, plate_parts, plate_rect
import mordor_layers as layers


def mordor_sky_parts():
    result = layers.sky()
    base_color, features = result[0], result[1]
    outline = result[2] if len(result) > 2 else plate_rect()
    return plate_parts("mordor_sky", outline, base_color, features)


@step(out="../STEP/mordor_sky.step")
@stl(out="../STL/mordor_sky.stl")
@threemf(out="../3MF/mordor_sky.3mf")
def mordor_sky():
    return plate_compound("mordor_sky", mordor_sky_parts())


if __name__ == "__main__":
    mordor_sky()
