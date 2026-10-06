package com.meshgen.core.llm

import com.meshgen.core.dsl.ShapeDsl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * GBNF grammar (llama.cpp) for shape recipes. Constrains the model to well-formed JSON with only real
 * node types and their fields, in a fixed order. Value checks (ranges, expressions) are left to [ShapeDsl],
 * whose error messages are fed back to the model.
 */
object DslGrammar {
    private enum class Kind { VALUE, VEC3, NODE, NODES, BOOL, SCALE }
    private class F(val name: String, val kind: Kind, val optional: Boolean = false)

    private val V = Kind.VALUE

    private val NODES: Map<String, List<F>> = linkedMapOf(
        "box" to listOf(F("size", Kind.VEC3), F("round", V, true)),
        "sphere" to listOf(F("radius", V)),
        "cylinder" to listOf(F("radius", V), F("height", V), F("round", V, true)),
        "cone" to listOf(F("radius_bottom", V), F("radius_top", V), F("height", V)),
        "torus" to listOf(F("major_radius", V), F("minor_radius", V)),
        "capsule" to listOf(F("radius", V), F("height", V)),
        "extrude" to emptyList(), // special-cased below
        "union" to listOf(F("children", Kind.NODES)),
        "subtract" to listOf(F("children", Kind.NODES)),
        "intersect" to listOf(F("children", Kind.NODES)),
        "smooth_union" to listOf(F("radius", V), F("children", Kind.NODES)),
        "translate" to listOf(F("offset", Kind.VEC3), F("child", Kind.NODE)),
        "rotate" to listOf(F("angles", Kind.VEC3), F("child", Kind.NODE)),
        "scale" to listOf(F("factor", Kind.SCALE), F("child", Kind.NODE)),
        "linear_array" to listOf(F("count", V), F("spacing", Kind.VEC3), F("child", Kind.NODE)),
        "radial_array" to listOf(F("count", V), F("radius", V, true), F("child", Kind.NODE)),
        "shell" to listOf(F("thickness", V), F("open_top", Kind.BOOL, true), F("child", Kind.NODE)),
        "offset" to listOf(F("distance", V), F("child", Kind.NODE)),
    )

    init {
        check(NODES.keys == ShapeDsl.NODE_TYPES.toSet()) { "grammar and DSL node types differ" }
    }

