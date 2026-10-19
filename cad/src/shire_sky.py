"""The Shire shadow box layer: sky, far hills and title."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import plate_compound, plate_parts, plate_rect
import shire_layers as layers


def shire_sky_parts():
    result = layers.sky()
    base_color, features = result[0], result[1]
    outline = result[2] if len(result) > 2 else plate_rect()
    return plate_parts("shire_sky", outline, base_color, features)


@step(out="../STEP/shire_sky.step")
@stl(out="../STL/shire_sky.stl")
@threemf(out="../3MF/shire_sky.3mf")
def shire_sky():
    return plate_compound("shire_sky", shire_sky_parts())


if __name__ == "__main__":
    shire_sky()
