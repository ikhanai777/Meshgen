package com.meshgen.core.dsl

import com.meshgen.core.sdf.BoxSdf
import com.meshgen.core.sdf.CapsuleSdf
import com.meshgen.core.sdf.ConeSdf
import com.meshgen.core.sdf.CylinderSdf
import com.meshgen.core.sdf.ExtrudeSdf
import com.meshgen.core.sdf.IntersectSdf
import com.meshgen.core.sdf.OffsetSdf
import com.meshgen.core.sdf.RotateSdf
import com.meshgen.core.sdf.ScaleSdf
import com.meshgen.core.sdf.Sdf
import com.meshgen.core.sdf.ShellSdf
import com.meshgen.core.sdf.SmoothUnionSdf
import com.meshgen.core.sdf.SphereSdf
import com.meshgen.core.sdf.SubtractSdf
import com.meshgen.core.sdf.TorusSdf
import com.meshgen.core.sdf.TranslateSdf
import com.meshgen.core.sdf.UnionSdf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The shape recipe language (JSON). See docs/SHAPE_DSL.md for the full reference.
 * Parsing and compiling collect every problem they find, each with a path, so errors can be shown
 * to a person or fed back to the language model in one go.
 */
object ShapeDsl {
    const val MAX_NODES = 3000
    const val MAX_DEPTH = 40
    const val MAX_SIZE_MM = 1000.0
    const val MAX_ARRAY = 200

    class Parsed(val doc: ShapeDoc?, val issues: List<DslIssue>)
    class Compiled(val sdf: Sdf?, val issues: List<DslIssue>)

    private val NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val TOP_KEYS = setOf("version", "name", "description", "prompt", "category", "units", "params", "shape")
    private val PARAM_KEYS = setOf("value", "min", "max", "step", "unit", "label", "integer")
    private val RESERVED = setOf("pi", "min", "max", "abs", "sqrt", "sin", "cos", "tan", "floor", "ceil", "round", "clamp")

    val NODE_TYPES = listOf(
        "box", "sphere", "cylinder", "cone", "torus", "capsule", "extrude",
        "union", "subtract", "intersect", "smooth_union",
        "translate", "rotate", "scale", "linear_array", "radial_array", "shell", "offset",
    )

    fun parse(text: String): Parsed {
        val root = try {
            Json.parseToJsonElement(text.trim().removeSurrounding("```json", "```").removeSurrounding("```").trim())
        } catch (e: Exception) {
            return Parsed(null, listOf(DslIssue("$", "not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}")))
        }
        return parse(root)
    }

    fun parse(root: JsonElement): Parsed {
        val issues = mutableListOf<DslIssue>()
        if (root !is JsonObject) return Parsed(null, listOf(DslIssue("$", "the recipe must be a JSON object")))
        for (k in root.keys - TOP_KEYS) issues += DslIssue(k, "unknown top-level field. Allowed: ${TOP_KEYS.joinToString()}")

        root["version"]?.let { v -> if ((v as? JsonPrimitive)?.doubleOrNull != 1.0) issues += DslIssue("version", "must be 1") }
        root["units"]?.let { u -> if ((u as? JsonPrimitive)?.content != "mm") issues += DslIssue("units", "only \"mm\" is supported") }
        val name = str(root, "name", issues, required = true) ?: ""
        val description = str(root, "description", issues) ?: ""
        val prompt = str(root, "prompt", issues)
        val category = str(root, "category", issues)

        val params = mutableListOf<ParamDef>()
        when (val p = root["params"]) {
            null, JsonNull -> Unit
            is JsonObject -> for ((key, def) in p) parseParam(key, def, issues)?.let { params += it }
            else -> issues += DslIssue("params", "must be an object of named parameters")
        }

        val shape = root["shape"]
        if (shape == null) issues += DslIssue("shape", "missing: every recipe needs a \"shape\" node")
        else if (shape !is JsonObject) issues += DslIssue("shape", "must be a node object with a \"type\"")

        if (issues.isNotEmpty() || shape !is JsonObject) return Parsed(null, issues)
        val doc = ShapeDoc(name, description, prompt, category, params, shape)
        // Compile once with default values so structural errors surface at parse time.
        val compiled = compile(doc)
        return Parsed(if (compiled.issues.isEmpty()) doc else null, compiled.issues)
    }

