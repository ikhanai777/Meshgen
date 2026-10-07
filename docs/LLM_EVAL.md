# Text → shape: measured results

Measured on a desktop (4 CPU cores) with the **same prompts, grammars and agent code the app uses**
(`tools/llm_eval/server.py` + `core/src/test/.../LlmEval.kt`). Phone timings will differ; see "Speed" below.
"Watertight" is checked automatically; "matches the request" is graded by hand.

## Approach history

1. **Free-form recipes only** (the model writes the whole CSG recipe). Qwen3 1.7B, prompt
   "a 10cm hexagonal pen holder with 3mm walls": copied parts of an example (a torus rim), ignored "hexagonal",
   and repeated the same mistake on all 3 corrections. Qwen3 4B: a 4-sided "hexagon" with a wrong height. **Not usable.**
2. **Template first** (the model picks a template and fills in the numbers; free-form only as fallback). Big improvement.
3. **+ prompt rules** (copy numbers exactly; "wide/across" = width; ring example).
4. **+ size checks in code** (sizes the request never stated are reset; an unused stated size triggers one follow-up).

## Qwen3 1.7B (default model), round 4

| request | result | matches the request? |
|---|---|---|
| a 10cm hexagonal pen holder with 3mm walls | Pen holder template: 6 sides, 100 mm tall, 3 mm walls, 80 mm wide | **yes** (acceptance test) |
| a round planter 15 cm wide with a drainage hole | Planter, 150 mm top | yes |
| a simple coaster with a raised edge | Coaster | yes |
| a box 60 x 40 x 30 mm with 2 mm walls and no lid | Open bin 60×40×30, 2 mm walls | yes |
| a wall hook for keys | Wall hook | yes |
| a ring 20 mm in diameter and 3 mm thick | Spacer/ring Ø20 × 3 mm | yes |
| a square vase 20 cm tall with a twist | 4-sided twisted vase, 200 mm tall | partly (also made it 200 mm wide) |
| a cylindrical spacer 10 mm tall, 20 mm across, with an 8 mm hole | Spacer 20/8/10 | yes |
| a small bowl | Bowl | yes |
| a phone stand for a thick phone | Phone stand, defaults | partly (did not widen the slot) |
| a knob for a 6 mm shaft, 40 mm wide | Knob, Ø30 | **no** (ignored 40 mm even after the follow-up) |
| a ball 30 mm in diameter | Ball Ø30 | yes |
| a cube with a hole through it | Custom recipe | partly (hole does not go through) |
| a pencil cup shaped like a star | Custom recipe | **no** (hexagonal, not a star) |
| edit: pen holder "make it taller, 15 cm" | height 100 → 150 | yes (also nudged wall 3 → 3.2) |
| edit: planter "thicker walls please" | wall 2.4 → 4.8 | yes |
| edit: open box "make it twice as long" | length 60 → 120 | yes |

**17/17 watertight and printable. 9/14 new designs fully match, 4 partly, 2 wrong; 3/3 edits correct.**
Template requests took 2–13 s; custom recipes 54–95 s (desktop CPU).

## Qwen3 4B (optional model)

_Pending — see below._

## Speed on phones (estimate, not yet measured)
The model reads its fixed instructions once per start (~1.8k tokens for templates, ~2k for custom recipes) and saves
that work to storage, so later starts skip it. After that a template request is ~60 tokens in and ~50 out.