    private fun lit(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun key(name: String) = lit("\"$name\":") + " ws"

    private fun kindRule(k: Kind) = when (k) {
        Kind.VALUE -> "val"
        Kind.VEC3 -> "vec3"
        Kind.NODE -> "node"
        Kind.NODES -> "nodes"
        Kind.BOOL -> "bool"
        Kind.SCALE -> "( val | vec3 )"
    }

    val GBNF: String by lazy {
        val sb = StringBuilder()
        sb.appendLine("root ::= \"{\" ws ${key("version")} \"1,\" ws ${key("name")} str \",\" ws ${key("units")} ${lit("\"mm\",")} ws ${key("params")} params \",\" ws ${key("shape")} node ws \"}\"")
        sb.appendLine("params ::= \"{\" ws ( param ( \",\" ws param ){0,15} )? ws \"}\"")
        sb.appendLine(
            "param ::= pname \":\" ws \"{\" ws ${key("value")} num \",\" ws ${key("min")} num \",\" ws ${key("max")} num" +
                " ( \",\" ws ${key("step")} num )? ( \",\" ws ${key("unit")} str )? ( \",\" ws ${key("label")} str )?" +
                " ( \",\" ws ${key("integer")} bool )? ws \"}\"",
        )
        sb.appendLine("pname ::= \"\\\"\" [a-z_] [a-z0-9_]{0,24} \"\\\"\"")
        sb.appendLine("node ::= " + NODES.keys.joinToString(" | ") { "n-${it.replace('_', '-')}" })
        for ((type, fields) in NODES) {
            val head = "\"{\" ws ${key("type")} ${lit("\"$type\"")} cmt"
            val body = if (type == "extrude") {
                " \",\" ws ${key("height")} val \",\" ws ( ${key("sides")} val \",\" ws ${key("radius")} val | ${key("points")} points )" +
                    " ( \",\" ws ${key("twist")} val )? ( \",\" ws ${key("taper")} val )?"
            } else {
                fields.joinToString("") { f ->
                    val part = "\",\" ws ${key(f.name)} ${kindRule(f.kind)}"
                    if (f.optional) " ( $part )?" else " $part"
                }
            }
            sb.appendLine("n-${type.replace('_', '-')} ::= $head$body ws \"}\"")
        }
        sb.appendLine("cmt ::= ( \",\" ws ${key("comment")} str )?")
        sb.appendLine("nodes ::= \"[\" ws node ( \",\" ws node ){0,15} ws \"]\"")
        sb.appendLine("vec3 ::= \"[\" ws val \",\" ws val \",\" ws val ws \"]\"")
        sb.appendLine("points ::= \"[\" ws pt ( \",\" ws pt ){2,47} ws \"]\"")
        sb.appendLine("pt ::= \"[\" ws val \",\" ws val ws \"]\"")
        sb.appendLine("val ::= num | expr")
        sb.appendLine("num ::= \"-\"? ( \"0\" | [1-9] [0-9]{0,4} ) ( \".\" [0-9]{1,4} )?")
        sb.appendLine("expr ::= \"\\\"\" [a-zA-Z0-9_(.-] ( [a-zA-Z0-9_+*/^(). ,-]{0,94} [a-zA-Z0-9_).] )? \"\\\"\"")
        sb.appendLine("bool ::= \"true\" | \"false\"")
        sb.appendLine("str ::= \"\\\"\" [^\"\\\\\\n]{0,48} \"\\\"\"")
        sb.appendLine("ws ::= [ \\n]{0,4}")
        sb.toString()
    }

    private fun order(type: String, obj: JsonObject): List<String> = if (type == "extrude") {
        listOf("type", "comment", "height", "sides", "radius", "points", "twist", "taper")
    } else {
        listOf("type", "comment") + (NODES[type] ?: emptyList()).map { it.name }
    }

    /** Rewrites a node tree with keys in grammar order, so examples look exactly like what the model may write. */
    fun normalize(node: JsonElement): JsonElement {
        if (node !is JsonObject) return node
        val type = (node["type"] as? JsonPrimitive)?.content ?: return node
        val keys = order(type, node)
        val out = LinkedHashMap<String, JsonElement>()
        for (k in keys) node[k]?.let { v ->
            out[k] = when (v) {
                is JsonObject -> normalize(v)
                is JsonArray -> if (k == "children") JsonArray(v.map { normalize(it) }) else v
                else -> v
            }
        }
        for ((k, v) in node) if (k !in out) out[k] = v // unknown keys kept (the conformance check reports them)
        return JsonObject(out)
    }

    private val EXPR = Regex("[a-zA-Z0-9_(.-]([a-zA-Z0-9_+*/^(). ,-]{0,94}[a-zA-Z0-9_).])?")
    private val NUM = Regex("-?(0|[1-9][0-9]{0,4})(\\.[0-9]{1,4})?")

    /** Differences between a recipe tree and what the grammar can produce (for tests). */
    fun conformance(node: JsonElement, path: String = "shape"): List<String> {
        val out = mutableListOf<String>()
        fun value(v: JsonElement?, p: String) {
            val prim = v as? JsonPrimitive
            when {
                prim == null -> out += "$p: not a value"
                prim.isString -> if (!EXPR.matches(prim.content)) out += "$p: expression \"${prim.content}\" outside the grammar"
                !NUM.matches(prim.content) -> out += "$p: number ${prim.content} outside the grammar"
            }
        }
        fun vec3(v: JsonElement?, p: String) {
            if (v !is JsonArray || v.size != 3) out += "$p: not [x,y,z]" else v.forEachIndexed { i, e -> value(e, "$p[$i]") }
        }
        if (node !is JsonObject) return listOf("$path: not a node")
        val type = (node["type"] as? JsonPrimitive)?.content ?: return listOf("$path: no type")
        val expected = order(type, node).filter { it in node }
        val actual = node.keys.toList()
        if (expected != actual) out += "$path: keys $actual, grammar order $expected"
        (node["comment"] as? JsonPrimitive)?.let { if (it.content.length > 48) out += "$path.comment longer than 48" }
        val fields = if (type == "extrude") listOf(F("height", V), F("sides", V, true), F("radius", V, true), F("twist", V, true), F("taper", V, true)) else NODES[type] ?: emptyList()
        for (f in fields) {
            val v = node[f.name] ?: continue
            val p = "$path.${f.name}"
            when (f.kind) {
                Kind.VALUE -> value(v, p)
                Kind.VEC3 -> vec3(v, p)
                Kind.SCALE -> if (v is JsonArray) vec3(v, p) else value(v, p)
                Kind.BOOL -> if ((v as? JsonPrimitive)?.booleanOrNull == null) out += "$p: not a bool"
                Kind.NODE -> out += conformance(v, p)
                Kind.NODES -> (v as? JsonArray)?.let { a ->
                    if (a.size > 16) out += "$p: more than 16 children"
                    a.forEachIndexed { i, c -> out += conformance(c, "$p[$i]") }
                }
            }
        }
        (node["points"] as? JsonArray)?.forEachIndexed { i, pt -> (pt as? JsonArray)?.forEachIndexed { j, e -> value(e, "$path.points[$i][$j]") } }
        return out
    }
}
