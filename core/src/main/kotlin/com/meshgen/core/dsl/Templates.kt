package com.meshgen.core.dsl

/** Hand-written recipes bundled with the app: the template gallery and the language model's few-shot examples. */
object Templates {
    val IDS = listOf(
        "planter", "hex_pen_holder", "box_with_lid", "wall_hook", "phone_stand", "twisted_vase",
        "gear_ring", "cable_holder", "coaster", "bowl", "jewelry_tray", "storage_bin",
        "l_bracket", "knob", "soap_dish", "keychain_tag", "drawer_pull", "tealight_holder",
        "spacer", "mounting_plate",
    )

    fun source(id: String): String =
        Templates::class.java.getResourceAsStream("/templates/$id.json")?.bufferedReader()?.use { it.readText() }
            ?: error("template '$id' is missing")

    fun load(id: String): ShapeDoc {
        val parsed = ShapeDsl.parse(source(id))
        return parsed.doc ?: error("template '$id' is invalid: ${parsed.issues.joinToString("; ")}")
    }
}
