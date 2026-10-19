"""The Shire layered shadow box: the shared frame and mat with five layers."""

from __future__ import annotations

from cadgen import build123d as bd
from cadgen import glb, step, threemf

from japan_common import plate_location
from japan_frame import japan_frame
from japan_mat import japan_mat
from shire_front import shire_front
from shire_bridge import shire_bridge
from shire_smials import shire_smials
from shire_hill import shire_hill
from shire_sky import shire_sky

# Frame slot -> layer
LAYERS = {
    "mat": japan_mat,
    "front": shire_front,
    "hills": shire_bridge,
    "city": shire_smials,
    "fuji": shire_hill,
    "sky": shire_sky,
}


@step(out="../STEP/shire_shadow_box.step")
@glb(out="../GLB/shire_shadow_box.glb")
@threemf(out="../3MF/shire_shadow_box.3mf")
def shire_shadow_box():
    frame = japan_frame()
    frame.label = "frame"
    children = [frame]
    for slot, model in LAYERS.items():
        plate = plate_location(slot) * model()
        plate.label = slot
        children.append(plate)
    return bd.Compound(children=children, label="shire_shadow_box")


if __name__ == "__main__":
    shire_shadow_box()
