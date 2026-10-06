package com.meshgen.core.llm

import com.meshgen.core.dsl.ShapeDoc
import com.meshgen.core.dsl.Templates

/** Grammars for stage-1 replies: only real template ids and their own parameter names are possible. */
object PlanGrammar {
    private fun lit(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun rule(id: String) = id.replace('_', '-')

    private const val COMMON = """
num ::= "-"? ( "0" | [1-9] [0-9]{0,4} ) ( "." [0-9]{1,3} )?
str ::= "\"" [^"\\\n]{1,40} "\""
ws ::= [ \n]{0,2}
"""

    private fun paramsRule(name: String, keys: List<String>): String {
        val key = keys.joinToString(" | ") { lit("\"$it\"") }
        return "$name ::= \"{\" ws ( $name-kv ( \",\" ws $name-kv ){0,${keys.size - 1}} )? ws \"}\"\n$name-kv ::= ( $key ) \":\" ws num\n"
    }

    /** New design: a template with values, or "custom". */
    val CREATE: String by lazy {
        val sb = StringBuilder()
        sb.append("root ::= \"{\" ws ${lit("\"template\":")} ws ( custom | ")
        sb.append(Templates.IDS.joinToString(" | ") { "t-${rule(it)}" })
        sb.append(" )\n")
        sb.append("custom ::= ${lit("\"${PlanPrompt.CUSTOM}\"}")}\n")
        for (id in Templates.IDS) {
            val keys = Templates.load(id).params.map { it.name }
            sb.append("t-${rule(id)} ::= ${lit("\"$id\",")} ws ${lit("\"name\":")} ws str \",\" ws ${lit("\"params\":")} ws p-${rule(id)} ws \"}\"\n")
            sb.append(paramsRule("p-${rule(id)}", keys))
        }
        sb.append(COMMON)
        sb.toString()
    }

    /** Change to an existing design: new values for its own parameters, or "custom" (needs a rewrite). */
    fun edit(doc: ShapeDoc): String {
        val keys = doc.params.map { it.name }
        val sb = StringBuilder()
        sb.append("root ::= \"{\" ws ${lit("\"template\":")} ws ( custom | current )\n")
        sb.append("custom ::= ${lit("\"${PlanPrompt.CUSTOM}\"}")}\n")
        if (keys.isEmpty()) {
            sb.append("current ::= ${lit("\"${PlanPrompt.CURRENT}\",")} ws ${lit("\"params\":{}}")}\n")
        } else {
            sb.append("current ::= ${lit("\"${PlanPrompt.CURRENT}\",")} ws ${lit("\"params\":")} ws cur ws \"}\"\n")
            sb.append(paramsRule("cur", keys))
        }
        sb.append(COMMON)
        return sb.toString()
    }
}
