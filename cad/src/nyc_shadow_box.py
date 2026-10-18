"""New York layered shadow box: the shared frame and mat with five layers."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import glb, step, threemf

from japan_common import plate_location
from japan_frame import japan_frame
from japan_mat import japan_mat
from nyc_bridge import nyc_bridge
from nyc_city import nyc_city
from nyc_front import nyc_front
from nyc_sky import nyc_sky
from nyc_skyline import nyc_skyline

# Frame slot -> layer
LAYERS = {
    "mat": japan_mat,
    "front": nyc_front,
    "hills": nyc_bridge,
    "city": nyc_city,
    "fuji": nyc_skyline,
    "sky": nyc_sky,
}


@step(out="../STEP/nyc_shadow_box.step")
@glb(out="../GLB/nyc_shadow_box.glb")
@threemf(out="../3MF/nyc_shadow_box.3mf")
def nyc_shadow_box():
    frame = japan_frame()
    frame.label = "frame"
    children = [frame]
    for slot, model in LAYERS.items():
        plate = plate_location(slot) * model()
        plate.label = slot
        children.append(plate)
    return bd.Compound(children=children, label="nyc_shadow_box")


if __name__ == "__main__":
    nyc_shadow_box()
