"""The Shire shadow box layer: lane with a cart, signpost and trees."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import plate_compound, plate_parts, plate_rect
import shire_layers as layers


def shire_front_parts():
    result = layers.front()
    base_color, features = result[0], result[1]
    outline = result[2] if len(result) > 2 else plate_rect()
    return plate_parts("shire_front", outline, base_color, features)


@step(out="../STEP/shire_front.step")
@stl(out="../STL/shire_front.stl")
@threemf(out="../3MF/shire_front.3mf")
def shire_front():
    return plate_compound("shire_front", shire_front_parts())


if __name__ == "__main__":
    shire_front()
