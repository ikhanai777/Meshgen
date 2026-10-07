package com.meshgen.core.llm

import com.meshgen.core.dsl.ParamDef

/**
 * Lengths written in a request ("10cm", "3 mm", "60 x 40 x 30 mm", "2 inches"), converted to millimetres.
 * Used to check the model's parameter values against what the person actually said.
 */
object StatedSizes {
    private val NUMBER = Regex("(?<![\\w.])(\\d+(?:[.,]\\d+)?)")
    private val UNIT = Regex("^\\s*(mm|millimet(?:er|re)s?|cm|centimet(?:er|re)s?|m(?:et(?:er|re)s?)?|in(?:ch(?:es)?)?|\")(?![a-z])", RegexOption.IGNORE_CASE)
    /** Between numbers of one dimension group: "60 x 40 x 30 mm", "6 by 4 cm", "10, 20 and 30 mm". */
    private val JOINER = Regex("^\\s*(x|×|by|,|and|to)\\s*", RegexOption.IGNORE_CASE)

    fun millimetres(request: String): List<Double> {
        val text = request.lowercase()
        val out = mutableListOf<Double>()
        val nums = NUMBER.findAll(text).toList()
        for ((i, m) in nums.withIndex()) {
            val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            // Find the unit after this number, possibly after further numbers of the same group.
            var j = i
            var unit: String? = null
            while (true) {
                val after = text.substring(nums[j].range.last + 1)
                val u = UNIT.find(after)
                if (u != null) { unit = u.groupValues[1]; break }
                val join = JOINER.find(after) ?: break
                val next = nums.getOrNull(j + 1) ?: break
                if (nums[j].range.last + 1 + join.range.last + 1 != next.range.first) break
                j++
            }
            val factor = when {
                unit == null -> continue
                unit!!.startsWith("mm") || unit!!.startsWith("milli") -> 1.0
                unit!!.startsWith("c") -> 10.0
                unit!! == "\"" || unit!!.startsWith("in") -> 25.4
                unit!!.startsWith("m") -> 1000.0
                else -> continue
            }
            out += v * factor
        }
        // "radius 20 mm" also states a 40 mm diameter (templates use diameters).
        if ("radius" in text) return out + out.map { it * 2 }
        return out
    }

    /** True when [value] (mm) is one of the stated sizes (within 2%). */
    fun matches(value: Double, stated: List<Double>): Boolean =
        stated.any { s -> kotlin.math.abs(s - value) <= maxOf(0.02 * s, 0.05) }

    /** Millimetre parameters the model changed to a value nobody stated: these go back to their defaults. */
    fun invented(params: List<ParamDef>, values: Map<String, Double>, stated: List<Double>): List<String> =
        params.filter { it.unit == "mm" }.mapNotNull { p ->
            val v = values[p.name] ?: return@mapNotNull null
            if (kotlin.math.abs(v - p.value) < 1e-9 || matches(v, stated)) null else p.name
        }

    /** Stated sizes that no millimetre parameter ended up using. */
    fun unused(params: List<ParamDef>, values: Map<String, Double>, stated: List<Double>): List<Double> {
        val used = params.filter { it.unit == "mm" }.map { values[it.name] ?: it.value }
        return stated.filter { s -> used.none { u -> matches(u, listOf(s)) || matches(u, listOf(s * 2)) } }.distinct()
    }
}
