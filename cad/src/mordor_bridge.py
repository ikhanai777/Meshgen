"""Middle Earth shadow box layer: three-arch stone bridge."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import plate_compound, plate_parts, plate_rect
import mordor_layers as layers


def mordor_bridge_parts():
    result = layers.bridge()
    base_color, features = result[0], result[1]
    outline = result[2] if len(result) > 2 else plate_rect()
    return plate_parts("mordor_bridge", outline, base_color, features)


@step(out="../STEP/mordor_bridge.step")
@stl(out="../STL/mordor_bridge.stl")
@threemf(out="../3MF/mordor_bridge.3mf")
def mordor_bridge():
    return plate_compound("mordor_bridge", mordor_bridge_parts())


if __name__ == "__main__":
    mordor_bridge()
