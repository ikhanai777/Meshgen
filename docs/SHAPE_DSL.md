# Shape recipe format (Engine 1)

A recipe is JSON. The language model (Phase 3) writes recipes; templates are recipes. The app checks every
recipe strictly and reports each problem with its path, e.g. `shape.children[1].radius: must be > 0 (got -2)`.

## Conventions
- Units: millimetres. Angles: degrees. Z is up (the print bed is Z = 0).
- **Every primitive rests on the bed and is centred on the Z axis** (it spans z = 0 … its height).
  Use `translate` / `rotate` to place parts.
- Any number can be an **expression string** using parameters: `"diameter/2 - wall"`.
  Operators `+ - * / ^`, parentheses, `pi`, functions `min max abs sqrt sin cos tan floor ceil round clamp`.
- Every node may carry a `"comment"` (ignored).

## Top level
```json
{
  "version": 1,
  "name": "Planter",
  "description": "optional",
  "units": "mm",
  "params": {
    "height": {"value": 110, "min": 30, "max": 250, "step": 1, "unit": "mm", "label": "Height"},
    "sides":  {"value": 6, "min": 3, "max": 12, "integer": true}
  },
  "shape": { ...node... }
}
```
`params` entries may also be a bare number (`"height": 110`); a range is then chosen automatically.

## Nodes

| type | fields |
|---|---|
| `box` | `size` [x, y, z], `round` (edge radius, default 0) |
| `sphere` | `radius` |
| `cylinder` | `radius`, `height`, `round` (default 0) |
| `cone` | `radius_bottom`, `radius_top`, `height` (a frustum; one radius may be 0) |
| `torus` | `major_radius` (centre of tube), `minor_radius` (tube) — lies flat |
| `capsule` | `radius`, `height` (total, ≥ 2 × radius) — vertical |
| `extrude` | `height`, and either `points` [[x,y], …] or `sides` + `radius` (corner distance); optional `twist` (degrees over the height), `taper` (top scale, default 1) |
| `union` | `children` [nodes] |
| `subtract` | `children` [base, cut, cut, …] — the first minus the rest |
| `intersect` | `children` [nodes] — only what all share |
| `smooth_union` | `children`, `radius` (fillet size) |
| `translate` | `offset` [x, y, z], `child` |
| `rotate` | `angles` [x, y, z] degrees (applied X, then Y, then Z, about the origin), `child` |
| `scale` | `factor` (number or [x, y, z]), `child` |
| `linear_array` | `count`, `spacing` [x, y, z], `child` — copies at 0, spacing, 2×spacing… |
| `radial_array` | `count`, `radius` (moves the child +X first, default 0), `start_angle`, `child` — copies around Z |
| `shell` | `thickness`, `open_top` (bool), `child` — hollows inward; `open_top` removes the lid |
| `offset` | `distance` (+ grows / − shrinks, rounds edges), `child` |

## Limits
At most 3000 parts after arrays are expanded, 40 levels of nesting, arrays of up to 200 copies and
1000 mm per side. Outlines (`points`) must not cross themselves.

## Meshing
The recipe compiles to a signed distance field, which is meshed with marching cubes (a generated case table
with consistent face rules, so output is always watertight and manifold), placed on the bed, cleaned and checked.
Quality: Draft 64, Standard 128, Fine 200 cells along the longest side.
