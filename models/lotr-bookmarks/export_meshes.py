"""Write print-ready STL and 3MF files from the bookmark STEP models, and check that every mesh is closed.

The meshes come from Open CASCADE's own mesher (build123d export), which closes these flat, many-holed
faces reliably. Run each model in src/ first to refresh its STEP, then:

    python export_meshes.py
"""
from __future__ import annotations

import struct
from pathlib import Path

from cadgen import build123d as bd
from cadgen import read_step

ROOT = Path(__file__).resolve().parent
BOOKMARKS = ["one_ring", "white_tree", "doors_of_durin", "anduril", "lorien_leaf"]
LINEAR_TOL = 0.02  # mm chord deviation
ANGULAR_TOL = 0.2  # radians


def _open_edges(stl: Path) -> int:
    """Edges used by only one triangle, after welding identical vertices. 0 means the mesh is closed."""
    data = stl.read_bytes()
    count = struct.unpack_from("<I", data, 80)[0]
    index: dict[tuple[float, float, float], int] = {}
    edges: dict[tuple[int, int], int] = {}
    for t in range(count):
        base = 84 + t * 50 + 12
        ids = []
        for k in range(3):
            v = struct.unpack_from("<3f", data, base + 12 * k)
            ids.append(index.setdefault(v, len(index)))
        for a, b in ((ids[0], ids[1]), (ids[1], ids[2]), (ids[2], ids[0])):
            key = (a, b) if a < b else (b, a)
            edges[key] = edges.get(key, 0) + 1
    return sum(1 for n in edges.values() if n != 2)


def main() -> None:
    (ROOT / "STL").mkdir(exist_ok=True)
    (ROOT / "3MF").mkdir(exist_ok=True)
    failed = []
    for name in BOOKMARKS:
        part = read_step(str(ROOT / "STEP" / f"{name}.step"))
        stl = ROOT / "STL" / f"{name}.stl"
        bd.export_stl(part, str(stl), tolerance=LINEAR_TOL, angular_tolerance=ANGULAR_TOL)
        mesher = bd.Mesher()
        mesher.add_shape(part, linear_deflection=LINEAR_TOL, angular_deflection=ANGULAR_TOL, part_number=name)
        mesher.write(str(ROOT / "3MF" / f"{name}.3mf"))
        bad = _open_edges(stl)
        size = part.bounding_box().size
        print(f"{name:15s} {size.X:5.1f} x {size.Y:5.1f} x {size.Z:4.2f} mm  volume {part.volume / 1000:5.2f} cm3  "
              f"{'closed' if bad == 0 else f'{bad} open edges'}")
        if bad:
            failed.append(name)
    if failed:
        raise SystemExit(f"open meshes: {', '.join(failed)}")


if __name__ == "__main__":
    main()
