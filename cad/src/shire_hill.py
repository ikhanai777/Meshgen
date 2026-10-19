"""The Shire shadow box layer: the Hill with the big tree."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import plate_compound, plate_parts, plate_rect
import shire_layers as layers


def shire_hill_parts():
    result = layers.hill()
    base_color, features = result[0], result[1]
    outline = result[2] if len(result) > 2 else plate_rect()
    return plate_parts("shire_hill", outline, base_color, features)


@step(out="../STEP/shire_hill.step")
@stl(out="../STL/shire_hill.stl")
@threemf(out="../3MF/shire_hill.3mf")
def shire_hill():
    return plate_compound("shire_hill", shire_hill_parts())


if __name__ == "__main__":
    shire_hill()
