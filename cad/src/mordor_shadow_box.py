"""Middle Earth layered shadow box: the shared frame and mat with five layers."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import glb, step, threemf

from japan_common import plate_location
from japan_frame import japan_frame
from japan_mat import japan_mat
from mordor_front import mordor_front
from mordor_bridge import mordor_bridge
from mordor_volcano import mordor_volcano
from mordor_mountains import mordor_mountains
from mordor_sky import mordor_sky

# Frame slot -> layer
LAYERS = {
    "mat": japan_mat,
    "front": mordor_front,
    "hills": mordor_bridge,
    "city": mordor_volcano,
    "fuji": mordor_mountains,
    "sky": mordor_sky,
}


@step(out="../STEP/mordor_shadow_box.step")
@glb(out="../GLB/mordor_shadow_box.glb")
@threemf(out="../3MF/mordor_shadow_box.3mf")
def mordor_shadow_box():
    frame = japan_frame()
    frame.label = "frame"
    children = [frame]
    for slot, model in LAYERS.items():
        plate = plate_location(slot) * model()
        plate.label = slot
        children.append(plate)
    return bd.Compound(children=children, label="mordor_shadow_box")


if __name__ == "__main__":
    mordor_shadow_box()
