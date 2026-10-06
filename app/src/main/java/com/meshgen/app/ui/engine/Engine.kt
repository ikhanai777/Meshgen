package com.meshgen.app.ui.engine

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.ui.graphics.vector.ImageVector

enum class Engine(
    val id: String,
    val index: String,
    val title: String,
    val tagline: String,
    val description: String,
    val badge: String,
    val experimental: Boolean,
    val plannedPhase: Int,
    val icon: ImageVector,
) {
    TEXT_TO_SHAPE(
        id = "shape",
        index = "01",
        title = "Text → Shape",
        tagline = "Describe a printable object",
        description = "A small on-device language model turns your words into a precise shape recipe, " +
            "which becomes a watertight, editable mesh built for 3D printing. Every size is a slider you can tweak.",
        badge = "CORE",
        experimental = false,
        plannedPhase = 3,
        icon = Icons.Outlined.TextFields,
    ),
    PHOTO_TO_3D(
        id = "photo",
        index = "02",
        title = "Photo → 3D",
        tagline = "Turn a single image into a mesh",
        description = "Removes the background and reconstructs a 3D mesh from one photo, entirely on your phone. " +
            "Optionally generate the image from text first.",
        badge = "EXPERIMENTAL · FLAGSHIP",
        experimental = true,
        plannedPhase = 4,
        icon = Icons.Outlined.Image,
    ),
    CAPTURE(
        id = "capture",
        index = "03",
        title = "Camera Capture",
        tagline = "Scan a real object",
        description = "Walk around an object with your camera. Depth frames are fused into a single mesh you can clean up and export.",
        badge = "EXPERIMENTAL · FLAGSHIP",
        experimental = true,
        plannedPhase = 5,
        icon = Icons.Outlined.CameraAlt,
    );

    companion object {
        fun fromId(id: String?): Engine? = entries.firstOrNull { it.id == id }
    }
}