    private fun str(o: JsonObject, key: String, issues: MutableList<DslIssue>, required: Boolean = false): String? {
        val v = o[key]
        if (v == null) { if (required) issues += DslIssue(key, "missing (a short name for the object)"); return null }
        if (v !is JsonPrimitive || !v.isString) { issues += DslIssue(key, "must be a string"); return null }
        return v.content
    }

    private fun parseParam(key: String, def: JsonElement, issues: MutableList<DslIssue>): ParamDef? {
        val path = "params.$key"
        if (!NAME.matches(key) || key in RESERVED) {
            issues += DslIssue(path, "parameter names must be letters, digits or _ (not starting with a digit) and not a function name")
            return null
        }
        // Shorthand: "height": 100
        if (def is JsonPrimitive && def.doubleOrNull != null) {
            val v = def.doubleOrNull!!
            val span = maxOf(kotlin.math.abs(v), 1.0)
            return ParamDef(key, v, minOf(v - span * 0.75, if (v > 0) v * 0.25 else v - span), v + span * 3, null, "", key, false)
        }
        if (def !is JsonObject) { issues += DslIssue(path, "must be a number or an object {value, min, max}"); return null }
        for (k in def.keys - PARAM_KEYS) issues += DslIssue("$path.$k", "unknown field. Allowed: ${PARAM_KEYS.joinToString()}")
        fun n(k: String): Double? = (def[k] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        val value = n("value") ?: run { issues += DslIssue("$path.value", "missing or not a number"); return null }
        val min = n("min") ?: run { issues += DslIssue("$path.min", "missing or not a number"); return null }
        val max = n("max") ?: run { issues += DslIssue("$path.max", "missing or not a number"); return null }
        if (min > max) { issues += DslIssue(path, "min ($min) is greater than max ($max)"); return null }
        if (value < min || value > max) { issues += DslIssue("$path.value", "$value is outside min..max ($min..$max)"); return null }
        val step = n("step")
        if (step != null && step <= 0) issues += DslIssue("$path.step", "must be > 0")
        val integer = (def["integer"] as? JsonPrimitive)?.booleanOrNull ?: false
        val unit = (def["unit"] as? JsonPrimitive)?.content ?: ""
        val label = (def["label"] as? JsonPrimitive)?.content ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
        return ParamDef(key, value, min, max, step, unit, label, integer)
    }

    /** Builds the SDF with the doc's current parameter values. */
    fun compile(doc: ShapeDoc): Compiled {
        val ctx = Ctx(doc.values)
        val sdf = ctx.node(doc.shape, "shape", 0)
        if (sdf != null && ctx.issues.isEmpty()) {
            val b = sdf.bounds
            if (b.isEmpty || b.sizeX <= 0 || b.sizeY <= 0 || b.sizeZ <= 0) ctx.issues += DslIssue("shape", "the result is empty (nothing left after cuts or intersections)")
            else if (b.sizeX > MAX_SIZE_MM || b.sizeY > MAX_SIZE_MM || b.sizeZ > MAX_SIZE_MM) {
                ctx.issues += DslIssue("shape", String.format(java.util.Locale.US, "the result is larger than %.0f mm on one side (%.0f × %.0f × %.0f mm)", MAX_SIZE_MM, b.sizeX, b.sizeY, b.sizeZ))
            }
        }
        return Compiled(if (ctx.issues.isEmpty()) sdf else null, ctx.issues.toList())
    }

    private class Ctx(val vars: Map<String, Double>) {
        val issues = mutableListOf<DslIssue>()
        var nodes = 0

        fun node(el: JsonElement?, path: String, depth: Int, multiplier: Int = 1): Sdf? {
            if (el !is JsonObject) { issues += DslIssue(path, "must be a node object like {\"type\": \"box\", ...}"); return null }
            if (depth > MAX_DEPTH) { issues += DslIssue(path, "nested too deeply (max $MAX_DEPTH levels)"); return null }
            nodes += multiplier
            if (nodes > MAX_NODES) {
                if (nodes - multiplier <= MAX_NODES) issues += DslIssue(path, "too many parts (max $MAX_NODES after arrays are expanded)")
                return null
            }
            val type = (el["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (type == null) { issues += DslIssue("$path.type", "missing. Use one of: ${NODE_TYPES.joinToString()}"); return null }
            val f = Fields(el, path, this, depth, multiplier)
            val sdf = when (type) {
                "box" -> {
                    val s = f.vec3("size", positive = true)
                    val r = f.num("round", default = 0.0, min = 0.0)
                    if (s != null && r != null && r * 2 >= minOf(s[0], s[1], s[2])) f.error("round", "must be less than half the smallest side")
                    f.ok { BoxSdf(s!![0], s[1], s[2], r!!) }
                }
                "sphere" -> { val r = f.num("radius", min = 0.0, exclusive = true); f.ok { SphereSdf(r!!) } }
                "cylinder" -> {
                    val r = f.num("radius", min = 0.0, exclusive = true)
                    val h = f.num("height", min = 0.0, exclusive = true)
                    val rd = f.num("round", default = 0.0, min = 0.0)
                    if (r != null && h != null && rd != null && (rd >= r || rd * 2 >= h)) f.error("round", "must be less than the radius and half the height")
                    f.ok { CylinderSdf(r!!, h!!, rd!!) }
                }
                "cone" -> {
                    val r1 = f.num("radius_bottom", min = 0.0)
                    val r2 = f.num("radius_top", min = 0.0)
                    val h = f.num("height", min = 0.0, exclusive = true)
                    if (r1 == 0.0 && r2 == 0.0) f.error("radius_bottom", "radius_bottom and radius_top cannot both be 0")
                    f.ok { ConeSdf(r1!!, r2!!, h!!) }
                }
                "torus" -> {
                    val R = f.num("major_radius", min = 0.0, exclusive = true)
                    val r = f.num("minor_radius", min = 0.0, exclusive = true)
                    if (R != null && r != null && r >= R) f.error("minor_radius", "must be smaller than major_radius")
                    f.ok { TorusSdf(R!!, r!!) }
                }
                "capsule" -> {
                    val r = f.num("radius", min = 0.0, exclusive = true)
                    val h = f.num("height", min = 0.0, exclusive = true)
                    if (r != null && h != null && h < 2 * r) f.error("height", "must be at least 2 × radius (it is the total height)")
                    f.ok { CapsuleSdf(r!!, h!!) }
                }
                "extrude" -> extrude(f)
                "union", "intersect" -> {
                    val kids = f.nodes("children", 1)
                    f.ok { if (kids!!.size == 1) kids[0] else if (type == "union") UnionSdf(kids) else IntersectSdf(kids) }
                }
                "subtract" -> { val kids = f.nodes("children", 2); f.ok { SubtractSdf(kids!![0], kids.drop(1)) } }
                "smooth_union" -> {
                    val k = f.num("radius", min = 0.0, exclusive = true)
                    val kids = f.nodes("children", 2)
                    f.ok { SmoothUnionSdf(kids!!, k!!) }
                }
                "translate" -> { val o = f.vec3("offset"); val c = f.child(); f.ok { TranslateSdf(c!!, o!![0], o[1], o[2]) } }
                "rotate" -> { val a = f.vec3("angles"); val c = f.child(); f.ok { RotateSdf(c!!, a!![0], a[1], a[2]) } }
                "scale" -> {
                    val s = f.scale("factor")
                    val c = f.child()
                    f.ok { ScaleSdf(c!!, s!![0], s[1], s[2]) }
                }
                "linear_array" -> {
                    val n = f.int("count", min = 1, max = MAX_ARRAY)
                    val sp = f.vec3("spacing")
                    val c = f.child(n ?: 1)
                    f.ok { UnionSdf((0 until n!!).map { i -> TranslateSdf(c!!, sp!![0] * i, sp[1] * i, sp[2] * i) }) }
                }
                "radial_array" -> {
                    val n = f.int("count", min = 1, max = MAX_ARRAY)
                    val r = f.num("radius", default = 0.0, min = 0.0)
                    val a0 = f.num("start_angle", default = 0.0)
                    val c = f.child(n ?: 1)
                    f.ok {
                        val moved = if (r!! > 0) TranslateSdf(c!!, r, 0.0, 0.0) else c!!
                        UnionSdf((0 until n!!).map { i -> RotateSdf(moved, 0.0, 0.0, a0!! + 360.0 * i / n) })
                    }
                }
                "shell" -> {
                    val t = f.num("thickness", min = 0.0, exclusive = true)
                    val open = f.bool("open_top", false)
                    val c = f.child()
                    f.ok { ShellSdf(c!!, t!!, open) }
                }
                "offset" -> { val d = f.num("distance"); val c = f.child(); f.ok { OffsetSdf(c!!, d!!) } }
                else -> { issues += DslIssue("$path.type", "unknown type \"$type\". Use one of: ${NODE_TYPES.joinToString()}"); return null }
            }
            f.finish()
            return sdf
        }

        private fun extrude(f: Fields): Sdf? {
            val h = f.num("height", min = 0.0, exclusive = true)
            val twist = f.num("twist", default = 0.0)
            val taper = f.num("taper", default = 1.0, min = 0.0, exclusive = true)
            val hasPoints = "points" in f.obj
            val hasSides = "sides" in f.obj
            var xs: DoubleArray? = null
            var ys: DoubleArray? = null
            when {
                hasPoints && hasSides -> f.error("points", "use either \"points\" or \"sides\"+\"radius\", not both")
                hasPoints -> f.points("points")?.let { (a, b) -> xs = a; ys = b }
                hasSides -> {
                    val n = f.int("sides", min = 3, max = 64)
                    val r = f.num("radius", min = 0.0, exclusive = true)
                    if (n != null && r != null) ExtrudeSdf.regular(n, r).let { (a, b) -> xs = a; ys = b }
                }
                else -> f.error("points", "missing: give \"points\" [[x,y],...] or \"sides\" and \"radius\"")
            }
            return f.ok { ExtrudeSdf(xs!!, ys!!, h!!, twist!!, taper!!) }
        }
    }

    private class Fields(val obj: JsonObject, val path: String, val ctx: Ctx, val depth: Int, val multiplier: Int) {
        private val used = mutableSetOf("type", "comment", "name")
        private var failed = false
        private val type = (obj["type"] as JsonPrimitive).content

        fun error(key: String, msg: String) { ctx.issues += DslIssue("$path.$key", msg); failed = true }

        fun <T> ok(build: () -> T): T? = if (failed) null else build()

        fun finish() {
            for (k in obj.keys - used) ctx.issues += DslIssue("$path.$k", "unknown field for \"$type\". Allowed: ${(used - "comment" - "name" - "type").joinToString()}")
        }

        private fun value(el: JsonElement, p: String): Double? {
            val prim = el as? JsonPrimitive
            if (prim == null || prim is JsonNull) { ctx.issues += DslIssue(p, "must be a number or an expression string"); failed = true; return null }
            if (prim.isString) {
                return try { Expr.eval(prim.content, ctx.vars) } catch (e: ExprException) {
                    ctx.issues += DslIssue(p, e.message ?: "bad expression"); failed = true; null
                }
            }
            return prim.doubleOrNull ?: run { ctx.issues += DslIssue(p, "must be a number"); failed = true; null }
        }

        private fun check(v: Double, p: String, min: Double?, exclusive: Boolean, max: Double?): Double? {
            if (min != null && (if (exclusive) v <= min else v < min)) {
                ctx.issues += DslIssue(p, "must be ${if (exclusive) ">" else "≥"} ${fmt(min)} (got ${fmt(v)})"); failed = true; return null
            }
            if (max != null && v > max) { ctx.issues += DslIssue(p, "must be ≤ ${fmt(max)} (got ${fmt(v)})"); failed = true; return null }
            if (kotlin.math.abs(v) > 10 * MAX_SIZE_MM && min != null) { ctx.issues += DslIssue(p, "${fmt(v)} is unreasonably large"); failed = true; return null }
            return v
        }

        fun num(key: String, default: Double? = null, min: Double? = null, exclusive: Boolean = false, max: Double? = null): Double? {
            used += key
            val el = obj[key] ?: return default ?: run { error(key, "missing"); null }
            val v = value(el, "$path.$key") ?: return null
            return check(v, "$path.$key", min, exclusive, max)
        }

        fun int(key: String, min: Int, max: Int): Int? {
            val v = num(key, min = min.toDouble(), max = max.toDouble()) ?: return null
            return Math.round(v).toInt()
        }

        fun bool(key: String, default: Boolean): Boolean {
            used += key
            val el = obj[key] ?: return default
            return (el as? JsonPrimitive)?.booleanOrNull ?: run { error(key, "must be true or false"); default }
        }

        fun vec3(key: String, positive: Boolean = false): DoubleArray? {
            used += key
            val el = obj[key] ?: run { error(key, "missing: expected [x, y, z]"); return null }
            if (el !is JsonArray || el.size != 3) { error(key, "must be a list of 3 values [x, y, z]"); return null }
            val out = DoubleArray(3)
            for (i in 0..2) {
                val v = value(el[i], "$path.$key[$i]") ?: return null
                out[i] = check(v, "$path.$key[$i]", if (positive) 0.0 else null, true, null) ?: return null
            }
            return out
        }

        fun scale(key: String): DoubleArray? {
            used += key
            val el = obj[key] ?: run { error(key, "missing: a number or [x, y, z]"); return null }
            if (el is JsonArray) return vec3(key, positive = true)
            val v = value(el, "$path.$key") ?: return null
            val s = check(v, "$path.$key", 0.0, true, null) ?: return null
            return doubleArrayOf(s, s, s)
        }

        fun points(key: String): Pair<DoubleArray, DoubleArray>? {
            used += key
            val el = obj[key]
            if (el !is JsonArray || el.size < 3) { error(key, "must be a list of at least 3 [x, y] points"); return null }
            if (el.size > 500) { error(key, "too many points (max 500)"); return null }
            val xs = DoubleArray(el.size); val ys = DoubleArray(el.size)
            for ((i, p) in el.withIndex()) {
                if (p !is JsonArray || p.size != 2) { error("$key[$i]", "must be [x, y]"); return null }
                xs[i] = value(p[0], "$path.$key[$i][0]") ?: return null
                ys[i] = value(p[1], "$path.$key[$i][1]") ?: return null
            }
            var area = 0.0
            for (i in xs.indices) { val j = (i + 1) % xs.size; area += xs[i] * ys[j] - xs[j] * ys[i] }
            if (kotlin.math.abs(area) < 1e-6) { error(key, "the points enclose no area"); return null }
            selfIntersection(xs, ys)?.let { (a, b) -> error(key, "the outline crosses itself (edges $a and $b)"); return null }
            return xs to ys
        }

        fun child(mult: Int = 1): Sdf? {
            used += "child"
            if ("child" !in obj) {
                error("child", if ("children" in obj) "\"$type\" takes a single \"child\" node (wrap several in a union)" else "missing")
                return null
            }
            return ctx.node(obj["child"], "$path.child", depth + 1, multiplier * mult) ?: run { failed = true; null }
        }

        fun nodes(key: String, minCount: Int): List<Sdf>? {
            used += key
            val el = obj[key]
            if (el !is JsonArray || el.size < minCount) {
                error(key, if (el == null && "child" in obj) "\"$type\" takes a \"children\" list" else "must be a list of at least $minCount nodes")
                return null
            }
            val out = el.mapIndexed { i, c -> ctx.node(c, "$path.$key[$i]", depth + 1, multiplier) }
            if (out.any { it == null }) { failed = true; return null }
            return out.map { it!! }
        }
    }

    private fun selfIntersection(xs: DoubleArray, ys: DoubleArray): Pair<Int, Int>? {
        val n = xs.size
        fun cross(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double) = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        for (i in 0 until n) for (j in i + 1 until n) {
            if (j == i + 1 || (i == 0 && j == n - 1)) continue
            val a = i; val b = (i + 1) % n; val c = j; val d = (j + 1) % n
            val d1 = cross(xs[c], ys[c], xs[d], ys[d], xs[a], ys[a])
            val d2 = cross(xs[c], ys[c], xs[d], ys[d], xs[b], ys[b])
            val d3 = cross(xs[a], ys[a], xs[b], ys[b], xs[c], ys[c])
            val d4 = cross(xs[a], ys[a], xs[b], ys[b], xs[d], ys[d])
            if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return i to j
        }
        return null
    }

    private fun fmt(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else String.format(java.util.Locale.US, "%.3g", v)
}
