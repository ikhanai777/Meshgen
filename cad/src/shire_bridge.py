"""The Shire shadow box layer: stone bridge, river and mill."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import plate_compound, plate_parts, plate_rect
import shire_layers as layers


def shire_bridge_parts():
    result = layers.bridge()
    base_color, features = result[0], result[1]
    outline = result[2] if len(result) > 2 else plate_rect()
    return plate_parts("shire_bridge", outline, base_color, features)


@step(out="../STEP/shire_bridge.step")
@stl(out="../STL/shire_bridge.stl")
@threemf(out="../3MF/shire_bridge.3mf")
def shire_bridge():
    return plate_compound("shire_bridge", shire_bridge_parts())


if __name__ == "__main__":
    shire_bridge()
