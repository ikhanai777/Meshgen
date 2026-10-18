"""Snowy hills layer of the Japan shadow box."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import WHITE, band, plate_compound, plate_parts

HILLS = [(-90.0, 64.0), (-75.0, 70.0), (-45.0, 62.0), (-10.0, 72.0), (25.0, 60.0), (60.0, 70.0), (90.0, 62.0)]


@step(out="../STEP/japan_hills.step")
@stl(out="../STL/japan_hills.stl")
@threemf(out="../3MF/japan_hills.3mf")
def japan_hills():
    return plate_compound("hills", plate_parts("hills", band(HILLS), WHITE, []))


if __name__ == "__main__":
    japan_hills()
