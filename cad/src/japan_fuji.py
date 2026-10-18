"""Mount Fuji layer of the Japan shadow box, with a snow cap."""

from __future__ import annotations

from cadgen import step, stl, threemf

from japan_common import BLACK, PLATE_X, WHITE, band, plate_compound, plate_parts, poly

SLOPE = [(-90.0, 72.0), (-60.0, 82.0), (-32.0, 101.0), (-14.0, 124.0), (-2.0, 139.0), (16.0, 140.0),
         (28.0, 124.0), (46.0, 102.0), (70.0, 86.0), (90.0, 78.0)]
SNOW_LINE = [(-20.0, 116.0), (-12.0, 110.0), (-7.0, 119.0), (-1.0, 106.0), (5.0, 117.0), (10.0, 104.0),
             (14.0, 118.0), (19.0, 109.0), (24.0, 120.0), (34.0, 115.0)]


def fuji_parts():
    mountain = band(SLOPE)
    snow = mountain & poly(SNOW_LINE + [(34.0, 160.0), (-20.0, 160.0)])
    return plate_parts("fuji", mountain, BLACK, [("snow", snow, WHITE, 1.2)])


@step(out="../STEP/japan_fuji.step")
@stl(out="../STL/japan_fuji.stl")
@threemf(out="../3MF/japan_fuji.3mf")
def japan_fuji():
    return plate_compound("fuji", fuji_parts())


if __name__ == "__main__":
    japan_fuji()
