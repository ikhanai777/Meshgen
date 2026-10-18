"""Japan layered shadow box: the frame with its six plates slid into place."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import glb, step, threemf

from japan_city import japan_city
from japan_common import plate_location
from japan_frame import japan_frame
from japan_front import japan_front
from japan_fuji import japan_fuji
from japan_hills import japan_hills
from japan_mat import japan_mat
from japan_sky import japan_sky

LAYERS = {
    "mat": japan_mat,
    "front": japan_front,
    "hills": japan_hills,
    "city": japan_city,
    "fuji": japan_fuji,
    "sky": japan_sky,
}


@step(out="../STEP/japan_shadow_box.step")
@glb(out="../GLB/japan_shadow_box.glb")
@threemf(out="../3MF/japan_shadow_box.3mf")
def japan_shadow_box():
    frame = japan_frame()
    frame.label = "frame"
    children = [frame]
    for slot, model in LAYERS.items():
        plate = plate_location(slot) * model()
        plate.label = slot
        children.append(plate)
    return bd.Compound(children=children, label="japan_shadow_box")


if __name__ == "__main__":
    japan_shadow_box()
